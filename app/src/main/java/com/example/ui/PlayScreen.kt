package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.BUCKET_COLOR_LIMIT
import com.example.model.BrushType
import com.example.model.ChallengeMode
import com.example.model.KidProfile
import com.example.model.contrastInkForArgb
import com.example.ui.theme.ActivePaintGlow
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MarigoldAccent
import com.example.ui.theme.SkyTint
import com.example.ui.theme.SlateMuted
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.withTimeoutOrNull

private val DANCE_LEAN = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)
private val DANCE_HOP = intArrayOf(0, -2, 0, -3, -1, -2, 0, -3)

internal data class ViewportMetrics(
  val baseCellSize: Float,
  val cellSize: Float,
  val gridPixelW: Float,
  val gridPixelH: Float,
  val baseOriginX: Float,
  val baseOriginY: Float,
  val originX: Float,
  val originY: Float,
)

internal fun computeViewportMetrics(
  canvasW: Float,
  canvasH: Float,
  brushRailPx: Float,
  gridW: Int,
  gridH: Int,
  zoom: Float,
  panX: Float,
  panY: Float,
): ViewportMetrics {
  val safeW = canvasW.coerceAtLeast(1f)
  val safeH = canvasH.coerceAtLeast(1f)
  val usableW = (safeW - brushRailPx).coerceAtLeast(120f)
  val baseCellSize = min(usableW / max(1, gridW), safeH / max(1, gridH)) * 0.90f
  val cellSize = baseCellSize * zoom
  val gridPixelW = cellSize * gridW
  val gridPixelH = cellSize * gridH
  val baseOriginX = brushRailPx + (usableW - gridPixelW) / 2f
  val baseOriginY = (safeH - gridPixelH) / 2f
  return ViewportMetrics(
    baseCellSize = baseCellSize,
    cellSize = cellSize,
    gridPixelW = gridPixelW,
    gridPixelH = gridPixelH,
    baseOriginX = baseOriginX,
    baseOriginY = baseOriginY,
    originX = baseOriginX + panX,
    originY = baseOriginY + panY,
  )
}

internal fun clampPanOffset(
  rawPanX: Float,
  rawPanY: Float,
  metrics: ViewportMetrics,
  canvasW: Float,
  canvasH: Float,
  brushRailPx: Float,
  zoom: Float,
): Pair<Float, Float> {
  if (zoom <= 1.02f) return 0f to 0f
  val usableW = (canvasW - brushRailPx).coerceAtLeast(120f)
  val centerX = brushRailPx + usableW * 0.5f
  val centerY = canvasH * 0.5f
  val minOriginX = centerX - metrics.gridPixelW
  val maxOriginX = centerX
  val minOriginY = centerY - metrics.gridPixelH
  val maxOriginY = centerY
  val clampedOriginX = (metrics.baseOriginX + rawPanX).coerceIn(minOriginX, maxOriginX)
  val clampedOriginY = (metrics.baseOriginY + rawPanY).coerceIn(minOriginY, maxOriginY)
  return (clampedOriginX - metrics.baseOriginX) to (clampedOriginY - metrics.baseOriginY)
}

internal fun screenToGridCell(
  pos: Offset,
  metrics: ViewportMetrics,
  gridW: Int,
  gridH: Int,
): Pair<Int, Int>? {
  val c = floor((pos.x - metrics.originX) / metrics.cellSize).toInt()
  val r = floor((pos.y - metrics.originY) / metrics.cellSize).toInt()
  return if (r in 0 until gridH && c in 0 until gridW) r to c else null
}

private data class ConfettiPiece(
  val xFraction: Float,
  val speed: Float,
  val startOffset: Float,
  val sizeDp: Float,
  val color: Color,
  val swayFreq: Float,
)

@Composable
fun PlayScreen(
  profile: KidProfile?,
  playState: PlayUiState,
  onBack: () -> Unit,
  onSelectColor: (Int) -> Unit,
  onSelectBrush: (BrushType) -> Unit,
  onPaintStroke: (fromRow: Int?, fromCol: Int?, toRow: Int, toCol: Int) -> Unit,
  onReplay: () -> Unit,
  onToggleSound: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  BackHandler { onBack() }

  val tpl = playState.template
  val challengeMode = profile?.challengeMode ?: ChallengeMode.PRE_K
  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val brushRailReservedPx = with(density) { 80.dp.toPx() }

  var zoomScale by remember(tpl.id) { mutableFloatStateOf(1f) }
  var panX by remember(tpl.id) { mutableFloatStateOf(0f) }
  var panY by remember(tpl.id) { mutableFloatStateOf(0f) }
  var isMoveMode by remember(tpl.id) { mutableStateOf(false) }
  var longPressPaintActive by remember { mutableStateOf(false) }

  val resetViewport = {
    zoomScale = 1f
    panX = 0f
    panY = 0f
    isMoveMode = false
    longPressPaintActive = false
  }

  LaunchedEffect(zoomScale) {
    if (zoomScale <= 1.05f && isMoveMode) {
      isMoveMode = false
    }
  }

  LaunchedEffect(playState.isCompleted) {
    if (playState.isCompleted) {
      resetViewport()
    }
  }

  val latestPaintStroke by rememberUpdatedState(onPaintStroke)
  val latestTemplate by rememberUpdatedState(tpl)
  val latestIsCompleted by rememberUpdatedState(playState.isCompleted)
  val latestIsMoveMode by rememberUpdatedState(isMoveMode)

  val numberPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
  ) {
    // Top Header Bar
    Surface(
      color = Color.White.copy(alpha = 0.88f),
      shadowElevation = 3.dp,
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("play_back_button"),
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = IndigoContainer,
              contentColor = IndigoPrimary,
            ),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back_to_pictures),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = tpl.title,
              style = MaterialTheme.typography.titleLarge,
              color = IndigoDeep,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${(playState.progressFraction * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
              color = if (playState.isCompleted) EmeraldSuccess else IndigoPrimary,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { playState.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = if (playState.isCompleted) EmeraldSuccess else IndigoPrimary,
            trackColor = IndigoContainer,
          )
        }

        val soundOn = profile?.soundEnabled != false
        IconButton(
          onClick = onToggleSound,
          modifier = Modifier.size(48.dp).testTag("play_sound_button"),
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = IndigoContainer,
              contentColor = IndigoPrimary,
            ),
        ) {
          Icon(
            imageVector =
              if (soundOn) Icons.AutoMirrored.Filled.VolumeUp
              else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = stringResource(R.string.toggle_sound),
          )
        }

        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier.size(48.dp).testTag("play_settings_button"),
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = IndigoContainer,
              contentColor = IndigoPrimary,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.open_settings),
          )
        }
      }
    }

    // Center Interactive Coloring Canvas + Floating Brush Tools
    Box(
      modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
    ) {
      Canvas(
        modifier =
          Modifier.fillMaxSize()
            .testTag("play_canvas")
            .pointerInput(tpl.id) {
              awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val startPos = firstDown.position
                val startTime = System.currentTimeMillis()
                var moved = false
                var wasMultiTouch = false
                var strokeStarted = false
                var prevCell: Pair<Int, Int>? = null
                longPressPaintActive = false

                while (true) {
                  val curTpl = latestTemplate
                  val curRailPx = if (latestIsCompleted) 0f else brushRailReservedPx
                  val w = size.width.toFloat()
                  val h = size.height.toFloat()

                  val canTriggerLongPress =
                    !moved &&
                      zoomScale > 1.05f &&
                      latestIsMoveMode &&
                      !longPressPaintActive &&
                      !latestIsCompleted

                  val event =
                    if (canTriggerLongPress) {
                      val remaining = (350L - (System.currentTimeMillis() - startTime)).coerceAtLeast(1L)
                      withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    } else {
                      awaitPointerEvent()
                    }

                  if (event == null) {
                    // Long-press timeout reached while holding steady in Move mode
                    longPressPaintActive = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val metrics =
                      computeViewportMetrics(
                        w,
                        h,
                        curRailPx,
                        curTpl.gridWidth,
                        curTpl.gridHeight,
                        zoomScale,
                        panX,
                        panY,
                      )
                    screenToGridCell(startPos, metrics, curTpl.gridWidth, curTpl.gridHeight)?.let {
                      (r, c) ->
                      latestPaintStroke(null, null, r, c)
                      prevCell = r to c
                      strokeStarted = true
                    }
                    continue
                  }

                  val pressedChanges = event.changes.filter { it.pressed }
                  if (pressedChanges.isEmpty()) {
                    if (!wasMultiTouch && !moved && !strokeStarted && !latestIsCompleted) {
                      val metrics =
                        computeViewportMetrics(
                          w,
                          h,
                          curRailPx,
                          curTpl.gridWidth,
                          curTpl.gridHeight,
                          zoomScale,
                          panX,
                          panY,
                        )
                      screenToGridCell(startPos, metrics, curTpl.gridWidth, curTpl.gridHeight)?.let {
                        (r, c) ->
                        latestPaintStroke(null, null, r, c)
                      }
                    }
                    longPressPaintActive = false
                    break
                  }

                  if (pressedChanges.size >= 2) {
                    wasMultiTouch = true
                    longPressPaintActive = false
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = true)

                    if (zoomChange != 1f || panChange != Offset.Zero) {
                      val oldScale = zoomScale
                      val newScale = (oldScale * zoomChange).coerceIn(1f, 10f)
                      val scaleRatio = newScale / oldScale

                      val mOld =
                        computeViewportMetrics(
                          w,
                          h,
                          curRailPx,
                          curTpl.gridWidth,
                          curTpl.gridHeight,
                          oldScale,
                          panX,
                          panY,
                        )
                      val targetOriginX =
                        centroid.x - (centroid.x - mOld.originX) * scaleRatio + panChange.x
                      val targetOriginY =
                        centroid.y - (centroid.y - mOld.originY) * scaleRatio + panChange.y

                      val mNewBase =
                        computeViewportMetrics(
                          w,
                          h,
                          curRailPx,
                          curTpl.gridWidth,
                          curTpl.gridHeight,
                          newScale,
                          0f,
                          0f,
                        )
                      val (clampedX, clampedY) =
                        clampPanOffset(
                          targetOriginX - mNewBase.baseOriginX,
                          targetOriginY - mNewBase.baseOriginY,
                          mNewBase,
                          w,
                          h,
                          curRailPx,
                          newScale,
                        )
                      zoomScale = newScale
                      panX = clampedX
                      panY = clampedY
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    continue
                  }

                  // Single pointer active after a pinch: wait cleanly for release
                  if (wasMultiTouch) {
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    continue
                  }

                  val change = pressedChanges.first()
                  val curPos = change.position
                  val deltaFromStart = curPos - startPos
                  if (!moved && deltaFromStart.getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                  }

                  if (moved) {
                    val shouldPaint =
                      !latestIsCompleted &&
                        (zoomScale <= 1.05f || !latestIsMoveMode || longPressPaintActive)

                    if (shouldPaint) {
                      val metrics =
                        computeViewportMetrics(
                          w,
                          h,
                          curRailPx,
                          curTpl.gridWidth,
                          curTpl.gridHeight,
                          zoomScale,
                          panX,
                          panY,
                        )
                      if (!strokeStarted) {
                        val firstCell =
                          screenToGridCell(startPos, metrics, curTpl.gridWidth, curTpl.gridHeight)
                        if (firstCell != null) {
                          latestPaintStroke(null, null, firstCell.first, firstCell.second)
                          prevCell = firstCell
                        }
                        strokeStarted = true
                      }
                      screenToGridCell(curPos, metrics, curTpl.gridWidth, curTpl.gridHeight)?.let {
                        (r, c) ->
                        val prev = prevCell
                        if (prev == null || prev.first != r || prev.second != c) {
                          latestPaintStroke(prev?.first, prev?.second, r, c)
                          prevCell = r to c
                        }
                      }
                      change.consume()
                    } else if (zoomScale > 1.05f && (latestIsMoveMode || latestIsCompleted)) {
                      val delta = curPos - change.previousPosition
                      val mCur =
                        computeViewportMetrics(
                          w,
                          h,
                          curRailPx,
                          curTpl.gridWidth,
                          curTpl.gridHeight,
                          zoomScale,
                          0f,
                          0f,
                        )
                      val (cx, cy) =
                        clampPanOffset(
                          panX + delta.x,
                          panY + delta.y,
                          mCur,
                          w,
                          h,
                          curRailPx,
                          zoomScale,
                        )
                      panX = cx
                      panY = cy
                      change.consume()
                    }
                  }
                }
              }
            }
      ) {
        val _rev = playState.paintRevision
        val danceFrame = playState.danceFrame
        val curRailPx = if (playState.isCompleted) 0f else brushRailReservedPx
        val metrics =
          computeViewportMetrics(
            size.width,
            size.height,
            curRailPx,
            tpl.gridWidth,
            tpl.gridHeight,
            zoomScale,
            panX,
            panY,
          )
        val effectiveCellSize = metrics.cellSize
        val gridPixelW = metrics.gridPixelW
        val gridPixelH = metrics.gridPixelH
        val originX = metrics.originX
        val originY = metrics.originY
        val pad = max(8f, effectiveCellSize * 0.35f)

        // Paper Card Shadow + Background
        drawRoundRect(
          color = Color(0x220F172A),
          topLeft = Offset(originX - pad + 4f, originY - pad + 8f),
          size = Size(gridPixelW + pad * 2f, gridPixelH + pad * 2f),
          cornerRadius = CornerRadius(24f, 24f),
        )
        drawRoundRect(
          color = Color.White,
          topLeft = Offset(originX - pad, originY - pad),
          size = Size(gridPixelW + pad * 2f, gridPixelH + pad * 2f),
          cornerRadius = CornerRadius(24f, 24f),
        )
        if (longPressPaintActive) {
          drawRoundRect(
            color = ActivePaintGlow,
            topLeft = Offset(originX - pad, originY - pad),
            size = Size(gridPixelW + pad * 2f, gridPixelH + pad * 2f),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 8f),
          )
        }

        if (playState.isCompleted) {
          val isCustomGif = tpl.isCustom && tpl.frames.size > 1
          val lean = if (isCustomGif) 0 else DANCE_LEAN[danceFrame % DANCE_LEAN.size]
          val hop = if (isCustomGif) 0 else DANCE_HOP[danceFrame % DANCE_HOP.size]
          val denom = max(1, tpl.gridHeight - 1).toFloat()

          for (r in 0 until tpl.gridHeight) {
            val rowFactor = 1f - (r.toFloat() / denom)
            for (c in 0 until tpl.gridWidth) {
              val target = tpl.frameCellNumber(danceFrame, r, c)
              if (target <= 0) continue
              val argb = tpl.palette[target] ?: continue
              val isStill = isCustomGif || target in tpl.still
              val dx = if (isStill) 0f else lean * rowFactor * effectiveCellSize * 0.25f
              val dy = if (isStill) 0f else hop * effectiveCellSize * 0.25f
              drawRect(
                color = Color(argb),
                topLeft =
                  Offset(originX + c * effectiveCellSize + dx, originY + r * effectiveCellSize + dy),
                size = Size(effectiveCellSize + 0.8f, effectiveCellSize + 0.8f),
              )
            }
          }
        } else {
          val showNumbers = effectiveCellSize >= 18f
          if (showNumbers) {
            numberPaint.textSize = (effectiveCellSize * 0.50f).coerceIn(10f, 38f)
          }
          val gridStroke = max(0.8f, effectiveCellSize * 0.03f)
          val textVerticalCenter =
            if (showNumbers) -(numberPaint.descent() + numberPaint.ascent()) / 2f else 0f

          for (r in 0 until tpl.gridHeight) {
            val y = originY + r * effectiveCellSize
            if (y + effectiveCellSize < 0f || y > size.height) continue
            for (c in 0 until tpl.gridWidth) {
              val x = originX + c * effectiveCellSize
              if (x + effectiveCellSize < 0f || x > size.width) continue

              val target = tpl.cellNumber(r, c)
              if (target <= 0) continue

              val painted = playState.paintedGrid.getOrNull(r * tpl.gridWidth + c) ?: 0
              if (painted == target) {
                val argb = tpl.palette[target] ?: 0xFF000000.toInt()
                drawRect(
                  color = Color(argb),
                  topLeft = Offset(x, y),
                  size = Size(effectiveCellSize + 0.7f, effectiveCellSize + 0.7f),
                )
              } else {
                val isActiveTarget = target == playState.activeColorNumber
                drawRect(
                  color = if (isActiveTarget) Color(0xFFD5DAE1) else Color.White,
                  topLeft = Offset(x, y),
                  size = Size(effectiveCellSize, effectiveCellSize),
                )
                drawRect(
                  color = Color(0xFFCBD5E1),
                  topLeft = Offset(x, y),
                  size = Size(effectiveCellSize, effectiveCellSize),
                  style = Stroke(width = gridStroke),
                )
                if (showNumbers) {
                  numberPaint.color = if (isActiveTarget) 0xFF0F172A.toInt() else 0xFF64748B.toInt()
                  drawContext.canvas.nativeCanvas.drawText(
                    target.toString(),
                    x + effectiveCellSize / 2f,
                    y + effectiveCellSize / 2f + textVerticalCenter,
                    numberPaint,
                  )
                }
              }
            }
          }
        }
      }

      // Celebratory Confetti Overlay on Completion
      if (playState.isCompleted) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize())
      }

      // Floating Left Brush Rail (hidden once completed)
      if (!playState.isCompleted) {
        Column(
          modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          BrushType.entries.forEach { brush ->
            val isUnlocked = challengeMode == ChallengeMode.OFF || brush in playState.unlockedBrushes
            val isSelected = playState.activeBrush == brush
            val bucketSpent =
              brush == BrushType.BUCKET &&
                challengeMode != ChallengeMode.OFF &&
                isUnlocked &&
                playState.bucketColorsUsed.size >= BUCKET_COLOR_LIMIT
            val progressDone =
              if (brush == BrushType.BUCKET && isUnlocked) playState.refillProgress
              else (playState.brushProgress[brush] ?: 0)

            BrushToolButton(
              brush = brush,
              isSelected = isSelected,
              isUnlocked = isUnlocked,
              bucketColorsRemaining =
                if (brush == BrushType.BUCKET && isUnlocked && challengeMode != ChallengeMode.OFF) {
                  (BUCKET_COLOR_LIMIT - playState.bucketColorsUsed.size).coerceAtLeast(0)
                } else null,
              bucketSpent = bucketSpent,
              questionProgress = progressDone,
              onClick = { onSelectBrush(brush) },
            )
          }
        }
      }

      // Zoom / Gesture Mode Controls (Top-Right of Canvas)
      Row(
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (zoomScale > 1.05f && !playState.isCompleted) {
          Surface(
            onClick = { isMoveMode = !isMoveMode },
            modifier = Modifier.height(48.dp).testTag("play_gesture_mode_button"),
            shape = CircleShape,
            color = if (isMoveMode) MarigoldAccent else IndigoPrimary,
            shadowElevation = 4.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = if (isMoveMode) Icons.Filled.OpenWith else Icons.Filled.Brush,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = if (isMoveMode) "Move" else "Paint",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
              )
            }
          }
        }

        if (zoomScale > 1.05f || panX != 0f || panY != 0f) {
          Surface(
            onClick = resetViewport,
            modifier = Modifier.height(48.dp).testTag("play_fit_button"),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, BorderSoft),
            shadowElevation = 4.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CenterFocusStrong,
                contentDescription = stringResource(R.string.fit_to_screen),
                tint = IndigoPrimary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Fit",
                style = MaterialTheme.typography.labelLarge,
                color = IndigoDeep,
              )
            }
          }
        }
      }
    }

    // Bottom Bar Below Canvas: Completion Actions OR Numbered Color Palette
    Surface(
      color = Color.White.copy(alpha = 0.94f),
      shadowElevation = 10.dp,
    ) {
      if (playState.isCompleted) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 12.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = onBack,
            modifier =
              Modifier.weight(1f).height(52.dp).testTag("play_back_to_gallery_button"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, IndigoPrimary),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Collections,
              contentDescription = null,
              tint = IndigoPrimary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.back_to_gallery),
              style = MaterialTheme.typography.labelLarge,
              color = IndigoPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Button(
            onClick = {
              resetViewport()
              onReplay()
            },
            modifier = Modifier.weight(1f).height(52.dp).testTag("play_color_again_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.color_again),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else {
        val sortedColors =
          remember(tpl) {
            val used = hashSetOf<Int>()
            for (r in 0 until tpl.gridHeight) {
              for (c in 0 until tpl.gridWidth) {
                val n = tpl.cellNumber(r, c)
                if (n > 0) used.add(n)
              }
            }
            tpl.palette.keys.filter { it in used }.sorted().ifEmpty { tpl.palette.keys.sorted() }
          }
        LazyRow(
          modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp),
          contentPadding = PaddingValues(horizontal = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          items(sortedColors, key = { it }) { colorNum ->
            val argb = tpl.palette[colorNum] ?: 0xFF000000.toInt()
            val swatchColor = Color(argb)
            val inkColor = contrastInkForArgb(argb)
            val remainingCount = playState.remainingByColor[colorNum] ?: 0
            val isColorDone = remainingCount == 0
            val isSelected = playState.activeColorNumber == colorNum

            PaletteSwatchItem(
              colorNumber = colorNum,
              swatchColor = swatchColor,
              inkColor = inkColor,
              isSelected = isSelected,
              isDone = isColorDone,
              onClick = { onSelectColor(colorNum) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BrushToolButton(
  brush: BrushType,
  isSelected: Boolean,
  isUnlocked: Boolean,
  bucketColorsRemaining: Int?,
  bucketSpent: Boolean,
  questionProgress: Int,
  onClick: () -> Unit,
) {
  val bg =
    when {
      isSelected -> IndigoPrimary
      bucketSpent -> Color(0xFFFEF3C7)
      !isUnlocked -> Color.White.copy(alpha = 0.9f)
      else -> Color.White
    }
  val contentColor =
    when {
      isSelected -> Color.White
      bucketSpent -> Color(0xFF92400E)
      else -> IndigoDeep
    }

  Surface(
    onClick = onClick,
    modifier = Modifier.width(64.dp).height(62.dp).testTag("brush_${brush.id}"),
    shape = RoundedCornerShape(18.dp),
    color = bg,
    border =
      BorderStroke(
        width = if (isSelected) 3.dp else 2.dp,
        color =
          when {
            isSelected -> ActivePaintGlow
            bucketSpent -> MarigoldAccent
            else -> BorderSoft
          },
      ),
    shadowElevation = if (isSelected) 6.dp else 3.dp,
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        when (brush) {
          BrushType.DOT -> BrushDotGridPreview(size = 1, tint = contentColor)
          BrushType.CROSS -> BrushDotGridPreview(size = 2, tint = contentColor)
          BrushType.BLOB -> BrushDotGridPreview(size = 3, tint = contentColor)
          BrushType.BUCKET ->
            Icon(
              imageVector = Icons.Filled.FormatColorFill,
              contentDescription = brush.title,
              tint = contentColor,
              modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = if (bucketSpent) "Refill" else brush.title,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 11.sp,
          color = contentColor,
          maxLines = 1,
        )
        if (bucketColorsRemaining != null && !bucketSpent) {
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(BUCKET_COLOR_LIMIT) { idx ->
              Box(
                modifier =
                  Modifier.size(5.dp)
                    .clip(CircleShape)
                    .background(
                      if (idx < bucketColorsRemaining) {
                        if (isSelected) MarigoldAccent else EmeraldSuccess
                      } else {
                        SlateMuted.copy(alpha = 0.3f)
                      }
                    )
              )
            }
          }
        }
      }

      if (!isUnlocked) {
        Surface(
          modifier = Modifier.align(Alignment.TopEnd),
          shape = CircleShape,
          color = MarigoldAccent,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = "Locked",
              tint = Color.White,
              modifier = Modifier.size(9.dp),
            )
            val remainingQ = (brush.cost - questionProgress).coerceAtLeast(1)
            Text(
              text = "$remainingQ",
              fontSize = 9.sp,
              fontWeight = FontWeight.Black,
              color = Color.White,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BrushDotGridPreview(size: Int, tint: Color) {
  Canvas(modifier = Modifier.size(20.dp)) {
    val dotR =
      when (size) {
        1 -> 4.5f
        2 -> 3.2f
        else -> 2.4f
      }
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f
    val step =
      when (size) {
        1 -> 0f
        2 -> 6.5f
        else -> 5.2f
      }
    val radius = size - 1
    for (dr in -radius..radius) {
      for (dc in -radius..radius) {
        if (abs(dr) + abs(dc) > radius) continue
        drawCircle(
          color = tint,
          radius = dotR,
          center = Offset(cx + dc * step, cy + dr * step),
        )
      }
    }
  }
}

@Composable
private fun PaletteSwatchItem(
  colorNumber: Int,
  swatchColor: Color,
  inkColor: Color,
  isSelected: Boolean,
  isDone: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.offset { IntOffset(0, if (isSelected) -8 else 0) }
        .size(56.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      onClick = onClick,
      modifier = Modifier.size(52.dp).testTag("palette_color_$colorNumber"),
      shape = CircleShape,
      color = swatchColor,
      border =
        BorderStroke(
          width = if (isSelected) 4.dp else 2.dp,
          color =
            when {
              isSelected -> IndigoPrimary
              isDone -> EmeraldSuccess
              else -> Color(0xFFCBD5E1)
            },
        ),
      shadowElevation = if (isSelected) 7.dp else 2.dp,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = colorNumber.toString(),
          style = MaterialTheme.typography.titleLarge,
          color = inkColor,
        )
      }
    }

    if (isDone) {
      Surface(
        modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
        shape = CircleShape,
        color = EmeraldSuccess,
        border = BorderStroke(1.5.dp, Color.White),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Color finished",
            tint = Color.White,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
  val pieces = remember {
    val palette =
      listOf(
        Color(0xFFF43F5E),
        Color(0xFFF59E0B),
        Color(0xFF10B981),
        Color(0xFF3B82F6),
        Color(0xFFA855F7),
        Color(0xFFEC4899),
      )
    val rng = Random(42)
    List(44) { idx ->
      ConfettiPiece(
        xFraction = rng.nextFloat(),
        speed = 0.65f + rng.nextFloat() * 0.7f,
        startOffset = rng.nextFloat(),
        sizeDp = 7f + rng.nextFloat() * 6f,
        color = palette[idx % palette.size],
        swayFreq = 2f + rng.nextFloat() * 3f,
      )
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "confetti")
  val tick by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "confetti_fall",
    )

  Canvas(modifier = modifier) {
    pieces.forEach { piece ->
      val progress = (tick * piece.speed + piece.startOffset) % 1f
      val x = piece.xFraction * size.width + sin(progress * Math.PI * 2 * piece.swayFreq).toFloat() * 24f
      val y = -30f + progress * (size.height + 60f)
      val s = piece.sizeDp.dp.toPx()
      drawRect(
        color = piece.color.copy(alpha = 0.9f),
        topLeft = Offset(x, y),
        size = Size(s, s * 0.65f),
      )
    }
  }
}
