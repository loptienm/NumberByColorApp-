package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.model.PictureTemplate
import com.example.model.computeGrayPalette
import kotlin.math.max

enum class PreviewRenderMode {
  FULL_COLOR,
  GRAY_WITH_PROGRESS,
  SHADOW_SILHOUETTE,
}

@Composable
fun PixelPreviewCanvas(
  template: PictureTemplate,
  mode: PreviewRenderMode,
  paintedGrid: Array<IntArray>? = null,
  frameIndex: Int? = null,
  modifier: Modifier = Modifier,
) {
  val grayPalette = remember(template.id, template.palette) { computeGrayPalette(template.palette) }

  Canvas(modifier = modifier) {
    val maxDim = max(template.gridWidth, template.gridHeight).coerceAtLeast(1)
    val cellSize = minOf(size.width, size.height) / maxDim
    val offsetX = (size.width - template.gridWidth * cellSize) / 2f
    val offsetY = (size.height - template.gridHeight * cellSize) / 2f

    for (r in 0 until template.gridHeight) {
      for (c in 0 until template.gridWidth) {
        val target =
          if (frameIndex != null) template.frameCellNumber(frameIndex, r, c)
          else template.cellNumber(r, c)
        if (target == 0) continue

        val argb =
          when (mode) {
            PreviewRenderMode.FULL_COLOR -> template.palette[target]
            PreviewRenderMode.SHADOW_SILHOUETTE -> 0xFFE5E0CF.toInt()
            PreviewRenderMode.GRAY_WITH_PROGRESS -> {
              val isPainted = paintedGrid?.getOrNull(r)?.getOrNull(c) == target
              if (isPainted) template.palette[target] else grayPalette[target]
            }
          } ?: continue

        drawRect(
          color = Color(argb),
          topLeft = Offset(offsetX + c * cellSize, offsetY + r * cellSize),
          size = Size(cellSize + 0.6f, cellSize + 0.6f),
        )
      }
    }
  }
}
