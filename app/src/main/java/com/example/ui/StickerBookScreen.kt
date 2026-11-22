package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.model.PictureTemplate
import com.example.model.PlacedSticker
import com.example.model.StickerPage
import com.example.model.StickerPagePaper
import com.example.model.clampStickerFraction
import com.example.model.clampStickerScale
import com.example.ui.components.PixelPreviewCanvas
import com.example.ui.components.PreviewRenderMode
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SkyTint
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun StickerBookScreen(
  profileName: String,
  templates: List<PictureTemplate>,
  completedIds: Set<String>,
  pages: List<StickerPage>,
  activePageId: String?,
  placedStickers: List<PlacedSticker>,
  onBack: () -> Unit,
  onSelectPage: (String) -> Unit,
  onAddPage: () -> Unit,
  onCyclePagePaper: (StickerPage) -> Unit,
  onPlaceSticker: (pageId: String, templateId: String, xFraction: Float, yFraction: Float) -> String,
  onUpdateStickerTransform: (PlacedSticker, xFraction: Float, yFraction: Float, scale: Float) -> Unit,
  onBringStickerToFront: (PlacedSticker) -> Unit,
  onToggleMirror: (PlacedSticker) -> Unit,
  onRemoveSticker: (String) -> Unit,
  onClearPage: (String) -> Unit,
  onLockedStickerClick: (PictureTemplate) -> Unit,
) {
  BackHandler { onBack() }

  val effectivePages =
    remember(pages) {
      pages.ifEmpty { listOf(StickerPage(id = "page_1", pageOrder = 0, paper = StickerPagePaper.WHITE)) }
    }
  val currentPageIndex =
    remember(effectivePages, activePageId) {
      effectivePages.indexOfFirst { it.id == activePageId }.let { if (it >= 0) it else 0 }
    }
  val currentPage = effectivePages[currentPageIndex]
  val pageStickers =
    remember(placedStickers, currentPage.id) {
      placedStickers.filter { it.pageId == currentPage.id }.sortedBy { it.zOrder }
    }
  val templateLookup = remember(templates) { templates.associateBy { it.id } }
  val orderedTrayTemplates =
    remember(templates, completedIds) {
      val unlocked = templates.filter { it.id in completedIds }
      val locked = templates.filter { it.id !in completedIds }
      unlocked + locked
    }
  val unlockedCount = remember(templates, completedIds) { templates.count { it.id in completedIds } }

  var selectedStickerId by remember(currentPage.id) { mutableStateOf<String?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

  // Shared animation ticker so animated GIF/sprite stickers play smoothly on the page
  var animTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(220L)
      animTick++
    }
  }

  // Root & zone coordinates for unified tray-to-canvas and canvas-to-trash dragging
  var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
  var canvasBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
  var trayBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

  // Active tray drag ghost state
  var trayDragTemplate by remember { mutableStateOf<PictureTemplate?>(null) }
  var trayDragPosInRoot by remember { mutableStateOf(Offset.Zero) }

  // Active placed-sticker move / resize state
  var movingStickerId by remember { mutableStateOf<String?>(null) }
  var isHoveringTrash by remember { mutableStateOf(false) }

  val density = LocalDensity.current

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
        .onGloballyPositioned { rootCoords = it }
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      StickerBookHeader(
        profileName = profileName,
        unlockedCount = unlockedCount,
        totalCount = templates.size,
        pageIndex = currentPageIndex,
        pageCount = effectivePages.size,
        currentPaper = currentPage.paper,
        canClearPage = pageStickers.isNotEmpty(),
        onBack = onBack,
        onPrevPage = {
          if (currentPageIndex > 0) {
            selectedStickerId = null
            onSelectPage(effectivePages[currentPageIndex - 1].id)
          }
        },
        onNextPage = {
          if (currentPageIndex < effectivePages.lastIndex) {
            selectedStickerId = null
            onSelectPage(effectivePages[currentPageIndex + 1].id)
          }
        },
        onAddPage = {
          selectedStickerId = null
          onAddPage()
        },
        onCyclePaper = { onCyclePagePaper(currentPage) },
        onRequestClear = { showClearConfirm = true },
      )

      // Main Blank Page Canvas
      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        val canvasWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val canvasHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val baseStickerSizePx =
          (min(canvasWidthPx, canvasHeightPx) * 0.22f).coerceIn(
            with(density) { 68.dp.toPx() },
            with(density) { 126.dp.toPx() },
          )
        val baseStickerSizeDp = with(density) { baseStickerSizePx.toDp() }
        val handlePadDp = 20.dp
        val handlePadPx = with(density) { handlePadDp.toPx() }

        Surface(
          modifier =
            Modifier.fillMaxSize()
              .onGloballyPositioned { canvasBoundsInRoot = it.boundsInRoot() }
              .testTag("sticker_book_canvas"),
          shape = RoundedCornerShape(24.dp),
          color = Color(currentPage.paper.argb),
          border = BorderStroke(2.5.dp, BorderSoft),
          shadowElevation = 6.dp,
        ) {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .clipToBounds()
                .pointerInput(currentPage.id) {
                  detectTapGestures { selectedStickerId = null }
                }
          ) {
            if (pageStickers.isEmpty()) {
              Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = IndigoPrimary.copy(alpha = 0.45f),
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text =
                    if (unlockedCount > 0) {
                      "Drag a sticker up from the tray — or tap one to place it!"
                    } else {
                      "Color and finish a picture to unlock your first sticker!"
                    },
                  style = MaterialTheme.typography.titleMedium,
                  color = SlateMuted,
                  textAlign = TextAlign.Center,
                )
              }
            }

            pageStickers.forEachIndexed { layerIndex, sticker ->
              val tpl = templateLookup[sticker.templateId]
              if (tpl != null) {
                key(sticker.id) {
                  val isSelected = selectedStickerId == sticker.id
                  val isMovingThis = movingStickerId == sticker.id
                  PlacedStickerItem(
                    sticker = sticker,
                    template = tpl,
                    baseZIndex = layerIndex.toFloat(),
                    topZIndex = (pageStickers.size + 100).toFloat(),
                    isSelected = isSelected,
                    isDimmedForTrash = isMovingThis && isHoveringTrash,
                    animTick = animTick,
                    baseStickerSizePx = baseStickerSizePx,
                    handlePadPx = handlePadPx,
                    canvasWidthPx = canvasBoundsInRoot.width.takeIf { it > 1f } ?: canvasWidthPx,
                    canvasHeightPx = canvasBoundsInRoot.height.takeIf { it > 1f } ?: canvasHeightPx,
                    canvasBoundsInRoot = canvasBoundsInRoot,
                    trayBoundsInRoot = trayBoundsInRoot,
                    onSelectImmediate = { selectedStickerId = sticker.id },
                    onTapCommitFront = { onBringStickerToFront(sticker) },
                    onDragStateChange = { dragging, hoveringTrash ->
                      movingStickerId = if (dragging) sticker.id else null
                      isHoveringTrash = dragging && hoveringTrash
                    },
                    onCommitTransform = { newXFrac, newYFrac, newScale ->
                      onUpdateStickerTransform(sticker, newXFrac, newYFrac, newScale)
                    },
                    onToggleMirror = { onToggleMirror(sticker) },
                    onRemove = {
                      if (selectedStickerId == sticker.id) selectedStickerId = null
                      onRemoveSticker(sticker.id)
                    },
                  )
                }
              }
            }

            // Highlight canvas border subtly while dragging a sticker up from the tray
            if (trayDragTemplate != null) {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .background(IndigoPrimary.copy(alpha = 0.04f))
              )
            }
          }
        }
      }

      // Bottom Sticker Slider / Drop-to-Remove Trash Zone
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .onGloballyPositioned { trayBoundsInRoot = it.boundsInRoot() },
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.5.dp, BorderSoft),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .navigationBarsPadding()
              .height(114.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (movingStickerId != null) {
            // Morph tray into Drop-to-Remove Zone while dragging a placed sticker
            val trashBg =
              if (isHoveringTrash) CoralError else CoralError.copy(alpha = 0.14f)
            val trashContent = if (isHoveringTrash) Color.White else CoralError
            Surface(
              modifier =
                Modifier.fillMaxSize()
                  .padding(horizontal = 16.dp, vertical = 14.dp)
                  .testTag("sticker_trash_drop_zone"),
              shape = RoundedCornerShape(18.dp),
              color = trashBg,
              border = BorderStroke(2.dp, CoralError),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = null,
                  tint = trashContent,
                  modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = stringResource(R.string.drop_to_remove),
                  style = MaterialTheme.typography.titleMedium,
                  color = trashContent,
                )
              }
            }
          } else {
            LazyRow(
              modifier = Modifier.fillMaxSize().testTag("sticker_tray_slider"),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              items(orderedTrayTemplates, key = { it.id }) { tpl ->
                val unlocked = tpl.id in completedIds
                StickerTrayItem(
                  template = tpl,
                  unlocked = unlocked,
                  animTick = animTick,
                  onTapPlace = {
                    val count = pageStickers.size
                    val offsetPattern = ((count % 5) - 2) * 0.045f
                    val yOffsetPattern = (((count / 5) % 3) - 1) * 0.045f
                    val placedId =
                      onPlaceSticker(
                        currentPage.id,
                        tpl.id,
                        (0.5f + offsetPattern).coerceIn(0.15f, 0.85f),
                        (0.5f + yOffsetPattern).coerceIn(0.15f, 0.85f),
                      )
                    selectedStickerId = placedId
                  },
                  onDragStart = { startRootPos ->
                    trayDragTemplate = tpl
                    trayDragPosInRoot = startRootPos
                  },
                  onDragMove = { rootPos ->
                    trayDragPosInRoot = rootPos
                  },
                  onDragEnd = { endRootPos ->
                    val draggingTpl = trayDragTemplate
                    trayDragTemplate = null
                    if (draggingTpl != null && canvasBoundsInRoot.width > 1f && canvasBoundsInRoot.height > 1f) {
                      val droppedAboveTray =
                        endRootPos.y < trayBoundsInRoot.top || canvasBoundsInRoot.contains(endRootPos)
                      if (droppedAboveTray) {
                        val canvasW = canvasBoundsInRoot.width
                        val canvasH = canvasBoundsInRoot.height
                        val approxBasePx = (min(canvasW, canvasH) * 0.22f)
                        val rawX = (endRootPos.x - canvasBoundsInRoot.left) / canvasW
                        val rawY = (endRootPos.y - canvasBoundsInRoot.top) / canvasH
                        val clampedX = clampStickerFraction(rawX, (approxBasePx * 0.5f) / canvasW)
                        val clampedY = clampStickerFraction(rawY, (approxBasePx * 0.5f) / canvasH)
                        val placedId = onPlaceSticker(currentPage.id, draggingTpl.id, clampedX, clampedY)
                        selectedStickerId = placedId
                      }
                    }
                  },
                  onDragCancel = {
                    trayDragTemplate = null
                  },
                  onLockedClick = { onLockedStickerClick(tpl) },
                )
              }
            }
          }
        }
      }
    }

    // Floating Drag Ghost rendered above both tray and canvas
    val ghostTpl = trayDragTemplate
    val rootOrigin = remember(rootCoords) { rootCoords?.boundsInRoot()?.topLeft ?: Offset.Zero }
    if (ghostTpl != null) {
      val ghostSizeDp = 96.dp
      val ghostHalfPx = with(density) { (ghostSizeDp / 2).toPx() }
      val localPos = trayDragPosInRoot - rootOrigin
      Surface(
        modifier =
          Modifier.offset {
            IntOffset(
              (localPos.x - ghostHalfPx).roundToInt(),
              (localPos.y - ghostHalfPx).roundToInt(),
            )
          }
            .size(ghostSizeDp)
            .graphicsLayer {
              scaleX = 1.12f
              scaleY = 1.12f
              alpha = 0.92f
            },
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.85f),
        shadowElevation = 10.dp,
        border = BorderStroke(2.dp, IndigoPrimary),
      ) {
        PixelPreviewCanvas(
          template = ghostTpl,
          mode = PreviewRenderMode.FULL_COLOR,
          frameIndex = if (ghostTpl.frames.size > 1) animTick % ghostTpl.frames.size else null,
          modifier = Modifier.fillMaxSize().padding(8.dp),
        )
      }
    }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear Page ${currentPageIndex + 1}?") },
      text = { Text("This removes all stickers from this page so you can start a fresh scene.") },
      confirmButton = {
        TextButton(
          onClick = {
            selectedStickerId = null
            showClearConfirm = false
            onClearPage(currentPage.id)
          },
          colors = ButtonDefaults.textButtonColors(contentColor = CoralError),
        ) {
          Text(stringResource(R.string.clear_sticker_page))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun StickerBookHeader(
  profileName: String,
  unlockedCount: Int,
  totalCount: Int,
  pageIndex: Int,
  pageCount: Int,
  currentPaper: StickerPagePaper,
  canClearPage: Boolean,
  onBack: () -> Unit,
  onPrevPage: () -> Unit,
  onNextPage: () -> Unit,
  onAddPage: () -> Unit,
  onCyclePaper: () -> Unit,
  onRequestClear: () -> Unit,
) {
  Surface(color = Color.White.copy(alpha = 0.92f), shadowElevation = 4.dp) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
      val isWide = maxWidth >= 660.dp

      @Composable
      fun PageSwitcherControls() {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = IndigoContainer.copy(alpha = 0.65f),
            border = BorderStroke(1.5.dp, BorderSoft),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = onPrevPage,
                enabled = pageIndex > 0,
                modifier = Modifier.size(48.dp).testTag("sticker_prev_page_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = "Previous page",
                )
              }
              Text(
                text = "Page ${pageIndex + 1}/$pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = IndigoDeep,
                modifier = Modifier.padding(horizontal = 4.dp),
              )
              IconButton(
                onClick = onNextPage,
                enabled = pageIndex < pageCount - 1,
                modifier = Modifier.size(48.dp).testTag("sticker_next_page_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                  contentDescription = "Next page",
                )
              }
            }
          }

          IconButton(
            onClick = onAddPage,
            modifier = Modifier.size(48.dp).testTag("sticker_add_page_button"),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = IndigoContainer,
                contentColor = IndigoPrimary,
              ),
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = stringResource(R.string.add_sticker_page),
            )
          }

          val paperDesc = stringResource(R.string.change_page_color)
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onCyclePaper)
                .semantics { contentDescription = "$paperDesc: ${currentPaper.label}" }
                .testTag("sticker_page_color_button"),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              modifier = Modifier.size(28.dp),
              shape = CircleShape,
              color = Color(currentPaper.argb),
              border = BorderStroke(2.dp, IndigoPrimary),
              shadowElevation = 2.dp,
            ) {}
          }
        }
      }

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("sticker_back_button"),
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
            Text(
              text = "📒 $profileName's Sticker Book",
              style = MaterialTheme.typography.titleLarge,
              color = IndigoDeep,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$unlockedCount/$totalCount stickers unlocked",
              style = MaterialTheme.typography.bodySmall,
              color = IndigoPrimary,
            )
          }
          PageSwitcherControls()
          IconButton(
            onClick = onRequestClear,
            enabled = canClearPage,
            modifier = Modifier.size(48.dp).testTag("sticker_clear_page_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = stringResource(R.string.clear_sticker_page),
              tint = if (canClearPage) CoralError else SlateMuted.copy(alpha = 0.4f),
            )
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp).testTag("sticker_back_button"),
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
              Text(
                text = "📒 $profileName's Stickers",
                style = MaterialTheme.typography.titleMedium,
                color = IndigoDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "$unlockedCount/$totalCount unlocked",
                style = MaterialTheme.typography.bodySmall,
                color = IndigoPrimary,
              )
            }
            IconButton(
              onClick = onRequestClear,
              enabled = canClearPage,
              modifier = Modifier.size(48.dp).testTag("sticker_clear_page_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.clear_sticker_page),
                tint = if (canClearPage) CoralError else SlateMuted.copy(alpha = 0.4f),
              )
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
          ) {
            PageSwitcherControls()
          }
        }
      }
    }
  }
}

@Composable
private fun PlacedStickerItem(
  sticker: PlacedSticker,
  template: PictureTemplate,
  baseZIndex: Float,
  topZIndex: Float,
  isSelected: Boolean,
  isDimmedForTrash: Boolean,
  animTick: Int,
  baseStickerSizePx: Float,
  handlePadPx: Float,
  canvasWidthPx: Float,
  canvasHeightPx: Float,
  canvasBoundsInRoot: Rect,
  trayBoundsInRoot: Rect,
  onSelectImmediate: () -> Unit,
  onTapCommitFront: () -> Unit,
  onDragStateChange: (dragging: Boolean, hoveringTrash: Boolean) -> Unit,
  onCommitTransform: (xFraction: Float, yFraction: Float, scale: Float) -> Unit,
  onToggleMirror: () -> Unit,
  onRemove: () -> Unit,
) {
  val density = LocalDensity.current
  val viewConfig = LocalViewConfiguration.current

  var liveXFrac by remember(sticker.id) { mutableFloatStateOf(sticker.xFraction) }
  var liveYFrac by remember(sticker.id) { mutableFloatStateOf(sticker.yFraction) }
  var liveScale by remember(sticker.id) { mutableFloatStateOf(sticker.scale) }
  var isDragging by remember(sticker.id) { mutableStateOf(false) }
  var isResizing by remember(sticker.id) { mutableStateOf(false) }

  LaunchedEffect(sticker.xFraction, sticker.yFraction, sticker.scale) {
    if (!isDragging && !isResizing) {
      liveXFrac = sticker.xFraction
      liveYFrac = sticker.yFraction
      liveScale = sticker.scale
    }
  }

  val currentCanvasBounds by rememberUpdatedState(canvasBoundsInRoot)
  val currentTrayBounds by rememberUpdatedState(trayBoundsInRoot)
  val currentCanvasW by rememberUpdatedState(canvasWidthPx)
  val currentCanvasH by rememberUpdatedState(canvasHeightPx)
  val currentOnSelectImmediate by rememberUpdatedState(onSelectImmediate)
  val currentOnTapCommitFront by rememberUpdatedState(onTapCommitFront)
  val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)
  val currentOnCommitTransform by rememberUpdatedState(onCommitTransform)
  val currentOnToggleMirror by rememberUpdatedState(onToggleMirror)
  val currentOnRemove by rememberUpdatedState(onRemove)

  val stickerPx = baseStickerSizePx * liveScale
  val boxPx = stickerPx + handlePadPx * 2f
  val stickerDp = with(density) { stickerPx.toDp() }
  val boxDp = with(density) { boxPx.toDp() }

  val offsetX = (liveXFrac * canvasWidthPx - boxPx / 2f).roundToInt()
  val offsetY = (liveYFrac * canvasHeightPx - boxPx / 2f).roundToInt()

  var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
  var resizeHandleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

  Box(
    modifier =
      Modifier.zIndex(if (isSelected || isDragging || isResizing) topZIndex else baseZIndex)
        .offset { IntOffset(offsetX, offsetY) }
        .size(boxDp)
        .graphicsLayer {
          alpha = if (isDimmedForTrash) 0.45f else 1f
          val trashZoom = if (isDimmedForTrash) 0.78f else 1f
          scaleX = trashZoom
          scaleY = trashZoom
        }
  ) {
    // Inner Sticker Body (drag to move / tap to select / drop into bottom tray to remove)
    Box(
      modifier =
        Modifier.align(Alignment.Center)
          .size(stickerDp)
          .onGloballyPositioned { bodyCoords = it }
          .then(
            if (isSelected) {
              Modifier.background(
                color = IndigoContainer.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
              )
            } else {
              Modifier
            }
          )
          .pointerInput(sticker.id) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              down.consume()
              currentOnSelectImmediate()

              val startXFrac = liveXFrac
              val startYFrac = liveYFrac
              val startPtrRoot = bodyCoords?.localToRoot(down.position)
              var exceededSlop = false
              var lastOverTrash = false

              try {
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                  if (change.changedToUpIgnoreConsumed()) {
                    change.consume()
                    break
                  }
                  change.consume()

                  val curPtrRoot = bodyCoords?.localToRoot(change.position) ?: continue
                  if (startPtrRoot == null) continue
                  val deltaRoot = curPtrRoot - startPtrRoot

                  if (!exceededSlop && hypot(deltaRoot.x, deltaRoot.y) > viewConfig.touchSlop) {
                    exceededSlop = true
                    isDragging = true
                  }
                  if (exceededSlop) {
                    val cw = currentCanvasW.coerceAtLeast(1f)
                    val ch = currentCanvasH.coerceAtLeast(1f)
                    liveXFrac = startXFrac + deltaRoot.x / cw
                    liveYFrac = startYFrac + deltaRoot.y / ch
                    lastOverTrash =
                      currentTrayBounds.contains(curPtrRoot) ||
                        curPtrRoot.y >= currentCanvasBounds.bottom
                    currentOnDragStateChange(true, lastOverTrash)
                  }
                }
              } finally {
                val wasDragging = isDragging
                isDragging = false
                currentOnDragStateChange(false, false)

                val cw = currentCanvasW.coerceAtLeast(1f)
                val ch = currentCanvasH.coerceAtLeast(1f)
                val hoveringTrashAtDrop =
                  wasDragging &&
                    (lastOverTrash ||
                      (currentCanvasBounds.top + liveYFrac * ch) >= currentTrayBounds.top)

                if (hoveringTrashAtDrop) {
                  currentOnRemove()
                } else if (wasDragging) {
                  val halfFracW = ((baseStickerSizePx * liveScale) * 0.5f) / cw
                  val halfFracH = ((baseStickerSizePx * liveScale) * 0.5f) / ch
                  val clampedX = clampStickerFraction(liveXFrac, halfFracW)
                  val clampedY = clampStickerFraction(liveYFrac, halfFracH)
                  liveXFrac = clampedX
                  liveYFrac = clampedY
                  currentOnCommitTransform(clampedX, clampedY, liveScale)
                } else {
                  currentOnTapCommitFront()
                }
              }
            }
          }
          .testTag("placed_sticker_${sticker.id}"),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = if (isSelected) BorderStroke(2.dp, IndigoPrimary) else null,
      ) {
        PixelPreviewCanvas(
          template = template,
          mode = PreviewRenderMode.FULL_COLOR,
          frameIndex = if (template.frames.size > 1) animTick % template.frames.size else null,
          modifier =
            Modifier.fillMaxSize()
              .padding(4.dp)
              .graphicsLayer { scaleX = if (sticker.isMirrored) -1f else 1f },
        )
      }
    }

    // Selection Corner Handles: X in Top-Left, Resize in Top-Right, Mirror in Bottom-Right
    AnimatedVisibility(
      visible = isSelected && !isDragging,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // Top-Left: Remove (X) button
        Box(
          modifier =
            Modifier.align(Alignment.TopStart)
              .size(48.dp)
              .clip(CircleShape)
              .clickable { currentOnRemove() }
              .testTag("sticker_remove_handle"),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = CoralError,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 4.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove_sticker),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }

        // Top-Right: Resize handle (measured from fixed sticker center in root coordinates)
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .size(48.dp)
              .onGloballyPositioned { resizeHandleCoords = it }
              .pointerInput(sticker.id) {
                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  down.consume()
                  isResizing = true
                  val startScale = liveScale
                  val cw = currentCanvasW.coerceAtLeast(1f)
                  val ch = currentCanvasH.coerceAtLeast(1f)
                  val centerRoot =
                    Offset(
                      currentCanvasBounds.left + liveXFrac * cw,
                      currentCanvasBounds.top + liveYFrac * ch,
                    )
                  val startPtrRoot = resizeHandleCoords?.localToRoot(down.position)
                  val startDist =
                    if (startPtrRoot != null) {
                      hypot(startPtrRoot.x - centerRoot.x, startPtrRoot.y - centerRoot.y)
                        .coerceAtLeast(18f)
                    } else {
                      ((baseStickerSizePx * startScale) * 0.7f).coerceAtLeast(24f)
                    }

                  try {
                    while (true) {
                      val event = awaitPointerEvent()
                      val change =
                        event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                      if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        break
                      }
                      change.consume()
                      val curPtrRoot = resizeHandleCoords?.localToRoot(change.position) ?: continue
                      val curDist = hypot(curPtrRoot.x - centerRoot.x, curPtrRoot.y - centerRoot.y)
                      liveScale = clampStickerScale(startScale * (curDist / startDist))
                    }
                  } finally {
                    isResizing = false
                    val finalCw = currentCanvasW.coerceAtLeast(1f)
                    val finalCh = currentCanvasH.coerceAtLeast(1f)
                    val halfFracW = ((baseStickerSizePx * liveScale) * 0.5f) / finalCw
                    val halfFracH = ((baseStickerSizePx * liveScale) * 0.5f) / finalCh
                    val clampedX = clampStickerFraction(liveXFrac, halfFracW)
                    val clampedY = clampStickerFraction(liveYFrac, halfFracH)
                    liveXFrac = clampedX
                    liveYFrac = clampedY
                    currentOnCommitTransform(clampedX, clampedY, liveScale)
                  }
                }
              }
              .semantics { contentDescription = "Resize sticker" }
              .testTag("sticker_resize_handle"),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = IndigoPrimary,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 4.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = stringResource(R.string.resize_sticker),
                tint = Color.White,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }

        // Bottom-Right: Mirror horizontally button
        Box(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .size(48.dp)
              .clip(CircleShape)
              .clickable { currentOnToggleMirror() }
              .testTag("sticker_mirror_handle"),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (sticker.isMirrored) IndigoDeep else Color(0xFF0D9488),
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 4.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Flip,
                contentDescription = stringResource(R.string.mirror_sticker),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StickerTrayItem(
  template: PictureTemplate,
  unlocked: Boolean,
  animTick: Int,
  onTapPlace: () -> Unit,
  onDragStart: (Offset) -> Unit,
  onDragMove: (Offset) -> Unit,
  onDragEnd: (Offset) -> Unit,
  onDragCancel: () -> Unit,
  onLockedClick: () -> Unit,
) {
  val viewConfig = LocalViewConfiguration.current
  var itemCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

  Surface(
    modifier =
      Modifier.width(84.dp)
        .fillMaxHeight()
        .onGloballyPositioned { itemCoords = it }
        .then(
          if (unlocked) {
            Modifier.pointerInput(template.id) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var totalDx = 0f
                var totalDy = 0f
                var draggingOut = false
                var endedNormally = false

                try {
                  while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    if (change.changedToUpIgnoreConsumed()) {
                      endedNormally = true
                      if (draggingOut) {
                        change.consume()
                        val endRoot = itemCoords?.localToRoot(change.position) ?: change.position
                        onDragEnd(endRoot)
                      } else if (!change.isConsumed) {
                        onTapPlace()
                      }
                      break
                    }
                    if (change.isConsumed && !draggingOut) {
                      // Horizontal LazyRow scroll claimed the gesture
                      endedNormally = true
                      break
                    }

                    val step = change.position - change.previousPosition
                    totalDx += step.x
                    totalDy += step.y

                    if (!draggingOut) {
                      val dist = hypot(totalDx, totalDy)
                      if (dist > viewConfig.touchSlop) {
                        if (-totalDy > abs(totalDx) * 1.1f) {
                          // Upward pull from tray -> lift sticker onto page!
                          draggingOut = true
                          change.consume()
                          val rootPos = itemCoords?.localToRoot(change.position) ?: change.position
                          onDragStart(rootPos)
                        } else {
                          // Horizontal swipe -> yield to LazyRow scrolling
                          endedNormally = true
                          break
                        }
                      }
                    } else {
                      change.consume()
                      val rootPos = itemCoords?.localToRoot(change.position) ?: change.position
                      onDragMove(rootPos)
                    }
                  }
                } finally {
                  if (draggingOut && !endedNormally) {
                    onDragCancel()
                  }
                }
              }
            }
          } else {
            Modifier.clickable { onLockedClick() }
          }
        )
        .testTag("sticker_tray_item_${template.id}"),
    shape = RoundedCornerShape(16.dp),
    color = if (unlocked) Color(0xFFF8FAFC) else Color(0xFFFFFBEB),
    border =
      BorderStroke(
        width = 1.5.dp,
        color = if (unlocked) BorderSoft else Color(0xFFFDE68A),
      ),
    shadowElevation = if (unlocked) 2.dp else 0.dp,
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        PixelPreviewCanvas(
          template = template,
          mode =
            if (unlocked) PreviewRenderMode.FULL_COLOR
            else PreviewRenderMode.SHADOW_SILHOUETTE,
          frameIndex =
            if (unlocked && template.frames.size > 1) animTick % template.frames.size
            else null,
          modifier = Modifier.weight(1f).aspectRatio(1f).alpha(if (unlocked) 1f else 0.72f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = if (unlocked) template.title else "?",
          style = MaterialTheme.typography.labelSmall,
          color = if (unlocked) SlateInk else Color(0xFF92400E),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
      if (!unlocked) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = "Locked",
          tint = Color(0xFFD97706),
          modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
        )
      }
    }
  }
}
