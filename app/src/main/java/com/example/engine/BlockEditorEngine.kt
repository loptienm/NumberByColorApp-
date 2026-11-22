package com.example.engine

import com.example.model.PictureTemplate
import com.example.model.charToColorNumber
import com.example.model.colorNumberToChar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class EditorTool {
  DRAW,
  ERASE,
  EYEDROPPER,
  FLOOD_FILL,
  GLOBAL_REPLACE,
  SHAPE,
  PAN,
}

enum class EditorShapeType {
  LINE,
  RECTANGLE,
  OVAL,
  TRIANGLE,
}

data class ShapeDraft(
  val shapeType: EditorShapeType,
  val centerCol: Float,
  val centerRow: Float,
  val halfWidth: Float,
  val halfHeight: Float,
  val rotationDeg: Float = 0f,
  val lineStartCol: Float = centerCol - halfWidth,
  val lineStartRow: Float = centerRow,
  val lineEndCol: Float = centerCol + halfWidth,
  val lineEndRow: Float = centerRow,
  val filled: Boolean = false,
)

data class PaletteRemapResult(
  val palette: Map<Int, Int>,
  val frames: List<List<String>>,
  val visibleColors: Set<Int>,
  val oldToNewMap: Map<Int, Int>,
)

object BlockEditorEngine {
  const val MAX_PALETTE_COLORS = 35
  const val MAX_FRAMES = 24

  fun normalizeFrames(template: PictureTemplate): List<List<String>> {
    val raw = if (template.frames.isNotEmpty()) template.frames else listOf(template.grid)
    return raw.map { frame ->
      List(template.gridHeight) { r ->
        val line = frame.getOrNull(r) ?: ""
        buildString(template.gridWidth) {
          for (c in 0 until template.gridWidth) {
            append(line.getOrNull(c) ?: '.')
          }
        }
      }
    }
  }

  fun cellAt(frame: List<String>, row: Int, col: Int): Int {
    val line = frame.getOrNull(row) ?: return 0
    val ch = line.getOrNull(col) ?: return 0
    return charToColorNumber(ch)
  }

  fun paintCell(frame: List<String>, row: Int, col: Int, colorNumber: Int): List<String> {
    if (row !in frame.indices) return frame
    val line = frame[row]
    if (col !in line.indices) return frame
    val newChar = colorNumberToChar(colorNumber)
    if (line[col] == newChar) return frame
    val updatedRow =
      buildString(line.length) {
        append(line, 0, col)
        append(newChar)
        append(line, col + 1, line.length)
      }
    return frame.toMutableList().apply { set(row, updatedRow) }
  }

  fun paintStroke(
    frame: List<String>,
    fromRow: Int?,
    fromCol: Int?,
    toRow: Int,
    toCol: Int,
    colorNumber: Int,
  ): List<String> {
    if (frame.isEmpty()) return frame
    val h = frame.size
    val w = frame[0].length
    if (toRow !in 0 until h || toCol !in 0 until w) return frame
    val startR = fromRow?.coerceIn(0, h - 1) ?: toRow
    val startC = fromCol?.coerceIn(0, w - 1) ?: toCol
    val steps = max(abs(toRow - startR), abs(toCol - startC)).coerceAtLeast(1)
    val newChar = colorNumberToChar(colorNumber)
    val rows = Array(h) { r -> frame[r].toCharArray() }
    var changed = false
    for (i in 0..steps) {
      val rr = (startR + ((toRow - startR) * i.toFloat() / steps)).toInt().coerceIn(0, h - 1)
      val cc = (startC + ((toCol - startC) * i.toFloat() / steps)).toInt().coerceIn(0, w - 1)
      if (rows[rr][cc] != newChar) {
        rows[rr][cc] = newChar
        changed = true
      }
    }
    return if (changed) rows.map { String(it) } else frame
  }

  fun floodFill(
    frame: List<String>,
    startRow: Int,
    startCol: Int,
    replacementColor: Int,
  ): List<String> {
    if (frame.isEmpty()) return frame
    val h = frame.size
    val w = frame[0].length
    if (startRow !in 0 until h || startCol !in 0 until w) return frame
    val targetChar = frame[startRow][startCol]
    val newChar = colorNumberToChar(replacementColor)
    if (targetChar == newChar) return frame

    val rows = Array(h) { r -> frame[r].toCharArray() }
    val queue = ArrayDeque<Int>()
    val visited = BooleanArray(h * w)
    val startIdx = startRow * w + startCol
    queue.addLast(startIdx)
    visited[startIdx] = true

    while (queue.isNotEmpty()) {
      val idx = queue.removeFirst()
      val r = idx / w
      val c = idx % w
      rows[r][c] = newChar

      if (r > 0) {
        val n = (r - 1) * w + c
        if (!visited[n] && rows[r - 1][c] == targetChar) {
          visited[n] = true
          queue.addLast(n)
        }
      }
      if (r + 1 < h) {
        val n = (r + 1) * w + c
        if (!visited[n] && rows[r + 1][c] == targetChar) {
          visited[n] = true
          queue.addLast(n)
        }
      }
      if (c > 0) {
        val n = r * w + (c - 1)
        if (!visited[n] && rows[r][c - 1] == targetChar) {
          visited[n] = true
          queue.addLast(n)
        }
      }
      if (c + 1 < w) {
        val n = r * w + (c + 1)
        if (!visited[n] && rows[r][c + 1] == targetChar) {
          visited[n] = true
          queue.addLast(n)
        }
      }
    }
    return rows.map { String(it) }
  }

  fun globalReplace(
    frames: List<List<String>>,
    activeFrameIndex: Int,
    targetColor: Int,
    replacementColor: Int,
    allFrames: Boolean,
  ): List<List<String>> {
    val targetChar = colorNumberToChar(targetColor)
    val newChar = colorNumberToChar(replacementColor)
    if (targetChar == newChar) return frames

    fun replaceFrame(frame: List<String>): List<String> =
      frame.map { row ->
        if (targetChar !in row) row else row.replace(targetChar, newChar)
      }

    return frames.mapIndexed { idx, frame ->
      if (allFrames || idx == activeFrameIndex) replaceFrame(frame) else frame
    }
  }

  fun reorderPalette(
    palette: Map<Int, Int>,
    frames: List<List<String>>,
    visibleColors: Set<Int>,
    orderedOldKeys: List<Int>,
  ): PaletteRemapResult {
    val cleanOrder = orderedOldKeys.filter { it in palette }.distinct()
    val missing = palette.keys.sorted().filter { it !in cleanOrder }
    val finalOrder = cleanOrder + missing
    val oldToNew = mutableMapOf<Int, Int>()
    val newPalette = linkedMapOf<Int, Int>()
    finalOrder.forEachIndexed { idx, oldKey ->
      val newKey = idx + 1
      oldToNew[oldKey] = newKey
      newPalette[newKey] = palette[oldKey] ?: 0xFF000000.toInt()
    }
    val updatedFrames = remapFrames(frames, oldToNew)
    val updatedVisible = visibleColors.mapNotNull { oldToNew[it] }.toSet()
    return PaletteRemapResult(
      palette = newPalette,
      frames = updatedFrames,
      visibleColors = updatedVisible,
      oldToNewMap = oldToNew,
    )
  }

  fun swapPaletteColors(
    palette: Map<Int, Int>,
    frames: List<List<String>>,
    visibleColors: Set<Int>,
    colorA: Int,
    colorB: Int,
  ): PaletteRemapResult {
    val sorted = palette.keys.sorted().toMutableList()
    val idxA = sorted.indexOf(colorA)
    val idxB = sorted.indexOf(colorB)
    if (idxA < 0 || idxB < 0 || idxA == idxB) {
      val identity = palette.keys.associateWith { it }
      return PaletteRemapResult(palette, frames, visibleColors, identity)
    }
    val tmp = sorted[idxA]
    sorted[idxA] = sorted[idxB]
    sorted[idxB] = tmp
    return reorderPalette(palette, frames, visibleColors, sorted)
  }

  fun mergePaletteColor(
    palette: Map<Int, Int>,
    frames: List<List<String>>,
    visibleColors: Set<Int>,
    sourceColor: Int,
    targetColor: Int,
  ): PaletteRemapResult {
    if (sourceColor == targetColor || sourceColor !in palette || targetColor !in palette) {
      val identity = palette.keys.associateWith { it }
      return PaletteRemapResult(palette, frames, visibleColors, identity)
    }
    val remaining = palette.keys.sorted().filter { it != sourceColor }
    val oldToNew = mutableMapOf<Int, Int>()
    val newPalette = linkedMapOf<Int, Int>()
    remaining.forEachIndexed { idx, oldKey ->
      val newKey = idx + 1
      oldToNew[oldKey] = newKey
      newPalette[newKey] = palette[oldKey] ?: 0xFF000000.toInt()
    }
    val newTarget = oldToNew[targetColor] ?: 1
    oldToNew[sourceColor] = newTarget

    val updatedFrames = remapFrames(frames, oldToNew)
    val updatedVisible =
      buildSet {
        visibleColors.forEach { oldVis ->
          oldToNew[oldVis]?.let { add(it) }
        }
      }
    return PaletteRemapResult(
      palette = newPalette,
      frames = updatedFrames,
      visibleColors = updatedVisible,
      oldToNewMap = oldToNew,
    )
  }

  fun deletePaletteColor(
    palette: Map<Int, Int>,
    frames: List<List<String>>,
    visibleColors: Set<Int>,
    targetColor: Int,
  ): PaletteRemapResult {
    if (palette.size <= 1 || targetColor !in palette) {
      val identity = palette.keys.associateWith { it }
      return PaletteRemapResult(palette, frames, visibleColors, identity)
    }
    val remaining = palette.keys.sorted().filter { it != targetColor }
    val oldToNew = mutableMapOf<Int, Int>()
    val newPalette = linkedMapOf<Int, Int>()
    remaining.forEachIndexed { idx, oldKey ->
      val newKey = idx + 1
      oldToNew[oldKey] = newKey
      newPalette[newKey] = palette[oldKey] ?: 0xFF000000.toInt()
    }
    oldToNew[targetColor] = 0
    val updatedFrames = remapFrames(frames, oldToNew)
    val updatedVisible =
      visibleColors.filter { it != targetColor }.mapNotNull { oldToNew[it] }.toSet()
    return PaletteRemapResult(
      palette = newPalette,
      frames = updatedFrames,
      visibleColors = updatedVisible,
      oldToNewMap = oldToNew,
    )
  }

  fun addPaletteColor(
    palette: Map<Int, Int>,
    visibleColors: Set<Int>,
    argb: Int,
  ): Triple<Map<Int, Int>, Set<Int>, Int>? {
    if (palette.size >= MAX_PALETTE_COLORS) return null
    val nextNum = (palette.keys.maxOrNull() ?: 0) + 1
    if (nextNum > MAX_PALETTE_COLORS) return null
    val nextPalette = linkedMapOf<Int, Int>()
    palette.keys.sorted().forEach { k -> nextPalette[k] = palette.getValue(k) }
    nextPalette[nextNum] = argb or (0xFF shl 24)
    return Triple(nextPalette, visibleColors + nextNum, nextNum)
  }

  fun insertFrame(
    frames: List<List<String>>,
    insertIndex: Int,
    width: Int,
    height: Int,
    copyFrom: List<String>? = null,
  ): List<List<String>> {
    if (frames.size >= MAX_FRAMES) return frames
    val newFrame =
      if (copyFrom != null && copyFrom.size == height) {
        copyFrom.toList()
      } else {
        val blankRow = ".".repeat(width)
        List(height) { blankRow }
      }
    val mutable = frames.toMutableList()
    mutable.add(insertIndex.coerceIn(0, mutable.size), newFrame)
    return mutable
  }

  fun deleteFrame(frames: List<List<String>>, index: Int): List<List<String>> {
    if (frames.size <= 1 || index !in frames.indices) return frames
    return frames.toMutableList().apply { removeAt(index) }
  }

  fun countCellsPerColor(frame: List<String>, paletteKeys: Iterable<Int>): Map<Int, Int> {
    val counts = mutableMapOf<Int, Int>()
    paletteKeys.forEach { counts[it] = 0 }
    for (row in frame) {
      for (ch in row) {
        val num = charToColorNumber(ch)
        if (num > 0) {
          counts[num] = (counts[num] ?: 0) + 1
        }
      }
    }
    return counts
  }

  fun buildTemplate(
    baseTemplate: PictureTemplate,
    title: String,
    palette: Map<Int, Int>,
    frames: List<List<String>>,
    frameMs: Int,
  ): PictureTemplate {
    val cleanFrames =
      frames.ifEmpty {
        listOf(List(baseTemplate.gridHeight) { ".".repeat(baseTemplate.gridWidth) })
      }
    return baseTemplate.copy(
      title = title.trim().ifEmpty { "My Drawing" },
      palette = palette.toSortedMap(),
      grid = cleanFrames.first(),
      frames = if (cleanFrames.size > 1) cleanFrames else emptyList(),
      frameMs = frameMs.coerceIn(80, 1200),
      isCustom = true,
    )
  }

  private fun remapFrames(frames: List<List<String>>, oldToNew: Map<Int, Int>): List<List<String>> {
    val charLookup = CharArray(128) { '.' }
    for (oldNum in 1..MAX_PALETTE_COLORS) {
      val oldCh = colorNumberToChar(oldNum)
      val newNum = oldToNew[oldNum] ?: oldNum
      charLookup[oldCh.code] = colorNumberToChar(newNum)
      if (oldCh.isLowerCase()) {
        charLookup[oldCh.uppercaseChar().code] = colorNumberToChar(newNum)
      }
    }
    return frames.map { frame ->
      frame.map { row ->
        buildString(row.length) {
          for (ch in row) {
            if (ch == '.' || ch.code >= charLookup.size) {
              append('.')
            } else {
              append(charLookup[ch.code])
            }
          }
        }
      }
    }
  }

  fun rasterizeShapeCells(
    draft: ShapeDraft,
    gridWidth: Int,
    gridHeight: Int,
  ): List<Pair<Int, Int>> {
    if (gridWidth <= 0 || gridHeight <= 0) return emptyList()

    if (draft.shapeType == EditorShapeType.LINE) {
      val c0 = floor(draft.lineStartCol).toInt().coerceIn(0, gridWidth - 1)
      val r0 = floor(draft.lineStartRow).toInt().coerceIn(0, gridHeight - 1)
      val c1 = floor(draft.lineEndCol).toInt().coerceIn(0, gridWidth - 1)
      val r1 = floor(draft.lineEndRow).toInt().coerceIn(0, gridHeight - 1)
      return bresenhamLine(r0, c0, r1, c1)
    }

    val hw = draft.halfWidth.coerceAtLeast(0.5f)
    val hh = draft.halfHeight.coerceAtLeast(0.5f)

    // Exact axis-aligned rectangle fast path (handles 0, 90, 180, 270 cleanly)
    val mod90 = ((draft.rotationDeg % 90f) + 90f) % 90f
    val isAxisAligned = mod90 < 0.5f || mod90 > 89.5f
    if (draft.shapeType == EditorShapeType.RECTANGLE && isAxisAligned) {
      val quarterTurns = ((draft.rotationDeg / 90f).roundToInt() % 2 + 2) % 2
      val effW = if (quarterTurns == 1) hh else hw
      val effH = if (quarterTurns == 1) hw else hh
      val minC = floor(draft.centerCol - effW + 0.5f).toInt()
      val maxC = floor(draft.centerCol + effW - 0.5f).toInt().coerceAtLeast(minC)
      val minR = floor(draft.centerRow - effH + 0.5f).toInt()
      val maxR = floor(draft.centerRow + effH - 0.5f).toInt().coerceAtLeast(minR)

      val out = ArrayList<Pair<Int, Int>>()
      for (r in minR..maxR) {
        if (r !in 0 until gridHeight) continue
        for (c in minC..maxC) {
          if (c !in 0 until gridWidth) continue
          val onEdge = r == minR || r == maxR || c == minC || c == maxC
          if (draft.filled || onEdge) {
            out.add(r to c)
          }
        }
      }
      return out
    }

    val rad = draft.rotationDeg * (PI.toFloat() / 180f)
    val cosA = cos(rad)
    val sinA = sin(rad)

    fun isInsideCell(r: Int, c: Int): Boolean {
      val dx = (c + 0.5f) - draft.centerCol
      val dy = (r + 0.5f) - draft.centerRow
      val lx = dx * cosA + dy * sinA
      val ly = -dx * sinA + dy * cosA
      return when (draft.shapeType) {
        EditorShapeType.LINE -> false
        EditorShapeType.RECTANGLE -> abs(lx) <= hw && abs(ly) <= hh
        EditorShapeType.OVAL -> (lx / hw) * (lx / hw) + (ly / hh) * (ly / hh) <= 1.0f
        EditorShapeType.TRIANGLE -> {
          if (ly < -hh || ly > hh) return false
          val t = ((ly + hh) / (2f * hh)).coerceIn(0f, 1f)
          abs(lx) <= t * hw
        }
      }
    }

    val out = ArrayList<Pair<Int, Int>>()
    val maxExtent = ceil(max(hw, hh) * 1.6f).toInt() + 2
    val rStart = (floor(draft.centerRow).toInt() - maxExtent).coerceAtLeast(0)
    val rEnd = (floor(draft.centerRow).toInt() + maxExtent).coerceAtMost(gridHeight - 1)
    val cStart = (floor(draft.centerCol).toInt() - maxExtent).coerceAtLeast(0)
    val cEnd = (floor(draft.centerCol).toInt() + maxExtent).coerceAtMost(gridWidth - 1)

    for (r in rStart..rEnd) {
      for (c in cStart..cEnd) {
        if (!isInsideCell(r, c)) continue
        if (draft.filled) {
          out.add(r to c)
        } else {
          val onBoundary =
            !isInsideCell(r - 1, c) ||
              !isInsideCell(r + 1, c) ||
              !isInsideCell(r, c - 1) ||
              !isInsideCell(r, c + 1)
          if (onBoundary) out.add(r to c)
        }
      }
    }
    return out
  }

  fun applyShapeCells(
    frame: List<String>,
    cells: List<Pair<Int, Int>>,
    colorNumber: Int,
  ): List<String> {
    if (frame.isEmpty() || cells.isEmpty()) return frame
    val h = frame.size
    val w = frame[0].length
    val newChar = colorNumberToChar(colorNumber)
    val rows = Array(h) { r -> frame[r].toCharArray() }
    var changed = false
    for ((r, c) in cells) {
      if (r in 0 until h && c in 0 until w && rows[r][c] != newChar) {
        rows[r][c] = newChar
        changed = true
      }
    }
    return if (changed) rows.map { String(it) } else frame
  }

  private fun bresenhamLine(r0: Int, c0: Int, r1: Int, c1: Int): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var x0 = c0
    var y0 = r0
    val dx = abs(c1 - c0)
    val dy = abs(r1 - r0)
    val sx = if (c0 < c1) 1 else -1
    val sy = if (r0 < r1) 1 else -1
    var err = dx - dy
    while (true) {
      out.add(y0 to x0)
      if (x0 == c1 && y0 == r1) break
      val e2 = 2 * err
      if (e2 > -dy) {
        err -= dy
        x0 += sx
      }
      if (e2 < dx) {
        err += dx
        y0 += sy
      }
    }
    return out
  }
}
