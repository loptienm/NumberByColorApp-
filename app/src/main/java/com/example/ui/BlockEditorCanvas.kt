package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.engine.BlockEditorEngine
import com.example.engine.EditorShapeType
import com.example.engine.EditorTool
import com.example.engine.ShapeDraft
import com.example.model.contrastInkForArgb
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ShapeHandleMode {
  NONE,
  CREATE_DRAG,
  MOVE,
  RESIZE_WIDTH,
  RESIZE_HEIGHT,
  RESIZE_CORNER,
  ROTATE,
  LINE_START,
  LINE_END,
}

@Composable
fun BlockEditorCanvas(
  gridWidth: Int,
  gridHeight: Int,
  frame: List<String>,
  palette: Map<Int, Int>,
  visibleColors: Set<Int>,
  activeTool: EditorTool,
  activeColorNumber: Int?,
  fillWithEraser: Boolean,
  shapeType: EditorShapeType,
  shapeFilled: Boolean,
  activeShape: ShapeDraft?,
  showNumbers: Boolean,
  showGridLines: Boolean,
  onToggleNumbers: () -> Unit,
  onToggleGridLines: () -> Unit,
  onTogglePanTool: () -> Unit,
  onStrokeBegin: (row: Int, col: Int) -> Unit,
  onStrokeStep: (fromRow: Int?, fromCol: Int?, toRow: Int, toCol: Int) -> Unit,
  onTapCellAction: (row: Int, col: Int) -> Unit,
  onUpdateShapeDraft: (ShapeDraft?) -> Unit,
  onCommitShapeDraft: () -> Unit,
  onDiscardShapeDraft: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var zoomScale by remember(gridWidth, gridHeight) { mutableFloatStateOf(1f) }
  var panX by remember(gridWidth, gridHeight) { mutableFloatStateOf(0f) }
  var panY by remember(gridWidth, gridHeight) { mutableFloatStateOf(0f) }

  val currentTool by rememberUpdatedState(activeTool)
  val currentGridW by rememberUpdatedState(gridWidth)
  val currentGridH by rememberUpdatedState(gridHeight)
  val currentShapeType by rememberUpdatedState(shapeType)
  val currentShapeFilled by rememberUpdatedState(shapeFilled)
  val currentActiveShape by rememberUpdatedState(activeShape)
  val currentOnStrokeBegin by rememberUpdatedState(onStrokeBegin)
  val currentOnStrokeStep by rememberUpdatedState(onStrokeStep)
  val currentOnTapCellAction by rememberUpdatedState(onTapCellAction)
  val currentOnUpdateShapeDraft by rememberUpdatedState(onUpdateShapeDraft)

  val previewCellsList =
    remember(activeShape, gridWidth, gridHeight) {
      if (activeShape != null) {
        BlockEditorEngine.rasterizeShapeCells(activeShape, gridWidth, gridHeight)
      } else {
        emptyList()
      }
    }
  val previewCellsSet = remember(previewCellsList) { previewCellsList.toHashSet() }
  val previewColorNum = activeColorNumber ?: palette.keys.minOrNull() ?: 1

  val numberPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(22.dp),
    color = Color.White,
    border = BorderStroke(2.dp, BorderSoft),
    shadowElevation = 4.dp,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .clip(RoundedCornerShape(22.dp))
          .background(Color(0xFFF8FAFC))
          .clipToBounds()
    ) {
      Canvas(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(gridWidth, gridHeight) {
              awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                firstDown.consume()
                val cw = size.width.toFloat()
                val ch = size.height.toFloat()

                val isStrokeTool =
                  currentTool == EditorTool.DRAW || currentTool == EditorTool.ERASE
                var strokeStarted = false
                var multiTouchUsed = false
                var dragMovedBeyondTap = false
                var lastCell: Pair<Int, Int>? = null
                var tapMovedPx = 0f
                var prevTapPos = firstDown.position

                var shapeHandleMode = ShapeHandleMode.NONE
                var shapeStartDraft: ShapeDraft? = null
                var createAnchorGrid = Offset.Zero

                val startMetrics =
                  computeViewportMetrics(
                    canvasW = cw,
                    canvasH = ch,
                    brushRailPx = 0f,
                    gridW = currentGridW,
                    gridH = currentGridH,
                    zoom = zoomScale,
                    panX = panX,
                    panY = panY,
                  )

                if (currentTool == EditorTool.SHAPE) {
                  val cellPx = startMetrics.cellSize.coerceAtLeast(1f)
                  val existing = currentActiveShape
                  if (existing == null) {
                    val gx = ((firstDown.position.x - startMetrics.originX) / cellPx)
                    val gy = ((firstDown.position.y - startMetrics.originY) / cellPx)
                    if (gx in -1f..(currentGridW + 1f) && gy in -1f..(currentGridH + 1f)) {
                      shapeHandleMode = ShapeHandleMode.CREATE_DRAG
                      createAnchorGrid = Offset(gx.coerceIn(0f, currentGridW.toFloat()), gy.coerceIn(0f, currentGridH.toFloat()))
                    }
                  } else {
                    shapeStartDraft = existing
                    val hitRadius = 32.dp.toPx()
                    if (existing.shapeType == EditorShapeType.LINE) {
                      val pA =
                        Offset(
                          startMetrics.originX + existing.lineStartCol * cellPx,
                          startMetrics.originY + existing.lineStartRow * cellPx,
                        )
                      val pB =
                        Offset(
                          startMetrics.originX + existing.lineEndCol * cellPx,
                          startMetrics.originY + existing.lineEndRow * cellPx,
                        )
                      shapeHandleMode =
                        when {
                          (firstDown.position - pA).getDistance() <= hitRadius -> ShapeHandleMode.LINE_START
                          (firstDown.position - pB).getDistance() <= hitRadius -> ShapeHandleMode.LINE_END
                          distanceToSegment(firstDown.position, pA, pB) <= hitRadius -> ShapeHandleMode.MOVE
                          else -> ShapeHandleMode.NONE
                        }
                    } else {
                      val cx = startMetrics.originX + existing.centerCol * cellPx
                      val cy = startMetrics.originY + existing.centerRow * cellPx
                      val hwPx = existing.halfWidth * cellPx
                      val hhPx = existing.halfHeight * cellPx
                      val rad = existing.rotationDeg * (PI.toFloat() / 180f)
                      val cosA = cos(rad)
                      val sinA = sin(rad)
                      fun localToScreen(lx: Float, ly: Float) =
                        Offset(cx + lx * cosA - ly * sinA, cy + lx * sinA + ly * cosA)

                      val rotKnobPx = localToScreen(0f, -hhPx - 32.dp.toPx())
                      val cornerPx = localToScreen(hwPx, hhPx)
                      val widthPx = localToScreen(hwPx, 0f)
                      val heightPx = localToScreen(0f, hhPx)

                      val dOff = firstDown.position - Offset(cx, cy)
                      val lxDown = dOff.x * cosA + dOff.y * sinA
                      val lyDown = -dOff.x * sinA + dOff.y * cosA
                      val insideBox =
                        abs(lxDown) <= hwPx + 18.dp.toPx() && abs(lyDown) <= hhPx + 18.dp.toPx()

                      shapeHandleMode =
                        when {
                          (firstDown.position - rotKnobPx).getDistance() <= hitRadius -> ShapeHandleMode.ROTATE
                          (firstDown.position - cornerPx).getDistance() <= hitRadius -> ShapeHandleMode.RESIZE_CORNER
                          (firstDown.position - widthPx).getDistance() <= hitRadius -> ShapeHandleMode.RESIZE_WIDTH
                          (firstDown.position - heightPx).getDistance() <= hitRadius -> ShapeHandleMode.RESIZE_HEIGHT
                          insideBox -> ShapeHandleMode.MOVE
                          else -> ShapeHandleMode.NONE
                        }
                    }
                  }
                } else if (currentTool != EditorTool.PAN) {
                  val startCell =
                    screenToGridCell(firstDown.position, startMetrics, currentGridW, currentGridH)
                  if (isStrokeTool && startCell != null) {
                    currentOnStrokeBegin(startCell.first, startCell.second)
                    strokeStarted = true
                    lastCell = startCell
                  }
                }

                while (true) {
                  val event = awaitPointerEvent()
                  val pressed = event.changes.filter { it.pressed }
                  if (pressed.isEmpty()) break

                  if (pressed.size >= 2 || currentTool == EditorTool.PAN) {
                    if (pressed.size >= 2) multiTouchUsed = true
                    val zoomFactor = if (pressed.size >= 2) event.calculateZoom() else 1f
                    val panDelta = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true)

                    val oldMetrics =
                      computeViewportMetrics(
                        canvasW = cw,
                        canvasH = ch,
                        brushRailPx = 0f,
                        gridW = currentGridW,
                        gridH = currentGridH,
                        zoom = zoomScale,
                        panX = panX,
                        panY = panY,
                      )
                    val newZoom = (zoomScale * zoomFactor).coerceIn(1f, 8f)
                    val ratio = if (zoomScale > 0.001f) newZoom / zoomScale else 1f
                    val newMetricsBase =
                      computeViewportMetrics(
                        canvasW = cw,
                        canvasH = ch,
                        brushRailPx = 0f,
                        gridW = currentGridW,
                        gridH = currentGridH,
                        zoom = newZoom,
                        panX = 0f,
                        panY = 0f,
                      )
                    val desiredOriginX =
                      centroid.x - (centroid.x - oldMetrics.originX) * ratio + panDelta.x
                    val desiredOriginY =
                      centroid.y - (centroid.y - oldMetrics.originY) * ratio + panDelta.y
                    val rawPanX = desiredOriginX - newMetricsBase.baseOriginX
                    val rawPanY = desiredOriginY - newMetricsBase.baseOriginY
                    val (clampedPanX, clampedPanY) =
                      clampPanOffset(
                        rawPanX = rawPanX,
                        rawPanY = rawPanY,
                        metrics = newMetricsBase,
                        canvasW = cw,
                        canvasH = ch,
                        brushRailPx = 0f,
                        zoom = newZoom,
                      )
                    zoomScale = newZoom
                    panX = clampedPanX
                    panY = clampedPanY
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                  } else if (!multiTouchUsed) {
                    val chg = pressed.first()
                    val delta = chg.position - prevTapPos
                    tapMovedPx += abs(delta.x) + abs(delta.y)
                    if (tapMovedPx > 14f) dragMovedBeyondTap = true
                    prevTapPos = chg.position

                    if (isStrokeTool) {
                      val metrics =
                        computeViewportMetrics(
                          canvasW = cw,
                          canvasH = ch,
                          brushRailPx = 0f,
                          gridW = currentGridW,
                          gridH = currentGridH,
                          zoom = zoomScale,
                          panX = panX,
                          panY = panY,
                        )
                      val cell = screenToGridCell(chg.position, metrics, currentGridW, currentGridH)
                      if (cell != null) {
                        if (!strokeStarted) {
                          currentOnStrokeBegin(cell.first, cell.second)
                          strokeStarted = true
                          lastCell = cell
                        } else if (cell != lastCell) {
                          currentOnStrokeStep(lastCell?.first, lastCell?.second, cell.first, cell.second)
                          lastCell = cell
                        }
                      }
                    } else if (currentTool == EditorTool.SHAPE && shapeHandleMode != ShapeHandleMode.NONE) {
                      val metrics =
                        computeViewportMetrics(
                          canvasW = cw,
                          canvasH = ch,
                          brushRailPx = 0f,
                          gridW = currentGridW,
                          gridH = currentGridH,
                          zoom = zoomScale,
                          panX = panX,
                          panY = panY,
                        )
                      val cellPx = metrics.cellSize.coerceAtLeast(1f)
                      val curGX = (chg.position.x - metrics.originX) / cellPx
                      val curGY = (chg.position.y - metrics.originY) / cellPx

                      when (shapeHandleMode) {
                        ShapeHandleMode.CREATE_DRAG -> {
                          if (dragMovedBeyondTap) {
                            val eX = curGX.coerceIn(0f, currentGridW.toFloat())
                            val eY = curGY.coerceIn(0f, currentGridH.toFloat())
                            if (currentShapeType == EditorShapeType.LINE) {
                              val sx = floor(createAnchorGrid.x).coerceIn(0f, currentGridW - 1f) + 0.5f
                              val sy = floor(createAnchorGrid.y).coerceIn(0f, currentGridH - 1f) + 0.5f
                              val ex = floor(eX).coerceIn(0f, currentGridW - 1f) + 0.5f
                              val ey = floor(eY).coerceIn(0f, currentGridH - 1f) + 0.5f
                              currentOnUpdateShapeDraft(
                                ShapeDraft(
                                  shapeType = EditorShapeType.LINE,
                                  centerCol = (sx + ex) / 2f,
                                  centerRow = (sy + ey) / 2f,
                                  halfWidth = (abs(ex - sx) / 2f).coerceAtLeast(0.5f),
                                  halfHeight = (abs(ey - sy) / 2f).coerceAtLeast(0.5f),
                                  lineStartCol = sx,
                                  lineStartRow = sy,
                                  lineEndCol = ex,
                                  lineEndRow = ey,
                                  filled = currentShapeFilled,
                                )
                              )
                            } else {
                              val minC = floor(min(createAnchorGrid.x, eX)).coerceIn(0f, currentGridW - 1f)
                              val maxC = (floor(max(createAnchorGrid.x, eX)) + 1f).coerceIn(minC + 1f, currentGridW.toFloat())
                              val minR = floor(min(createAnchorGrid.y, eY)).coerceIn(0f, currentGridH - 1f)
                              val maxR = (floor(max(createAnchorGrid.y, eY)) + 1f).coerceIn(minR + 1f, currentGridH.toFloat())
                              currentOnUpdateShapeDraft(
                                ShapeDraft(
                                  shapeType = currentShapeType,
                                  centerCol = (minC + maxC) / 2f,
                                  centerRow = (minR + maxR) / 2f,
                                  halfWidth = ((maxC - minC) / 2f).coerceAtLeast(0.5f),
                                  halfHeight = ((maxR - minR) / 2f).coerceAtLeast(0.5f),
                                  rotationDeg = 0f,
                                  filled = currentShapeFilled,
                                )
                              )
                            }
                          }
                        }

                        ShapeHandleMode.MOVE -> {
                          val base = shapeStartDraft
                          if (base != null) {
                            val dCol = ((chg.position.x - firstDown.position.x) / cellPx * 2f).roundToInt() / 2f
                            val dRow = ((chg.position.y - firstDown.position.y) / cellPx * 2f).roundToInt() / 2f
                            if (base.shapeType == EditorShapeType.LINE) {
                              currentOnUpdateShapeDraft(
                                base.copy(
                                  centerCol = (base.centerCol + dCol).coerceIn(0f, currentGridW.toFloat()),
                                  centerRow = (base.centerRow + dRow).coerceIn(0f, currentGridH.toFloat()),
                                  lineStartCol = base.lineStartCol + dCol,
                                  lineStartRow = base.lineStartRow + dRow,
                                  lineEndCol = base.lineEndCol + dCol,
                                  lineEndRow = base.lineEndRow + dRow,
                                )
                              )
                            } else {
                              currentOnUpdateShapeDraft(
                                base.copy(
                                  centerCol = (base.centerCol + dCol).coerceIn(0f, currentGridW.toFloat()),
                                  centerRow = (base.centerRow + dRow).coerceIn(0f, currentGridH.toFloat()),
                                )
                              )
                            }
                          }
                        }

                        ShapeHandleMode.LINE_START -> {
                          val base = currentActiveShape ?: shapeStartDraft
                          if (base != null) {
                            val nx = floor(curGX).coerceIn(0f, currentGridW - 1f) + 0.5f
                            val ny = floor(curGY).coerceIn(0f, currentGridH - 1f) + 0.5f
                            currentOnUpdateShapeDraft(
                              base.copy(
                                lineStartCol = nx,
                                lineStartRow = ny,
                                centerCol = (nx + base.lineEndCol) / 2f,
                                centerRow = (ny + base.lineEndRow) / 2f,
                              )
                            )
                          }
                        }

                        ShapeHandleMode.LINE_END -> {
                          val base = currentActiveShape ?: shapeStartDraft
                          if (base != null) {
                            val nx = floor(curGX).coerceIn(0f, currentGridW - 1f) + 0.5f
                            val ny = floor(curGY).coerceIn(0f, currentGridH - 1f) + 0.5f
                            currentOnUpdateShapeDraft(
                              base.copy(
                                lineEndCol = nx,
                                lineEndRow = ny,
                                centerCol = (base.lineStartCol + nx) / 2f,
                                centerRow = (base.lineStartRow + ny) / 2f,
                              )
                            )
                          }
                        }

                        ShapeHandleMode.RESIZE_WIDTH,
                        ShapeHandleMode.RESIZE_HEIGHT,
                        ShapeHandleMode.RESIZE_CORNER -> {
                          val base = shapeStartDraft
                          if (base != null) {
                            val rad = base.rotationDeg * (PI.toFloat() / 180f)
                            val cosA = cos(rad)
                            val sinA = sin(rad)
                            val dx = curGX - base.centerCol
                            val dy = curGY - base.centerRow
                            val lx = dx * cosA + dy * sinA
                            val ly = -dx * sinA + dy * cosA
                            val maxRadius = max(currentGridW, currentGridH).toFloat()
                            val snappedHW = (abs(lx) * 2f).roundToInt().coerceAtLeast(1) / 2f
                            val snappedHH = (abs(ly) * 2f).roundToInt().coerceAtLeast(1) / 2f
                            val nextHW =
                              if (shapeHandleMode == ShapeHandleMode.RESIZE_HEIGHT) base.halfWidth
                              else snappedHW.coerceIn(0.5f, maxRadius)
                            val nextHH =
                              if (shapeHandleMode == ShapeHandleMode.RESIZE_WIDTH) base.halfHeight
                              else snappedHH.coerceIn(0.5f, maxRadius)
                            currentOnUpdateShapeDraft(base.copy(halfWidth = nextHW, halfHeight = nextHH))
                          }
                        }

                        ShapeHandleMode.ROTATE -> {
                          val base = shapeStartDraft
                          if (base != null) {
                            val dx = curGX - base.centerCol
                            val dy = curGY - base.centerRow
                            if (hypot(dx, dy) > 0.25f) {
                              val rawDeg = atan2(dx, -dy) * (180f / PI.toFloat())
                              val snapped45 = (rawDeg / 45f).roundToInt() * 45f
                              val finalDeg =
                                if (abs(rawDeg - snapped45) <= 4.5f) snapped45 else rawDeg.roundToInt().toFloat()
                              val normDeg = ((finalDeg % 360f) + 360f) % 360f
                              currentOnUpdateShapeDraft(base.copy(rotationDeg = if (normDeg > 180f) normDeg - 360f else normDeg))
                            }
                          }
                        }

                        ShapeHandleMode.NONE -> Unit
                      }
                    }
                    chg.consume()
                  }
                }

                // Single tap actions
                if (!multiTouchUsed && currentTool != EditorTool.PAN && !dragMovedBeyondTap) {
                  if (currentTool == EditorTool.SHAPE && shapeHandleMode == ShapeHandleMode.CREATE_DRAG) {
                    val rDefault =
                      (min(currentGridW, currentGridH) * 0.16f).roundToInt().coerceIn(2, 6).toFloat()
                    val cx = (floor(createAnchorGrid.x) + 0.5f).coerceIn(1f, currentGridW - 1f)
                    val cy = (floor(createAnchorGrid.y) + 0.5f).coerceIn(1f, currentGridH - 1f)
                    if (currentShapeType == EditorShapeType.LINE) {
                      val sx = (cx - rDefault).coerceIn(0.5f, currentGridW - 0.5f)
                      val ex = (cx + rDefault).coerceIn(0.5f, currentGridW - 0.5f)
                      currentOnUpdateShapeDraft(
                        ShapeDraft(
                          shapeType = EditorShapeType.LINE,
                          centerCol = cx,
                          centerRow = cy,
                          halfWidth = rDefault,
                          halfHeight = 0.5f,
                          lineStartCol = sx,
                          lineStartRow = cy,
                          lineEndCol = ex,
                          lineEndRow = cy,
                          filled = currentShapeFilled,
                        )
                      )
                    } else {
                      currentOnUpdateShapeDraft(
                        ShapeDraft(
                          shapeType = currentShapeType,
                          centerCol = cx,
                          centerRow = cy,
                          halfWidth = rDefault,
                          halfHeight = rDefault,
                          rotationDeg = 0f,
                          filled = currentShapeFilled,
                        )
                      )
                    }
                  } else if (!isStrokeTool && currentTool != EditorTool.SHAPE) {
                    val endMetrics =
                      computeViewportMetrics(
                        canvasW = cw,
                        canvasH = ch,
                        brushRailPx = 0f,
                        gridW = currentGridW,
                        gridH = currentGridH,
                        zoom = zoomScale,
                        panX = panX,
                        panY = panY,
                      )
                    val cell =
                      screenToGridCell(prevTapPos, endMetrics, currentGridW, currentGridH)
                    if (cell != null) {
                      currentOnTapCellAction(cell.first, cell.second)
                    }
                  }
                }
              }
            }
            .testTag("block_editor_canvas")
      ) {
        val m =
          computeViewportMetrics(
            canvasW = size.width,
            canvasH = size.height,
            brushRailPx = 0f,
            gridW = gridWidth,
            gridH = gridHeight,
            zoom = zoomScale,
            panX = panX,
            panY = panY,
          )
        val cellPx = m.cellSize
        if (cellPx <= 0.5f) return@Canvas

        // Outer canvas frame around grid
        drawRect(
          color = Color(0xFFE2E8F0),
          topLeft = Offset(m.originX - 2f, m.originY - 2f),
          size = Size(m.gridPixelW + 4f, m.gridPixelH + 4f),
          style = Stroke(width = 2.5f),
        )

        val numberScale = (cellPx * 0.48f).coerceIn(7f, 28f)
        numberPaint.textSize = numberScale

        for (r in 0 until gridHeight) {
          val top = m.originY + r * cellPx
          if (top + cellPx < 0f || top > size.height) continue
          for (c in 0 until gridWidth) {
            val left = m.originX + c * cellPx
            if (left + cellPx < 0f || left > size.width) continue

            val isPreviewCell = (r to c) in previewCellsSet
            if (isPreviewCell) {
              if (fillWithEraser) {
                val isLightChecker = ((r + c) and 1) == 0
                drawRect(
                  color = if (isLightChecker) Color(0xFFFFFFFF) else Color(0xFFE9EFF6),
                  topLeft = Offset(left, top),
                  size = Size(cellPx + 0.5f, cellPx + 0.5f),
                )
                drawRect(
                  color = CoralError.copy(alpha = 0.7f),
                  topLeft = Offset(left + 1f, top + 1f),
                  size = Size((cellPx - 2f).coerceAtLeast(1f), (cellPx - 2f).coerceAtLeast(1f)),
                  style = Stroke(width = 1.6f),
                )
              } else {
                val previewArgb = palette[previewColorNum] ?: 0xFF000000.toInt()
                drawRect(
                  color = Color(previewArgb),
                  topLeft = Offset(left, top),
                  size = Size(cellPx + 0.5f, cellPx + 0.5f),
                )
                drawRect(
                  color = IndigoPrimary.copy(alpha = 0.55f),
                  topLeft = Offset(left + 0.8f, top + 0.8f),
                  size = Size((cellPx - 1.6f).coerceAtLeast(1f), (cellPx - 1.6f).coerceAtLeast(1f)),
                  style = Stroke(width = 1.4f),
                )
              }
            } else {
              val colorNum = BlockEditorEngine.cellAt(frame, r, c)
              if (colorNum <= 0) {
                // Transparent/erased square: subtle light checkerboard
                val isLightChecker = ((r + c) and 1) == 0
                drawRect(
                  color = if (isLightChecker) Color(0xFFFFFFFF) else Color(0xFFE9EFF6),
                  topLeft = Offset(left, top),
                  size = Size(cellPx + 0.5f, cellPx + 0.5f),
                )
              } else if (colorNum in visibleColors) {
                val argb = palette[colorNum] ?: 0xFF000000.toInt()
                drawRect(
                  color = Color(argb),
                  topLeft = Offset(left, top),
                  size = Size(cellPx + 0.5f, cellPx + 0.5f),
                )
                if (showNumbers && cellPx >= 13f) {
                  numberPaint.color = contrastInkForArgb(argb).toArgb()
                  val textY = top + cellPx * 0.5f - (numberPaint.ascent() + numberPaint.descent()) * 0.5f
                  drawContext.canvas.nativeCanvas.drawText(
                    colorNum.toString(),
                    left + cellPx * 0.5f,
                    textY,
                    numberPaint,
                  )
                }
              } else {
                // Color exists on grid, currently deactivated ("uncolored" layer preview)
                drawRect(
                  color = Color(0xFFF1F5F9),
                  topLeft = Offset(left, top),
                  size = Size(cellPx + 0.5f, cellPx + 0.5f),
                )
                drawRect(
                  color = Color(0xFFCBD5E1),
                  topLeft = Offset(left + 0.6f, top + 0.6f),
                  size = Size((cellPx - 1.2f).coerceAtLeast(1f), (cellPx - 1.2f).coerceAtLeast(1f)),
                  style = Stroke(width = 1f),
                )
                if (showNumbers && cellPx >= 13f) {
                  numberPaint.color = SlateInk.copy(alpha = 0.62f).toArgb()
                  val textY = top + cellPx * 0.5f - (numberPaint.ascent() + numberPaint.descent()) * 0.5f
                  drawContext.canvas.nativeCanvas.drawText(
                    colorNum.toString(),
                    left + cellPx * 0.5f,
                    textY,
                    numberPaint,
                  )
                }
              }
            }

            if (showGridLines && cellPx >= 6f) {
              drawRect(
                color = Color(0x2A334155),
                topLeft = Offset(left, top),
                size = Size(cellPx, cellPx),
                style = Stroke(width = 0.8f),
              )
            }
          }
        }

        // Interactive handles for Shape Draft overlay
        val draft = activeShape
        if (draft != null && activeTool == EditorTool.SHAPE) {
          val handleStroke = 2.5.dp.toPx()
          val knobRadius = 11.dp.toPx()
          if (draft.shapeType == EditorShapeType.LINE) {
            val pA = Offset(m.originX + draft.lineStartCol * cellPx, m.originY + draft.lineStartRow * cellPx)
            val pB = Offset(m.originX + draft.lineEndCol * cellPx, m.originY + draft.lineEndRow * cellPx)
            drawLine(
              color = IndigoPrimary.copy(alpha = 0.75f),
              start = pA,
              end = pB,
              strokeWidth = 2.dp.toPx(),
            )
            listOf(pA, pB).forEach { pt ->
              drawCircle(color = Color.White, radius = knobRadius, center = pt)
              drawCircle(color = IndigoPrimary, radius = knobRadius, center = pt, style = Stroke(handleStroke))
            }
          } else {
            val cx = m.originX + draft.centerCol * cellPx
            val cy = m.originY + draft.centerRow * cellPx
            val hwPx = draft.halfWidth * cellPx
            val hhPx = draft.halfHeight * cellPx
            val rad = draft.rotationDeg * (PI.toFloat() / 180f)
            val cosA = cos(rad)
            val sinA = sin(rad)
            fun toScreen(lx: Float, ly: Float) =
              Offset(cx + lx * cosA - ly * sinA, cy + lx * sinA + ly * cosA)

            val tl = toScreen(-hwPx, -hhPx)
            val tr = toScreen(hwPx, -hhPx)
            val br = toScreen(hwPx, hhPx)
            val bl = toScreen(-hwPx, hhPx)
            val topMid = toScreen(0f, -hhPx)
            val rotKnob = toScreen(0f, -hhPx - 32.dp.toPx())
            val rightMid = toScreen(hwPx, 0f)
            val bottomMid = toScreen(0f, hhPx)

            val boxPath =
              Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
              }
            drawPath(path = boxPath, color = IndigoPrimary.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx()))

            // Stem + rotate knob
            drawLine(color = IndigoPrimary.copy(alpha = 0.8f), start = topMid, end = rotKnob, strokeWidth = 2.dp.toPx())
            drawCircle(color = Color.White, radius = knobRadius, center = rotKnob)
            drawCircle(color = IndigoPrimary, radius = knobRadius, center = rotKnob, style = Stroke(handleStroke))
            drawCircle(color = IndigoPrimary, radius = 3.5.dp.toPx(), center = rotKnob)

            // Squish width handle (right edge)
            rotate(degrees = draft.rotationDeg, pivot = rightMid) {
              val pillW = 9.dp.toPx()
              val pillH = 22.dp.toPx()
              val topLeft = Offset(rightMid.x - pillW / 2f, rightMid.y - pillH / 2f)
              drawRoundRect(
                color = Color.White,
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillW / 2f, pillW / 2f),
              )
              drawRoundRect(
                color = IndigoPrimary,
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillW / 2f, pillW / 2f),
                style = Stroke(handleStroke),
              )
            }

            // Squish height handle (bottom edge)
            rotate(degrees = draft.rotationDeg, pivot = bottomMid) {
              val pillW = 22.dp.toPx()
              val pillH = 9.dp.toPx()
              val topLeft = Offset(bottomMid.x - pillW / 2f, bottomMid.y - pillH / 2f)
              drawRoundRect(
                color = Color.White,
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f),
              )
              drawRoundRect(
                color = IndigoPrimary,
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f),
                style = Stroke(handleStroke),
              )
            }

            // Corner resize handle (bottom-right)
            drawCircle(color = Color.White, radius = knobRadius, center = br)
            drawCircle(color = IndigoPrimary, radius = knobRadius, center = br, style = Stroke(handleStroke))
          }
        }
      }

      // Floating Top-Left: Zoom & contextual tool tip pill
      Row(
        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (zoomScale > 1.05f) {
          Surface(
            onClick = {
              zoomScale = 1f
              panX = 0f
              panY = 0f
            },
            shape = CircleShape,
            color = IndigoDeep.copy(alpha = 0.90f),
            shadowElevation = 4.dp,
            modifier = Modifier.testTag("editor_fit_zoom_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CenterFocusStrong,
                contentDescription = "Reset zoom",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "${((zoomScale * 10f).roundToInt() / 10f)}× Fit",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
              )
            }
          }
        }

        val hint =
          when (activeTool) {
            EditorTool.DRAW -> "Tap or drag to color"
            EditorTool.ERASE -> "Tap or drag to erase"
            EditorTool.EYEDROPPER -> "Tap a block to pick color"
            EditorTool.FLOOD_FILL -> "Tap region to flood fill"
            EditorTool.GLOBAL_REPLACE -> "Tap color to replace all"
            EditorTool.SHAPE ->
              if (activeShape == null) "Drag on grid to draw shape"
              else "Drag handles to squish, resize, or rotate"
            EditorTool.PAN -> "Drag to pan • Pinch to zoom"
          }
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.92f),
          border = BorderStroke(1.dp, BorderSoft),
          shadowElevation = 2.dp,
        ) {
          Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = IndigoDeep,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      // Floating Top-Right: Number overlay, Gridlines, and Pan quick toggles
      Row(
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ViewportTogglePill(
          selected = showNumbers,
          onClick = onToggleNumbers,
          icon = Icons.Filled.Tag,
          label = "Toggle block numbers",
          testTag = "editor_toggle_numbers",
        )
        ViewportTogglePill(
          selected = showGridLines,
          onClick = onToggleGridLines,
          icon = Icons.Filled.GridOn,
          label = "Toggle grid lines",
          testTag = "editor_toggle_gridlines",
        )
        ViewportTogglePill(
          selected = activeTool == EditorTool.PAN,
          onClick = onTogglePanTool,
          icon = Icons.Filled.OpenWith,
          label = "Pan view",
          testTag = "editor_toggle_pan",
        )
      }

      // Floating Bottom-Center: Place / Cancel bar for active Shape draft
      val shapeDraft = activeShape
      if (shapeDraft != null && activeTool == EditorTool.SHAPE) {
        val dimText =
          if (shapeDraft.shapeType == EditorShapeType.LINE) {
            "Line (${previewCellsList.size} blocks)"
          } else {
            val wBlocks = (shapeDraft.halfWidth * 2f).roundToInt().coerceAtLeast(1)
            val hBlocks = (shapeDraft.halfHeight * 2f).roundToInt().coerceAtLeast(1)
            val deg = shapeDraft.rotationDeg.roundToInt()
            if (deg != 0) "$wBlocks×$hBlocks • ${deg}°" else "$wBlocks×$hBlocks"
          }
        Surface(
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.96f),
          border = BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.35f)),
          shadowElevation = 6.dp,
        ) {
          Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = dimText,
              style = MaterialTheme.typography.labelLarge,
              color = IndigoDeep,
            )
            Button(
              onClick = onCommitShapeDraft,
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
              modifier = Modifier.height(40.dp).testTag("editor_shape_commit_button"),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Place", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(
              onClick = onDiscardShapeDraft,
              modifier = Modifier.size(40.dp).testTag("editor_shape_cancel_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Discard shape",
                tint = CoralError,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
  }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
  val ab = b - a
  val lenSq = ab.x * ab.x + ab.y * ab.y
  if (lenSq <= 0.0001f) return (p - a).getDistance()
  val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq).coerceIn(0f, 1f)
  val proj = Offset(a.x + t * ab.x, a.y + t * ab.y)
  return (p - proj).getDistance()
}

@Composable
private fun ViewportTogglePill(
  selected: Boolean,
  onClick: () -> Unit,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (selected) IndigoPrimary else Color.White.copy(alpha = 0.92f),
    border = BorderStroke(1.dp, if (selected) IndigoPrimary else BorderSoft),
    shadowElevation = 3.dp,
    modifier =
      Modifier.size(48.dp)
        .semantics { contentDescription = label }
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (selected) Color.White else IndigoDeep,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
