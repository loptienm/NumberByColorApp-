package com.example.engine

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.PictureTemplate
import com.example.model.argbToHex
import com.example.model.charToColorNumber
import com.example.model.colorNumberToChar
import com.example.model.hexToArgb
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ExportFormat(
  val mimeType: String,
  val extension: String,
) {
  PNG_IMAGE("image/png", "png"),
  COLORPOP_PAGE("application/json", "colorpop.json"),
}

object PageExportEngine {

  private const val FORMAT_MARKER = "colorpop-page"
  private const val SCHEMA_VERSION = 1

  fun sanitizeFileBaseName(title: String): String {
    val cleaned =
      title
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.')
        .take(40)
    return cleaned.ifEmpty { "coloring_page" }
  }

  fun suggestedFileName(title: String, format: ExportFormat): String =
    "${sanitizeFileBaseName(title)}.${format.extension}"

  fun suggestedFileName(template: PictureTemplate, format: ExportFormat): String {
    val base =
      if (
        format == ExportFormat.COLORPOP_PAGE &&
          template.id.isNotBlank() &&
          !template.id.startsWith("custom_")
      ) {
        sanitizeFileBaseName(template.id)
      } else {
        sanitizeFileBaseName(template.title)
      }
    return "$base.${format.extension}"
  }

  fun sanitizePageId(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(40)

  fun defaultCellSizePx(gridWidth: Int, gridHeight: Int): Int {
    val maxSide = max(gridWidth, gridHeight).coerceAtLeast(1)
    return (768 / maxSide).coerceIn(10, 24)
  }

  fun renderTemplateToBitmap(
    template: PictureTemplate,
    frameIndex: Int = 0,
    cellSizePx: Int? = null,
    transparentBackground: Boolean = false,
    includeMargin: Boolean = true,
  ): Bitmap {
    val gridW = template.gridWidth.coerceAtLeast(1)
    val gridH = template.gridHeight.coerceAtLeast(1)
    val cellPx = (cellSizePx ?: defaultCellSizePx(gridW, gridH)).coerceIn(1, 64)
    val marginBlocks = if (includeMargin) 1 else 0
    val outW = (gridW + marginBlocks * 2) * cellPx
    val outH = (gridH + marginBlocks * 2) * cellPx

    val bgColor = if (transparentBackground) Color.TRANSPARENT else 0xFFFFFFFF.toInt()
    val pixels = IntArray(outW * outH) { bgColor }

    val sourceGrid =
      if (template.frames.isNotEmpty()) {
        template.frames[frameIndex.coerceIn(0, template.frames.lastIndex)]
      } else {
        template.grid
      }

    for (r in 0 until gridH) {
      val rowStr = sourceGrid.getOrNull(r) ?: continue
      val startY = (r + marginBlocks) * cellPx
      for (c in 0 until gridW) {
        val ch = rowStr.getOrNull(c) ?: '.'
        val colorNum = charToColorNumber(ch)
        if (colorNum <= 0) continue
        val argb = (template.palette[colorNum] ?: 0xFF000000.toInt()) or (0xFF shl 24)
        val startX = (c + marginBlocks) * cellPx
        for (dy in 0 until cellPx) {
          val rowOffset = (startY + dy) * outW + startX
          for (dx in 0 until cellPx) {
            pixels[rowOffset + dx] = argb
          }
        }
      }
    }

    return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
  }

  fun encodeBitmapToPngBytes(bitmap: Bitmap): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
  }

  fun encodePageToJson(template: PictureTemplate): String {
    val root = JSONObject()
    root.put("format", FORMAT_MARKER)
    root.put("schemaVersion", SCHEMA_VERSION)
    if (template.id.isNotBlank()) {
      root.put("id", template.id)
    }
    root.put("version", template.version.coerceAtLeast(1))
    root.put("title", template.title.ifBlank { "My Drawing" })
    root.put("gridWidth", template.gridWidth)
    root.put("gridHeight", template.gridHeight)
    root.put("frameMs", template.frameMs.coerceIn(80, 1200))

    val paletteObj = JSONObject()
    template.palette.keys.sorted().forEach { num ->
      val argb = template.palette[num] ?: return@forEach
      paletteObj.put(num.toString(), argbToHex(argb))
    }
    root.put("palette", paletteObj)

    val gridArr = JSONArray()
    template.grid.forEach { row -> gridArr.put(row) }
    root.put("grid", gridArr)

    if (template.frames.size > 1) {
      val framesArr = JSONArray()
      template.frames.forEach { frame ->
        val frameRows = JSONArray()
        frame.forEach { row -> frameRows.put(row) }
        framesArr.put(frameRows)
      }
      root.put("frames", framesArr)
    }

    return root.toString(2)
  }

  fun decodePageFromJson(
    rawJson: String,
    newId: String? = null,
    isSyncedOfficial: Boolean = false,
    preserveEmbeddedId: Boolean = false,
  ): PictureTemplate? {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return null
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      if (fmt.isNotEmpty() && fmt != FORMAT_MARKER) return null

      val gridW = root.optInt("gridWidth", 0)
      val gridH = root.optInt("gridHeight", 0)
      if (gridW !in 4..144 || gridH !in 4..144) return null

      val paletteJson = root.optJSONObject("palette") ?: return null
      val palette = linkedMapOf<Int, Int>()
      val keys = paletteJson.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val num = k.toIntOrNull() ?: continue
        if (num !in 1..BlockEditorEngine.MAX_PALETTE_COLORS) continue
        val hex = paletteJson.optString(k, "")
        if (hex.isBlank()) continue
        palette[num] = hexToArgb(hex)
      }
      if (palette.isEmpty()) return null

      fun parseGridArray(arr: JSONArray?): List<String>? {
        if (arr == null || arr.length() == 0) return null
        return List(gridH) { r ->
          val rawRow = arr.optString(r, "")
          buildString(gridW) {
            for (c in 0 until gridW) {
              val ch = rawRow.getOrNull(c) ?: '.'
              val num = charToColorNumber(ch)
              append(if (num in palette) colorNumberToChar(num) else '.')
            }
          }
        }
      }

      val grid = parseGridArray(root.optJSONArray("grid")) ?: return null
      val framesArr = root.optJSONArray("frames")
      val frames =
        if (framesArr != null && framesArr.length() > 1) {
          val decoded = mutableListOf<List<String>>()
          for (i in 0 until framesArr.length()) {
            val f = parseGridArray(framesArr.optJSONArray(i))
            if (f != null) decoded.add(f)
          }
          if (decoded.size > 1) decoded else emptyList()
        } else {
          emptyList()
        }

      val title = root.optString("title", "Imported Page").trim().ifEmpty { "Imported Page" }.take(24)
      val frameMs = root.optInt("frameMs", 400).coerceIn(80, 1200)
      val pageVersion = root.optInt("version", 1).coerceAtLeast(1)
      val embeddedId =
        root.optString("id", "").ifBlank { root.optString("pageId", "") }.trim()
      val resolvedId =
        when {
          !newId.isNullOrBlank() -> newId
          (preserveEmbeddedId || isSyncedOfficial) && embeddedId.isNotBlank() -> embeddedId
          else -> "custom_${System.currentTimeMillis()}"
        }

      PictureTemplate(
        id = resolvedId,
        title = title,
        gridWidth = gridW,
        gridHeight = gridH,
        palette = palette.toSortedMap(),
        grid = frames.firstOrNull() ?: grid,
        frames = frames,
        frameMs = frameMs,
        isCustom = !isSyncedOfficial,
        version = pageVersion,
        isSyncedOfficial = isSyncedOfficial,
      )
    } catch (_: Throwable) {
      null
    }
  }

  fun buildExportBytes(
    template: PictureTemplate,
    format: ExportFormat,
    frameIndex: Int = 0,
    transparentPng: Boolean = false,
  ): ByteArray =
    when (format) {
      ExportFormat.PNG_IMAGE -> {
        val bmp =
          renderTemplateToBitmap(
            template = template,
            frameIndex = frameIndex,
            transparentBackground = transparentPng,
            includeMargin = true,
          )
        try {
          encodeBitmapToPngBytes(bmp)
        } finally {
          bmp.recycle()
        }
      }
      ExportFormat.COLORPOP_PAGE -> encodePageToJson(template).toByteArray(Charsets.UTF_8)
    }

  suspend fun writeExportFileToCache(
    context: Context,
    template: PictureTemplate,
    format: ExportFormat,
    frameIndex: Int = 0,
    transparentPng: Boolean = false,
  ): Uri =
    withContext(Dispatchers.IO) {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val cutoffMs = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
      exportDir.listFiles()?.forEach { f ->
        if (f.lastModified() < cutoffMs) f.delete()
      }

      val fileName = suggestedFileName(template, format)
      val outFile = File(exportDir, fileName)
      val bytes = buildExportBytes(template, format, frameIndex, transparentPng)
      outFile.writeBytes(bytes)
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

  suspend fun writeExportToUri(
    context: Context,
    destinationUri: Uri,
    template: PictureTemplate,
    format: ExportFormat,
    frameIndex: Int = 0,
    transparentPng: Boolean = false,
  ): Boolean =
    withContext(Dispatchers.IO) {
      try {
        val bytes = buildExportBytes(template, format, frameIndex, transparentPng)
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
          out.write(bytes)
          out.flush()
          true
        } ?: false
      } catch (_: Throwable) {
        false
      }
    }

  fun launchShareIntent(
    context: Context,
    uri: Uri,
    format: ExportFormat,
    title: String,
  ): Boolean {
    return try {
      val cleanTitle = title.ifBlank { "Coloring Page" }
      val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = format.mimeType
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, cleanTitle)
          putExtra(Intent.EXTRA_TEXT, "Number by Color page: $cleanTitle")
          clipData = ClipData.newRawUri(cleanTitle, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      val chooser = Intent.createChooser(sendIntent, "Share $cleanTitle")
      if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    } catch (_: Throwable) {
      false
    }
  }
}
