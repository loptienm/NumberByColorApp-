package com.example.model

import androidx.compose.ui.graphics.Color

enum class ChallengeMode(val key: String, val label: String, val subtitle: String) {
  PRE_K("pre-k", "Pre-K", "Easy numbers, letters, counting & patterns"),
  KINDER("kinder", "Kindergarten / 1st", "Skip counting, adding, subtracting & Spanish basics"),
  OLDER("older", "Older Kids", "2-digit math, times tables, fractions & Spanish"),
  OFF("off", "Off", "All brushes unlocked, no questions"),
  ;

  companion object {
    fun fromKey(key: String): ChallengeMode = entries.firstOrNull { it.key == key } ?: PRE_K
  }
}

enum class BrushType(val id: String, val title: String, val cost: Int) {
  DOT("1", "Dot", 0),
  CROSS("5", "Cross", 2),
  BLOB("13", "Blob", 4),
  BUCKET("bucket", "Bucket", 6),
}

val KID_AVATARS = listOf("🐱", "🐶", "🐰", "🦄", "🐼", "🦊", "🐸", "🐯", "🐙", "🦋", "🐢", "🐥")

const val BUCKET_COLOR_LIMIT = 3
const val BUCKET_REFILL_COST = 4

data class KidProfile(
  val id: String,
  val name: String,
  val avatar: String,
  val challengeMode: ChallengeMode,
  val soundEnabled: Boolean,
  val customLevelId: String? = null,
)

data class PictureTemplate(
  val id: String,
  val title: String,
  val gridWidth: Int,
  val gridHeight: Int,
  val palette: Map<Int, Int>,
  val grid: List<String>,
  val frames: List<List<String>> = emptyList(),
  val frameMs: Int = 400,
  val still: List<Int> = emptyList(),
  val isCustom: Boolean = false,
  val version: Int = 1,
  val isSyncedOfficial: Boolean = false,
) {
  fun cellNumber(row: Int, col: Int): Int {
    val line = grid.getOrNull(row) ?: return 0
    val ch = line.getOrNull(col) ?: return 0
    return charToColorNumber(ch)
  }

  fun frameCellNumber(frameIdx: Int, row: Int, col: Int): Int {
    val activeGrid = if (frames.isNotEmpty()) frames[frameIdx % frames.size] else grid
    val line = activeGrid.getOrNull(row) ?: return 0
    val ch = line.getOrNull(col) ?: return 0
    return charToColorNumber(ch)
  }

  fun totalPaintableCells(): Int {
    var count = 0
    for (r in 0 until gridHeight) {
      for (c in 0 until gridWidth) {
        if (cellNumber(r, c) > 0) count++
      }
    }
    return count
  }
}

fun charToColorNumber(ch: Char): Int {
  if (ch == '.') return 0
  return when (ch) {
    in '1'..'9' -> ch - '0'
    in 'a'..'z' -> (ch - 'a') + 10
    in 'A'..'Z' -> (ch - 'A') + 10
    else -> 0
  }
}

fun colorNumberToChar(num: Int): Char {
  if (num <= 0) return '.'
  return if (num < 10) ('0' + num) else ('a' + (num - 10))
}

fun hexToArgb(hex: String): Int {
  val clean = hex.removePrefix("#")
  val rgb = clean.toLong(16).toInt()
  return (0xFF000000.toInt()) or rgb
}

fun argbToHex(argb: Int): String = String.format("#%06x", argb and 0xFFFFFF)

fun contrastInkForArgb(argb: Int): Color {
  val r = (argb shr 16) and 0xFF
  val g = (argb shr 8) and 0xFF
  val b = argb and 0xFF
  val luminance = (r * 299 + g * 587 + b * 114) / 1000
  return if (luminance >= 128) Color(0xFF0F172A) else Color.White
}

/**
 * Computes gray shades for uncolored cells in gallery previews so adjacent numbers remain visually
 * distinct rather than blending into one flat gray.
 */
fun computeGrayPalette(palette: Map<Int, Int>): Map<Int, Int> {
  if (palette.isEmpty()) return emptyMap()
  val lum = palette.mapValues { (_, argb) ->
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    0.299 * r + 0.587 * g + 0.114 * b
  }
  val order = lum.keys.sortedBy { lum[it] }
  val lo = lum[order.first()] ?: 0.0
  val hi = lum[order.last()] ?: 255.0
  val out = mutableMapOf<Int, Int>()
  order.forEachIndexed { idx, colorNum ->
    val byBrightness = if (hi > lo) ((lum[colorNum] ?: 0.0) - lo) / (hi - lo) else 0.5
    val byRank = if (order.size > 1) idx.toDouble() / (order.size - 1) else 0.5
    val g = (55 + 185 * (0.5 * byBrightness + 0.5 * byRank)).toInt().coerceIn(40, 245)
    out[colorNum] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
  }
  return out
}

fun serializePaintedGrid(grid: Array<IntArray>): String =
  grid.joinToString("/") { row ->
    buildString(row.size) { row.forEach { v -> append(colorNumberToChar(v)) } }
  }

fun deserializePaintedGrid(serialized: String?, width: Int, height: Int): Array<IntArray> {
  val empty = Array(height) { IntArray(width) }
  if (serialized.isNullOrEmpty()) return empty
  val rows = serialized.split('/')
  if (rows.size != height || rows.any { it.length != width }) return empty
  return Array(height) { r ->
    IntArray(width) { c -> charToColorNumber(rows[r][c]) }
  }
}

const val STICKER_MIN_SCALE = 0.55f
const val STICKER_MAX_SCALE = 2.20f

enum class StickerPagePaper(val index: Int, val label: String, val argb: Int) {
  WHITE(0, "Crisp White", 0xFFFFFFFF.toInt()),
  CREAM(1, "Warm Cream", 0xFFFFFBEB.toInt()),
  SKY(2, "Soft Sky", 0xFFF0F9FF.toInt()),
  MEADOW(3, "Mint Meadow", 0xFFECFDF5.toInt()),
  LAVENDER(4, "Lavender", 0xFFF5F3FF.toInt()),
  PEACH(5, "Peach", 0xFFFFF1F2.toInt()),
  ;

  companion object {
    fun fromIndex(index: Int): StickerPagePaper =
      entries.firstOrNull { it.index == index } ?: WHITE
  }
}

data class StickerPage(
  val id: String,
  val pageOrder: Int,
  val paper: StickerPagePaper = StickerPagePaper.WHITE,
)

data class PlacedSticker(
  val id: String,
  val pageId: String,
  val templateId: String,
  val xFraction: Float,
  val yFraction: Float,
  val scale: Float = 1.0f,
  val isMirrored: Boolean = false,
  val zOrder: Long = 0L,
)

fun clampStickerScale(scale: Float): Float = scale.coerceIn(STICKER_MIN_SCALE, STICKER_MAX_SCALE)

fun clampStickerFraction(fraction: Float, halfStickerFraction: Float): Float {
  val safeHalf = halfStickerFraction.coerceIn(0.02f, 0.45f)
  return fraction.coerceIn(safeHalf * 0.45f, 1f - safeHalf * 0.45f)
}

