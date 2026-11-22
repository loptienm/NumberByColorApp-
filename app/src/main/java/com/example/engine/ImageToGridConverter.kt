package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.PictureTemplate
import com.example.model.colorNumberToChar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImageSamplingStyle(val label: String) {
  AUTO("Auto"),
  CRISP("Drawing / Pixel"),
  SMOOTH("Photo"),
}

data class PixelGridEstimate(
  val stepPx: Float,
  val phaseX: Float,
  val phaseY: Float,
  val confidence: Float,
)

data class ConversionResult(
  val template: PictureTemplate,
  val detectedPixelSizePx: Float?,
)

/**
 * Offline image-to-color-by-number converter.
 * - Decodes high-res bitmaps (up to 1600px) without premature downscale blurring.
 * - Detects pixel-art block size and sub-pixel grid phase via 1D edge projections + sub-harmonic
 *   verification (handling JPEG compression, anti-aliased screenshots, and sprite sheets).
 * - Trims edge-connected backgrounds (including subtle gray gradients/checkerboards) at full
 *   resolution before cropping cleanly along pixel-grid boundaries.
 */
object ImageToGridConverter {

  suspend fun decodePreviewBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        while (
          bounds.outWidth / sampleSize > maxDim * 2 ||
            bounds.outHeight / sampleSize > maxDim * 2
        ) {
          sampleSize *= 2
        }

        val decodeOpts =
          BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
          }
        val raw =
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          } ?: return@withContext null

        val scale = min(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height)
        if (scale >= 1f) {
          raw
        } else {
          val w = max(1, (raw.width * scale).roundToInt())
          val h = max(1, (raw.height * scale).roundToInt())
          Bitmap.createScaledBitmap(raw, w, h, true).also {
            if (it !== raw) raw.recycle()
          }
        }
      } catch (_: Throwable) {
        null
      }
    }

  suspend fun convertBitmapToResult(
    bitmap: Bitmap,
    id: String,
    title: String,
    maxGridDim: Int,
    maxColors: Int,
    trimBackground: Boolean,
    samplingStyle: ImageSamplingStyle = ImageSamplingStyle.AUTO,
  ): ConversionResult =
    convertFramesToResult(
      frames = listOf(bitmap),
      frameMs = 400,
      id = id,
      title = title,
      maxGridDim = maxGridDim,
      maxColors = maxColors,
      trimBackground = trimBackground,
      samplingStyle = samplingStyle,
    )

  suspend fun convertFramesToResult(
    frames: List<Bitmap>,
    frameMs: Int,
    id: String,
    title: String,
    maxGridDim: Int,
    maxColors: Int,
    trimBackground: Boolean,
    samplingStyle: ImageSamplingStyle = ImageSamplingStyle.AUTO,
  ): ConversionResult =
    withContext(Dispatchers.Default) {
      val validFrames = frames.filter { it.width > 0 && it.height > 0 }
      val firstFrame = validFrames.firstOrNull()
      if (firstFrame == null) {
        val empty =
          PictureTemplate(
            id = id,
            title = title.ifBlank { "My Drawing" },
            gridWidth = 8,
            gridHeight = 8,
            palette = mapOf(1 to 0xFF4F46E5.toInt()),
            grid = List(8) { "1".repeat(8) },
            isCustom = true,
          )
        return@withContext ConversionResult(empty, null)
      }

      val autoColors = maxColors <= 0
      val clampedColors = if (autoColors) 24 else maxColors.coerceIn(3, 24)
      val srcW = firstFrame.width.coerceAtLeast(1)
      val srcH = firstFrame.height.coerceAtLeast(1)

      val framePixelsList =
        validFrames.map { bmp ->
          val normalized =
            if (bmp.width == srcW && bmp.height == srcH) bmp
            else Bitmap.createScaledBitmap(bmp, srcW, srcH, false)
          IntArray(srcW * srcH).also { px -> normalized.getPixels(px, 0, srcW, 0, 0, srcW, srcH) }
        }

      val primaryPixels = framePixelsList.first()

      // 1. Detect pixel-art block size & phase across frame 0 before any cropping
      val minConfidence = if (samplingStyle == ImageSamplingStyle.CRISP) 0.16f else 0.22f
      val pixelEstimate =
        if (samplingStyle == ImageSamplingStyle.SMOOTH) null
        else detectPixelArtGrid(primaryPixels, srcW, srcH, minConfidence)

      // 2. Detect background at full source resolution per frame
      val frameBgMasks =
        framePixelsList.map { pixels ->
          val isBg = BooleanArray(pixels.size)
          if (trimBackground) {
            markEdgeBackgroundFullRes(pixels, srcW, srcH, isBg)
          } else {
            for (i in pixels.indices) {
              if (((pixels[i] ushr 24) and 0xFF) < 100) isBg[i] = true
            }
          }
          if (isBg.all { it }) {
            for (i in pixels.indices) {
              isBg[i] = ((pixels[i] ushr 24) and 0xFF) < 100
            }
          }
          isBg
        }

      // 3. Find tight union foreground bounding box across all frames so animation doesn't clip
      var minX = srcW
      var maxX = -1
      var minY = srcH
      var maxY = -1
      for (isBg in frameBgMasks) {
        for (y in 0 until srcH) {
          val rowOff = y * srcW
          for (x in 0 until srcW) {
            if (!isBg[rowOff + x]) {
              if (x < minX) minX = x
              if (x > maxX) maxX = x
              if (y < minY) minY = y
              if (y > maxY) maxY = y
            }
          }
        }
      }
      if (maxX < minX || maxY < minY) {
        minX = 0
        maxX = srcW - 1
        minY = 0
        maxY = srcH - 1
      }

      val useCrispSampling =
        when (samplingStyle) {
          ImageSamplingStyle.CRISP -> true
          ImageSamplingStyle.SMOOTH -> false
          ImageSamplingStyle.AUTO -> pixelEstimate != null
        }

      // 4. Determine grid origin, cell dimensions, and grid counts
      val cropW = (maxX - minX + 1).coerceAtLeast(1)
      val cropH = (maxY - minY + 1).coerceAtLeast(1)

      val originX: Float
      val originY: Float
      val stepX: Float
      val stepY: Float
      val gridW: Int
      val gridH: Int

      if (pixelEstimate != null) {
        // Always align origin & bounds to detected pixel-art blocks
        val cStart = floor((minX - pixelEstimate.phaseX) / pixelEstimate.stepPx).toInt()
        val cEnd = ceil((maxX + 1 - pixelEstimate.phaseX) / pixelEstimate.stepPx).toInt()
        val rStart = floor((minY - pixelEstimate.phaseY) / pixelEstimate.stepPx).toInt()
        val rEnd = ceil((maxY + 1 - pixelEstimate.phaseY) / pixelEstimate.stepPx).toInt()

        val rawW = (cEnd - cStart).coerceAtLeast(8)
        val rawH = (rEnd - rStart).coerceAtLeast(8)
        val maxSide = max(rawW, rawH)
        val cap = if (maxGridDim <= 0) 144 else maxGridDim.coerceIn(16, 144)

        if (maxSide <= cap) {
          gridW = rawW
          gridH = rawH
          stepX = pixelEstimate.stepPx
          stepY = pixelEstimate.stepPx
          originX = pixelEstimate.phaseX + cStart * stepX
          originY = pixelEstimate.phaseY + rStart * stepY
        } else {
          val scale = cap.toFloat() / maxSide
          gridW = max(8, (rawW * scale).roundToInt())
          gridH = max(8, (rawH * scale).roundToInt())
          val alignedW = rawW * pixelEstimate.stepPx
          val alignedH = rawH * pixelEstimate.stepPx
          originX = pixelEstimate.phaseX + cStart * pixelEstimate.stepPx
          originY = pixelEstimate.phaseY + rStart * pixelEstimate.stepPx
          stepX = alignedW / gridW
          stepY = alignedH / gridH
        }
      } else {
        val targetMax = if (maxGridDim <= 0) 36 else maxGridDim.coerceIn(16, 144)
        val aspect = cropW.toFloat() / cropH
        if (aspect >= 1f) {
          gridW = targetMax
          gridH = max(8, (targetMax / aspect).roundToInt())
        } else {
          gridW = max(8, (targetMax * aspect).roundToInt())
          gridH = targetMax
        }
        originX = minX.toFloat()
        originY = minY.toFloat()
        stepX = cropW.toFloat() / gridW
        stepY = cropH.toFloat() / gridH
      }

      // 5. Sample each grid cell cleanly for every frame
      val frameCellColors = Array(framePixelsList.size) { IntArray(gridW * gridH) }
      val frameCellIsBg = Array(framePixelsList.size) { BooleanArray(gridW * gridH) }

      for (fIdx in framePixelsList.indices) {
        val pixels = framePixelsList[fIdx]
        val isBg = frameBgMasks[fIdx]
        val colors = frameCellColors[fIdx]
        val bgOut = frameCellIsBg[fIdx]

        for (r in 0 until gridH) {
          val y0 = originY + r * stepY
          val y1 = y0 + stepY
          for (c in 0 until gridW) {
            val x0 = originX + c * stepX
            val x1 = x0 + stepX
            val cellIdx = r * gridW + c
            val sampled =
              sampleCell(
                pixels = pixels,
                srcW = srcW,
                srcH = srcH,
                isBg = isBg,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                crisp = useCrispSampling,
              )
            if (sampled == null) {
              bgOut[cellIdx] = true
            } else {
              colors[cellIdx] = sampled
            }
          }
        }
      }

      // Trim completely empty outer rows/columns across ALL frames
      fun colEmptyAllFrames(c: Int, top: Int, bottom: Int): Boolean =
        frameCellIsBg.all { bg -> (top..bottom).all { r -> bg[r * gridW + c] } }

      fun rowEmptyAllFrames(r: Int): Boolean =
        frameCellIsBg.all { bg -> (0 until gridW).all { c -> bg[r * gridW + c] } }

      var trimTop = 0
      while (trimTop < gridH && rowEmptyAllFrames(trimTop)) {
        trimTop++
      }
      var trimBottom = gridH - 1
      while (trimBottom > trimTop && rowEmptyAllFrames(trimBottom)) {
        trimBottom--
      }
      var trimLeft = 0
      while (trimLeft < gridW && colEmptyAllFrames(trimLeft, trimTop, trimBottom)) {
        trimLeft++
      }
      var trimRight = gridW - 1
      while (trimRight > trimLeft && colEmptyAllFrames(trimRight, trimTop, trimBottom)) {
        trimRight--
      }

      val finalW = (trimRight - trimLeft + 1).coerceAtLeast(1)
      val finalH = (trimBottom - trimTop + 1).coerceAtLeast(1)
      val trimmedColorsPerFrame =
        Array(framePixelsList.size) { fIdx ->
          IntArray(finalW * finalH).also { dst ->
            val src = frameCellColors[fIdx]
            for (r in 0 until finalH) {
              for (c in 0 until finalW) {
                dst[r * finalW + c] = src[(trimTop + r) * gridW + (trimLeft + c)]
              }
            }
          }
        }
      val trimmedBgPerFrame =
        Array(framePixelsList.size) { fIdx ->
          BooleanArray(finalW * finalH).also { dst ->
            val src = frameCellIsBg[fIdx]
            for (r in 0 until finalH) {
              for (c in 0 until finalW) {
                dst[r * finalW + c] = src[(trimTop + r) * gridW + (trimLeft + c)]
              }
            }
          }
        }

      val primaryColors = trimmedColorsPerFrame[0]
      val primaryBg = trimmedBgPerFrame[0]
      val primaryFgIndices = primaryColors.indices.filter { !primaryBg[it] }
      if (primaryFgIndices.isEmpty()) {
        val fallback =
          PictureTemplate(
            id = id,
            title = title.ifBlank { "My Drawing" },
            gridWidth = finalW,
            gridHeight = finalH,
            palette = mapOf(1 to 0xFF4F46E5.toInt()),
            grid = List(finalH) { "1".repeat(finalW) },
            isCustom = true,
          )
        return@withContext ConversionResult(fallback, pixelEstimate?.stepPx)
      }

      // 6. Quantize foreground colors from Frame 0 in perceptual CIE94 space so every palette entry
      //    appears on the paintable grid (zero orphan colors), then share that palette across all frames.
      val centroids = quantizeForegroundColors(primaryColors, primaryFgIndices, clampedColors)
      val centroidLabs = Array(centroids.size) { rgbToLab(centroids[it]) }

      val primaryCounts = IntArray(centroids.size)
      val frameAssignments =
        Array(trimmedColorsPerFrame.size) { fIdx ->
          val colors = trimmedColorsPerFrame[fIdx]
          val bg = trimmedBgPerFrame[fIdx]
          IntArray(colors.size).also { out ->
            for (idx in colors.indices) {
              if (bg[idx]) continue
              val lab = rgbToLab(colors[idx])
              var best = 0
              var bestDist = Float.MAX_VALUE
              for (k in centroidLabs.indices) {
                val d = labDistSq(lab, centroidLabs[k])
                if (d < bestDist) {
                  bestDist = d
                  best = k
                }
              }
              out[idx] = best + 1
              if (fIdx == 0) primaryCounts[best]++
            }
          }
        }

      val activeClusters =
        centroids.indices.filter { primaryCounts[it] > 0 }.sortedByDescending { primaryCounts[it] }
      val remap = IntArray(centroids.size + 1)
      val palette = linkedMapOf<Int, Int>()
      activeClusters.forEachIndexed { newIdx, oldCluster ->
        val num = newIdx + 1
        remap[oldCluster + 1] = num
        palette[num] = centroids[oldCluster] or (0xFF shl 24)
      }

      val encodedFrames =
        frameAssignments.map { assignments ->
          List(finalH) { r ->
            buildString(finalW) {
              for (c in 0 until finalW) {
                val rawCluster = assignments[r * finalW + c]
                val colorNum = if (rawCluster == 0) 0 else remap[rawCluster]
                append(colorNumberToChar(colorNum))
              }
            }
          }
        }

      ConversionResult(
        template =
          PictureTemplate(
            id = id,
            title = title.ifBlank { "My Drawing" },
            gridWidth = finalW,
            gridHeight = finalH,
            palette = palette,
            grid = encodedFrames.first(),
            frames = if (encodedFrames.size > 1) encodedFrames else emptyList(),
            frameMs = frameMs.coerceIn(80, 1200),
            isCustom = true,
          ),
        detectedPixelSizePx = pixelEstimate?.stepPx,
      )
    }

  suspend fun convertBitmapToTemplate(
    bitmap: Bitmap,
    id: String,
    title: String,
    maxGridDim: Int,
    maxColors: Int,
    trimBackground: Boolean,
    samplingStyle: ImageSamplingStyle = ImageSamplingStyle.AUTO,
  ): PictureTemplate =
    convertBitmapToResult(
      bitmap = bitmap,
      id = id,
      title = title,
      maxGridDim = maxGridDim,
      maxColors = maxColors,
      trimBackground = trimBackground,
      samplingStyle = samplingStyle,
    ).template

  /**
   * Finds the fundamental pixel-art block size and sub-pixel grid origin using horizontal & vertical
   * gradient projections plus sub-harmonic verification (s/2, s/3).
   */
  internal fun detectPixelArtGrid(
    pixels: IntArray,
    w: Int,
    h: Int,
    minConfidence: Float = 0.22f,
  ): PixelGridEstimate? {
    if (w < 48 || h < 48) return null

    val gx = FloatArray(w)
    val gy = FloatArray(h)
    val rowStep = max(1, h / 120)
    val colStep = max(1, w / 120)

    for (y in 0 until h step rowStep) {
      val rowOff = y * w
      for (x in 1 until (w - 1)) {
        val c0 = pixels[rowOff + x - 1]
        val c1 = pixels[rowOff + x]
        val d =
          kotlin.math.abs(((c1 shr 16) and 0xFF) - ((c0 shr 16) and 0xFF)) +
            kotlin.math.abs(((c1 shr 8) and 0xFF) - ((c0 shr 8) and 0xFF)) +
            kotlin.math.abs((c1 and 0xFF) - (c0 and 0xFF))
        if (d > 45) gx[x] += d.toFloat()
      }
    }

    for (x in 0 until w step colStep) {
      for (y in 1 until (h - 1)) {
        val c0 = pixels[(y - 1) * w + x]
        val c1 = pixels[y * w + x]
        val d =
          kotlin.math.abs(((c1 shr 16) and 0xFF) - ((c0 shr 16) and 0xFF)) +
            kotlin.math.abs(((c1 shr 8) and 0xFF) - ((c0 shr 8) and 0xFF)) +
            kotlin.math.abs((c1 and 0xFF) - (c0 and 0xFF))
        if (d > 45) gy[y] += d.toFloat()
      }
    }

    val totX = gx.sum()
    val totY = gy.sum()
    if (totX < 1f || totY < 1f) return null

    fun lineEnergy(g: FloatArray, n: Int, p: Float, ph: Float): Float {
      var s = 0f
      var idx = ph
      while (idx < n - 1) {
        val c = idx.roundToInt()
        if (c in 0 until n) {
          s += g[c]
          if (c - 1 >= 0) s += 0.35f * g[c - 1]
          if (c + 1 < n) s += 0.35f * g[c + 1]
        }
        idx += p
      }
      return s
    }

    fun bestPhase(g: FloatArray, n: Int, p: Float): Pair<Float, Float> {
      val steps = max(8, (p * 4f).toInt())
      var bestE = -1f
      var bestPh = 0f
      val stepPh = p / steps
      for (k in 0 until steps) {
        val ph = k * stepPh
        val e = lineEnergy(g, n, p, ph)
        if (e > bestE) {
          bestE = e
          bestPh = ph
        }
      }
      return bestE to bestPh
    }

    var bestScore = -1f
    var bestP = 0f
    var bestPhX = 0f
    var bestPhY = 0f

    // Coarse search 3.5..32.0 px in 0.25 px steps
    for (p4 in 14..128) {
      val p = p4 / 4f
      val (ex, phX) = bestPhase(gx, w, p)
      val (ey, phY) = bestPhase(gy, h, p)
      val expFrac = min(0.8f, 1.7f / p)
      val score = (ex / totX - expFrac) + (ey / totY - expFrac)
      if (score > bestScore) {
        bestScore = score
        bestP = p
        bestPhX = phX
        bestPhY = phY
      }
    }

    if (bestP <= 0f) return null

    // Sub-harmonic refinement: ensure a 2x or 3x harmonic didn't win over the true fundamental step
    for (div in intArrayOf(3, 2)) {
      while (bestP / div >= 3.5f) {
        val pSub = bestP / div
        val (exFull, _) = bestPhase(gx, w, bestP)
        val (eyFull, _) = bestPhase(gy, h, bestP)
        val (exSub, phXSub) = bestPhase(gx, w, pSub)
        val (eySub, phYSub) = bestPhase(gy, h, pSub)
        val addedX = (exSub - exFull) / max(1f, exFull)
        val addedY = (eySub - eyFull) / max(1f, eyFull)
        val thresh = if (div == 2) 0.38f else 0.70f
        if (addedX >= thresh && addedY >= thresh) {
          bestP = pSub
          bestPhX = phXSub
          bestPhY = phYSub
        } else {
          break
        }
      }
    }

    // Fine-tune period in 0.025 px steps around bestP
    var fineBest = -1f
    var fineP = bestP
    var finePhX = bestPhX
    var finePhY = bestPhY
    for (k in -10..10) {
      val p = bestP + k * 0.025f
      if (p < 3.5f) continue
      val (ex, phX) = bestPhase(gx, w, p)
      val (ey, phY) = bestPhase(gy, h, p)
      val s = ex / totX + ey / totY
      if (s > fineBest) {
        fineBest = s
        fineP = p
        finePhX = phX
        finePhY = phY
      }
    }

    val expFrac = min(0.8f, 1.7f / fineP)
    val confidence = fineBest - 2f * expFrac
    if (confidence < minConfidence) return null

    return PixelGridEstimate(
      stepPx = fineP,
      phaseX = finePhX,
      phaseY = finePhY,
      confidence = confidence,
    )
  }

  private fun markEdgeBackgroundFullRes(
    pixels: IntArray,
    w: Int,
    h: Int,
    outBg: BooleanArray,
  ) {
    for (i in pixels.indices) {
      if (((pixels[i] ushr 24) and 0xFF) < 100) outBg[i] = true
    }

    val borderSamples = mutableListOf<Int>()
    val stepX = max(1, w / 64)
    val stepY = max(1, h / 64)
    for (c in 0 until w step stepX) {
      borderSamples.add(pixels[c])
      borderSamples.add(pixels[(h - 1) * w + c])
    }
    for (r in 0 until h step stepY) {
      borderSamples.add(pixels[r * w])
      borderSamples.add(pixels[r * w + (w - 1)])
    }

    val opaqueBorders = borderSamples.filter { ((it ushr 24) and 0xFF) >= 100 }
    if (opaqueBorders.isEmpty()) return

    val bucketCounts = mutableMapOf<Int, MutableList<Int>>()
    for (px in opaqueBorders) {
      val key =
        (((px shr 16) and 0xF0) shl 12) or
          (((px shr 8) and 0xF0) shl 4) or
          ((px and 0xF0) shr 4)
      bucketCounts.getOrPut(key) { mutableListOf() }.add(px)
    }
    val modalGroup = bucketCounts.maxByOrNull { it.value.size }?.value ?: return
    val ref = modalGroup.first()
    val refR = (ref shr 16) and 0xFF
    val refG = (ref shr 8) and 0xFF
    val refB = ref and 0xFF
    val refSat = maxOf(refR, refG, refB) - minOf(refR, refG, refB)
    val refLum = (refR * 299 + refG * 587 + refB * 114) / 1000
    val neutralLightBackdrop = refSat <= 18 && refLum >= 180

    val isUniformOrLightBorder =
      neutralLightBackdrop ||
        opaqueBorders.count { colorDistSq(it, ref) <= 28 * 28 } >= opaqueBorders.size * 0.50

    if (!isUniformOrLightBorder) return

    val strictDistSq = 22 * 22
    val gradientStepDistSq = 24 * 24
    val maxNeutralDriftSq = 60 * 60
    val queue = ArrayDeque<Int>()

    fun trySeed(r: Int, c: Int) {
      val idx = r * w + c
      if (outBg[idx]) return
      val px = pixels[idx]
      val a = (px ushr 24) and 0xFF
      if (a < 100 || colorDistSq(px, ref) <= 28 * 28) {
        outBg[idx] = true
        queue.addLast(idx)
      }
    }

    fun tryExpand(nr: Int, nc: Int, fromPx: Int) {
      val idx = nr * w + nc
      if (outBg[idx]) return
      val px = pixels[idx]
      val a = (px ushr 24) and 0xFF
      if (a < 100) {
        outBg[idx] = true
        queue.addLast(idx)
        return
      }
      if (colorDistSq(px, ref) <= strictDistSq) {
        outBg[idx] = true
        queue.addLast(idx)
        return
      }
      if (neutralLightBackdrop) {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        val sat = maxOf(r, g, b) - minOf(r, g, b)
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        if (
          sat <= 18 &&
            lum >= 175 &&
            colorDistSq(px, fromPx) <= gradientStepDistSq &&
            colorDistSq(px, ref) <= maxNeutralDriftSq
        ) {
          outBg[idx] = true
          queue.addLast(idx)
        }
      }
    }

    for (c in 0 until w) {
      trySeed(0, c)
      trySeed(h - 1, c)
    }
    for (r in 0 until h) {
      trySeed(r, 0)
      trySeed(r, w - 1)
    }

    while (queue.isNotEmpty()) {
      val cur = queue.removeFirst()
      val r = cur / w
      val c = cur % w
      val curPx = pixels[cur]
      if (r > 0) tryExpand(r - 1, c, curPx)
      if (r + 1 < h) tryExpand(r + 1, c, curPx)
      if (c > 0) tryExpand(r, c - 1, curPx)
      if (c + 1 < w) tryExpand(r, c + 1, curPx)
    }
  }

  private fun sampleCell(
    pixels: IntArray,
    srcW: Int,
    srcH: Int,
    isBg: BooleanArray,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    crisp: Boolean,
  ): Int? {
    val cellW = (x1 - x0).coerceAtLeast(1f)
    val cellH = (y1 - y0).coerceAtLeast(1f)
    // Sample inside the central 50% of the pixel block to dodge JPEG edge compression ringing
    val fractions =
      if (crisp) floatArrayOf(0.28f, 0.50f, 0.72f)
      else floatArrayOf(0.18f, 0.40f, 0.60f, 0.82f)

    var bgCount = 0
    var total = 0
    val fgSamples = IntArray(fractions.size * fractions.size)
    var fgCount = 0

    for (fy in fractions) {
      val sy = (y0 + fy * cellH).toInt().coerceIn(0, srcH - 1)
      val rowOff = sy * srcW
      for (fx in fractions) {
        val sx = (x0 + fx * cellW).toInt().coerceIn(0, srcW - 1)
        val idx = rowOff + sx
        total++
        if (isBg[idx]) {
          bgCount++
        } else {
          fgSamples[fgCount++] = pixels[idx] or (0xFF shl 24)
        }
      }
    }

    if (fgCount == 0 || bgCount > total / 2) return null

    if (!crisp) {
      var sr = 0L
      var sg = 0L
      var sb = 0L
      for (i in 0 until fgCount) {
        val px = fgSamples[i]
        sr += (px shr 16) and 0xFF
        sg += (px shr 8) and 0xFF
        sb += px and 0xFF
      }
      val r = (sr / fgCount).toInt().coerceIn(0, 255)
      val g = (sg / fgCount).toInt().coerceIn(0, 255)
      val b = (sb / fgCount).toInt().coerceIn(0, 255)
      return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    val buckets = linkedMapOf<Int, MutableList<Int>>()
    for (i in 0 until fgCount) {
      val px = fgSamples[i]
      val key =
        (((px shr 19) and 0x1F) shl 10) or
          (((px shr 11) and 0x1F) shl 5) or
          ((px shr 3) and 0x1F)
      buckets.getOrPut(key) { mutableListOf() }.add(px)
    }
    val winner = buckets.maxByOrNull { it.value.size }?.value ?: return fgSamples[0]
    var sr = 0L
    var sg = 0L
    var sb = 0L
    for (px in winner) {
      sr += (px shr 16) and 0xFF
      sg += (px shr 8) and 0xFF
      sb += px and 0xFF
    }
    val n = winner.size
    return (0xFF shl 24) or
      ((sr / n).toInt().coerceIn(0, 255) shl 16) or
      ((sg / n).toInt().coerceIn(0, 255) shl 8) or
      (sb / n).toInt().coerceIn(0, 255)
  }

  private data class LabColor(val l: Float, val a: Float, val b: Float)

  private data class ColorCluster(
    var argb: Int,
    var lab: LabColor,
    var sumR: Long,
    var sumG: Long,
    var sumB: Long,
    var count: Int,
  )

  private fun rgbToLab(argb: Int): LabColor {
    fun linearize(channel: Int): Double {
      val v = channel / 255.0
      return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    val r = linearize((argb shr 16) and 0xFF)
    val g = linearize((argb shr 8) and 0xFF)
    val b = linearize(argb and 0xFF)

    val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
    val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000
    val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883

    fun f(t: Double): Double =
      if (t > 0.008856) Math.cbrt(t) else (7.787 * t + 16.0 / 116.0)

    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return LabColor(
      l = (116.0 * fy - 16.0).toFloat(),
      a = (500.0 * (fx - fy)).toFloat(),
      b = (200.0 * (fy - fz)).toFloat(),
    )
  }

  // CIE94 perceptual distance squared: handles high-chroma yellows/oranges and near-black outlines accurately
  private fun labDistSq(c1: LabColor, c2: LabColor): Float {
    val dl = c1.l - c2.l
    val da = c1.a - c2.a
    val db = c1.b - c2.b
    val c1Chroma = sqrt((c1.a * c1.a + c1.b * c1.b).toDouble()).toFloat()
    val c2Chroma = sqrt((c2.a * c2.a + c2.b * c2.b).toDouble()).toFloat()
    val dc = c1Chroma - c2Chroma
    val dhSq = max(0f, da * da + db * db - dc * dc)

    val cBar = 0.5f * (c1Chroma + c2Chroma)
    val lBar = 0.5f * (c1.l + c2.l)
    // Soften distinctions in near-black outlines/eyes (L < 24) and damp amplified Lab chroma noise near black
    val sl =
      when {
        lBar < 16f -> 1.9f
        lBar < 24f -> 1.5f
        else -> 1.0f
      }
    val chromaDamp = if (lBar < 16f) 2.2f - 0.075f * lBar else 1.0f
    val sc = (1.0f + 0.045f * cBar) * chromaDamp
    val sh = (1.0f + 0.015f * cBar) * (if (lBar < 22f) 1.5f else 1.0f) * chromaDamp

    val termL = dl / sl
    val termC = dc / sc
    return termL * termL + termC * termC + dhSq / (sh * sh)
  }

  private fun quantizeForegroundColors(
    cellColors: IntArray,
    fgIndices: List<Int>,
    maxColors: Int,
  ): IntArray {
    fun mergeClusterInto(target: ColorCluster, absorbed: ColorCluster) {
      target.sumR += absorbed.sumR
      target.sumG += absorbed.sumG
      target.sumB += absorbed.sumB
      target.count += absorbed.count
      val nr = (target.sumR / target.count).toInt().coerceIn(0, 255)
      val ng = (target.sumG / target.count).toInt().coerceIn(0, 255)
      val nb = (target.sumB / target.count).toInt().coerceIn(0, 255)
      val mergedArgb = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
      target.argb = mergedArgb
      target.lab = rgbToLab(mergedArgb)
    }

    // 1. Pre-group nearly identical shades in perceptual CIE94 space
    val preMergeDistSq = 5.0f * 5.0f
    val clusters = mutableListOf<ColorCluster>()

    for (idx in fgIndices) {
      val px = cellColors[idx]
      val r = (px shr 16) and 0xFF
      val g = (px shr 8) and 0xFF
      val b = px and 0xFF
      val lab = rgbToLab(px)

      var matched = -1
      var bestD = Float.MAX_VALUE
      for (i in clusters.indices) {
        val d = labDistSq(lab, clusters[i].lab)
        if (d <= preMergeDistSq && d < bestD) {
          bestD = d
          matched = i
        }
      }
      if (matched >= 0) {
        val cl = clusters[matched]
        cl.sumR += r
        cl.sumG += g
        cl.sumB += b
        cl.count += 1
        val nr = (cl.sumR / cl.count).toInt().coerceIn(0, 255)
        val ng = (cl.sumG / cl.count).toInt().coerceIn(0, 255)
        val nb = (cl.sumB / cl.count).toInt().coerceIn(0, 255)
        val mergedArgb = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        cl.argb = mergedArgb
        cl.lab = rgbToLab(mergedArgb)
      } else {
        clusters.add(
          ColorCluster(
            argb = px or (0xFF shl 24),
            lab = lab,
            sumR = r.toLong(),
            sumG = g.toLong(),
            sumB = b.toLong(),
            count = 1,
          )
        )
      }
    }

    // 2. Agglomeratively smash perceptually indistinguishable shades (e.g., nearly identical
    //    yellows/oranges and off-black JPEG chroma bleed) into unified colors.
    val minDistinctSq = 9.0f * 9.0f
    while (clusters.size > 1) {
      var bestI = -1
      var bestJ = -1
      var bestDistSq = Float.MAX_VALUE
      for (i in 0 until clusters.size) {
        for (j in i + 1 until clusters.size) {
          val d = labDistSq(clusters[i].lab, clusters[j].lab)
          if (d < bestDistSq) {
            bestDistSq = d
            bestI = i
            bestJ = j
          }
        }
      }
      if (bestI < 0 || bestDistSq >= minDistinctSq) break
      val keep = if (clusters[bestI].count >= clusters[bestJ].count) bestI else bestJ
      val drop = if (keep == bestI) bestJ else bestI
      mergeClusterInto(clusters[keep], clusters[drop])
      clusters.removeAt(drop)
    }

    // 2b. Fold tiny 1–few block JPEG/anti-aliasing fringe hues (like slight off-black eye/outline
    //     anti-aliasing) into a much larger close neighbor while preserving distinct small accents.
    val fringeMaxCount = max(2, fgIndices.size / 350)
    val fringeDistSq = 17.5f * 17.5f
    while (clusters.size > 1) {
      var bestKeep = -1
      var bestDrop = -1
      var bestCost = Double.MAX_VALUE
      for (i in 0 until clusters.size) {
        for (j in i + 1 until clusters.size) {
          val small = if (clusters[i].count <= clusters[j].count) i else j
          val big = if (small == i) j else i
          val smallCount = clusters[small].count
          val bigCount = clusters[big].count
          if (smallCount > fringeMaxCount || bigCount < smallCount * 6) continue
          val d = labDistSq(clusters[i].lab, clusters[j].lab)
          if (d <= fringeDistSq) {
            val cost = d.toDouble() * (1.0 + kotlin.math.ln(smallCount.toDouble()))
            if (cost < bestCost) {
              bestCost = cost
              bestKeep = big
              bestDrop = small
            }
          }
        }
      }
      if (bestKeep < 0) break
      mergeClusterInto(clusters[bestKeep], clusters[bestDrop])
      clusters.removeAt(bestDrop)
    }

    // 3. Count-weighted agglomerative merging: whenever the user lowers Max Colors, combine each
    //    removed color directly into its most similar remaining color (smallest/closest first).
    while (clusters.size > maxColors && clusters.size > 1) {
      var bestI = -1
      var bestJ = -1
      var bestCost = Double.MAX_VALUE
      for (i in 0 until clusters.size) {
        for (j in i + 1 until clusters.size) {
          val d = labDistSq(clusters[i].lab, clusters[j].lab)
          val minCount = min(clusters[i].count, clusters[j].count)
          val cost = d.toDouble() * kotlin.math.ln(1.35 + minCount.toDouble())
          if (cost < bestCost) {
            bestCost = cost
            bestI = i
            bestJ = j
          }
        }
      }
      if (bestI < 0) break
      val keep = if (clusters[bestI].count >= clusters[bestJ].count) bestI else bestJ
      val drop = if (keep == bestI) bestJ else bestI
      mergeClusterInto(clusters[keep], clusters[drop])
      clusters.removeAt(drop)
    }

    return clusters.map { it.argb }.toIntArray()
  }

  private fun colorDistSq(c1: Int, c2: Int): Int {
    val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
    val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
    val db = (c1 and 0xFF) - (c2 and 0xFF)
    return dr * dr + dg * dg + db * db
  }
}
