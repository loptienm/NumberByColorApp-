package com.example.engine

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DecodedGif(
  val frames: List<Bitmap>,
  val frameMs: Int,
)

/**
 * Pure-Kotlin offline GIF87a/GIF89a frame decoder compatible with Android API 24+ (including
 * Amazon Fire tablets) with zero external library dependencies.
 */
object GifFrameDecoder {
  fun isGifHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 6) return false
    return bytes[0] == 'G'.code.toByte() &&
      bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() &&
      bytes[3] == '8'.code.toByte() &&
      (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
      bytes[5] == 'a'.code.toByte()
  }

  fun decodeGifFrames(
    bytes: ByteArray,
    maxFrames: Int = 16,
    maxDim: Int = 1200,
  ): DecodedGif? {
    if (!isGifHeader(bytes) || bytes.size < 14) return null
    val r = ByteCursor(bytes)
    r.pos = 6

    val screenW = r.readU16()
    val screenH = r.readU16()
    if (screenW <= 0 || screenH <= 0 || screenW > 4096 || screenH > 4096) return null

    val lsdPacked = r.readU8()
    val hasGct = (lsdPacked and 0x80) != 0
    val gctSize = 1 shl ((lsdPacked and 0x07) + 1)
    r.readU8() // bgColorIndex
    r.readU8() // pixelAspect

    val gct = if (hasGct) readColorTable(r, gctSize) else IntArray(0)

    val canvas = IntArray(screenW * screenH)
    var prevCanvas: IntArray? = null

    var disposalMethod = 0
    var hasTransparency = false
    var transparentIndex = 0
    var currentDelayMs = 100

    val rawFrames = mutableListOf<Pair<IntArray, Int>>()
    val maxRawCap = 72 // prevent huge memory spikes on multi-hundred-frame GIFs

    loop@ while (r.hasMore() && rawFrames.size < maxRawCap) {
      when (r.readU8()) {
        0x3B -> break@loop // Trailer
        0x21 -> {
          // Extension block
          if (!r.hasMore()) break@loop
          val label = r.readU8()
          if (label == 0xF9) {
            // Graphic Control Extension
            val blockSize = r.readU8()
            if (blockSize == 4 && r.remaining() >= 5) {
              val packed = r.readU8()
              disposalMethod = (packed shr 2) and 0x07
              hasTransparency = (packed and 0x01) != 0
              val delayCentis = r.readU16()
              currentDelayMs = if (delayCentis <= 1) 100 else delayCentis * 10
              transparentIndex = r.readU8()
              r.readU8() // terminator
            } else {
              r.skip(blockSize)
              skipSubBlocks(r)
            }
          } else {
            skipSubBlocks(r)
          }
        }
        0x2C -> {
          // Image Descriptor
          if (r.remaining() < 9) break@loop
          val imgLeft = r.readU16()
          val imgTop = r.readU16()
          val imgW = r.readU16()
          val imgH = r.readU16()
          val imgPacked = r.readU8()

          val hasLct = (imgPacked and 0x80) != 0
          val interlaced = (imgPacked and 0x40) != 0
          val lctSize = 1 shl ((imgPacked and 0x07) + 1)
          val colorTable = if (hasLct) readColorTable(r, lctSize) else gct
          if (colorTable.isEmpty()) break@loop

          if (!r.hasMore()) break@loop
          val minCodeSize = r.readU8().coerceIn(2, 11)
          val lzwBytes = readSubBlocksBytes(r)

          if (disposalMethod == 3) {
            prevCanvas = canvas.copyOf()
          }

          val expectedPixels = imgW * imgH
          if (imgW > 0 && imgH > 0 && expectedPixels <= 16_000_000) {
            val indices = decodeLzw(lzwBytes, minCodeSize, expectedPixels)
            writeFramePixels(
              canvas = canvas,
              screenW = screenW,
              screenH = screenH,
              indices = indices,
              imgLeft = imgLeft,
              imgTop = imgTop,
              imgW = imgW,
              imgH = imgH,
              interlaced = interlaced,
              colorTable = colorTable,
              hasTransparency = hasTransparency,
              transparentIndex = transparentIndex,
            )
            rawFrames.add(canvas.copyOf() to currentDelayMs)
          }

          // Apply disposal after rendering frame
          when (disposalMethod) {
            2 -> {
              // Restore area to transparent
              val endY = min(screenH, imgTop + imgH)
              val endX = min(screenW, imgLeft + imgW)
              for (y in max(0, imgTop) until endY) {
                val off = y * screenW
                for (x in max(0, imgLeft) until endX) {
                  canvas[off + x] = 0
                }
              }
            }
            3 -> {
              if (prevCanvas != null) {
                prevCanvas.copyInto(canvas)
              }
            }
          }

          // Reset GCE defaults for next frame
          disposalMethod = 0
          hasTransparency = false
          transparentIndex = 0
          currentDelayMs = 100
        }
        else -> {
          // Unknown byte; stop safely
          break@loop
        }
      }
    }

    if (rawFrames.size <= 1) return null

    val step = max(1, (rawFrames.size + maxFrames - 1) / maxFrames)
    val kept = rawFrames.filterIndexed { idx, _ -> idx % step == 0 }.take(maxFrames)
    val totalMs = rawFrames.sumOf { it.second }.coerceAtLeast(kept.size * 80)
    val avgFrameMs = (totalMs / kept.size).coerceIn(80, 1200)

    val scale = min(1f, min(maxDim.toFloat() / screenW, maxDim.toFloat() / screenH))
    val outW = max(1, (screenW * scale).roundToInt())
    val outH = max(1, (screenH * scale).roundToInt())

    val bitmaps =
      kept.map { (pixels, _) ->
        val full = Bitmap.createBitmap(pixels, screenW, screenH, Bitmap.Config.ARGB_8888)
        if (outW == screenW && outH == screenH) {
          full
        } else {
          Bitmap.createScaledBitmap(full, outW, outH, false).also {
            if (it !== full) full.recycle()
          }
        }
      }

    return DecodedGif(frames = bitmaps, frameMs = avgFrameMs)
  }

  private fun readColorTable(r: ByteCursor, count: Int): IntArray {
    val out = IntArray(count)
    for (i in 0 until count) {
      if (r.remaining() < 3) break
      val red = r.readU8()
      val green = r.readU8()
      val blue = r.readU8()
      out[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
    return out
  }

  private fun skipSubBlocks(r: ByteCursor) {
    while (r.hasMore()) {
      val len = r.readU8()
      if (len == 0) break
      r.skip(len)
    }
  }

  private fun readSubBlocksBytes(r: ByteCursor): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var total = 0
    while (r.hasMore()) {
      val len = r.readU8()
      if (len == 0) break
      val actual = min(len, r.remaining())
      val chunk = r.readBytes(actual)
      chunks.add(chunk)
      total += actual
    }
    val out = ByteArray(total)
    var pos = 0
    for (c in chunks) {
      c.copyInto(out, pos)
      pos += c.size
    }
    return out
  }

  private fun decodeLzw(data: ByteArray, minCodeSize: Int, expectedPixels: Int): IntArray {
    val out = IntArray(expectedPixels)
    val clearCode = 1 shl minCodeSize
    val endCode = clearCode + 1
    val maxStack = 4096

    val prefix = IntArray(maxStack)
    val suffix = IntArray(maxStack)
    val stack = IntArray(maxStack)

    for (i in 0 until clearCode) {
      prefix[i] = 0
      suffix[i] = i
    }

    var codeSize = minCodeSize + 1
    var codeMask = (1 shl codeSize) - 1
    var available = clearCode + 2
    var oldCode = -1
    var first = 0

    var bitBuf = 0
    var bitCount = 0
    var bytePos = 0
    var pixelPos = 0

    fun nextCode(): Int {
      while (bitCount < codeSize && bytePos < data.size) {
        bitBuf = bitBuf or ((data[bytePos++].toInt() and 0xFF) shl bitCount)
        bitCount += 8
      }
      if (bitCount < codeSize) return -1
      val c = bitBuf and codeMask
      bitBuf = bitBuf ushr codeSize
      bitCount -= codeSize
      return c
    }

    while (pixelPos < expectedPixels) {
      val code = nextCode()
      if (code < 0 || code == endCode) break

      if (code == clearCode) {
        codeSize = minCodeSize + 1
        codeMask = (1 shl codeSize) - 1
        available = clearCode + 2
        oldCode = -1
        continue
      }

      if (oldCode == -1) {
        if (code < maxStack) {
          out[pixelPos++] = suffix[code]
          oldCode = code
          first = code
        }
        continue
      }

      var cur = code
      var top = 0
      if (cur == available) {
        stack[top++] = first
        cur = oldCode
      }
      while (cur >= clearCode && cur < maxStack && top < maxStack - 1) {
        stack[top++] = suffix[cur]
        cur = prefix[cur]
      }
      if (cur >= maxStack) break
      first = suffix[cur]
      stack[top++] = first

      if (available < maxStack) {
        prefix[available] = oldCode
        suffix[available] = first
        available++
        if ((available and codeMask) == 0 && available < maxStack) {
          codeSize++
          codeMask = (1 shl codeSize) - 1
        }
      }
      oldCode = code

      while (top > 0 && pixelPos < expectedPixels) {
        out[pixelPos++] = stack[--top]
      }
    }
    return out
  }

  private fun writeFramePixels(
    canvas: IntArray,
    screenW: Int,
    screenH: Int,
    indices: IntArray,
    imgLeft: Int,
    imgTop: Int,
    imgW: Int,
    imgH: Int,
    interlaced: Boolean,
    colorTable: IntArray,
    hasTransparency: Boolean,
    transparentIndex: Int,
  ) {
    var srcRow = 0
    if (!interlaced) {
      for (ry in 0 until imgH) {
        val dy = imgTop + ry
        if (dy in 0 until screenH) {
          val dstOff = dy * screenW
          val srcOff = ry * imgW
          for (rx in 0 until imgW) {
            val dx = imgLeft + rx
            if (dx in 0 until screenW) {
              val idx = indices[srcOff + rx]
              if ((!hasTransparency || idx != transparentIndex) && idx in colorTable.indices) {
                canvas[dstOff + dx] = colorTable[idx]
              }
            }
          }
        }
      }
    } else {
      val starts = intArrayOf(0, 4, 2, 1)
      val steps = intArrayOf(8, 8, 4, 2)
      for (pass in 0..3) {
        var ry = starts[pass]
        while (ry < imgH) {
          val dy = imgTop + ry
          if (dy in 0 until screenH) {
            val dstOff = dy * screenW
            val srcOff = srcRow * imgW
            for (rx in 0 until imgW) {
              val dx = imgLeft + rx
              if (dx in 0 until screenW) {
                val idx = indices[srcOff + rx]
                if ((!hasTransparency || idx != transparentIndex) && idx in colorTable.indices) {
                  canvas[dstOff + dx] = colorTable[idx]
                }
              }
            }
          }
          srcRow++
          ry += steps[pass]
        }
      }
    }
  }

  private class ByteCursor(private val bytes: ByteArray) {
    var pos = 0

    fun hasMore(): Boolean = pos < bytes.size

    fun remaining(): Int = (bytes.size - pos).coerceAtLeast(0)

    fun readU8(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else 0

    fun readU16(): Int {
      val lo = readU8()
      val hi = readU8()
      return lo or (hi shl 8)
    }

    fun skip(n: Int) {
      pos = min(bytes.size, pos + n.coerceAtLeast(0))
    }

    fun readBytes(n: Int): ByteArray {
      val count = min(n, remaining())
      val out = bytes.copyOfRange(pos, pos + count)
      pos += count
      return out
    }
  }
}
