package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.BlockEditorEngine
import com.example.engine.EditorShapeType
import com.example.engine.EditorTool
import com.example.engine.ShapeDraft
import com.example.model.PictureTemplate
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SkyTint
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import kotlinx.coroutines.delay

@Composable
fun BlockEditorScreen(
  state: BlockEditorUiState,
  onDiscardAndExit: () -> Unit,
  onSaveOrApply: () -> Unit,
  onTitleChange: (String) -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onSelectTool: (EditorTool) -> Unit,
  onToggleFillWithEraser: () -> Unit,
  onToggleGlobalScope: () -> Unit,
  onSelectShapeType: (EditorShapeType) -> Unit,
  onToggleShapeFilled: () -> Unit,
  onUpdateShapeDraft: (ShapeDraft?) -> Unit,
  onCommitShapeDraft: () -> Unit,
  onDiscardShapeDraft: () -> Unit,
  onToggleNumbers: () -> Unit,
  onToggleGridLines: () -> Unit,
  onTogglePanTool: () -> Unit,
  onStrokeBegin: (row: Int, col: Int) -> Unit,
  onStrokeStep: (fromRow: Int?, fromCol: Int?, toRow: Int, toCol: Int) -> Unit,
  onTapCellAction: (row: Int, col: Int) -> Unit,
  onSelectColor: (Int) -> Unit,
  onToggleColorVisibility: (Int) -> Unit,
  onShowAllColors: () -> Unit,
  onUncolorAll: () -> Unit,
  onSwapColors: (Int, Int) -> Unit,
  onReorderPalette: (List<Int>) -> Unit,
  onAddColor: (Int) -> Unit,
  onUpdateColorShade: (colorNum: Int, argb: Int) -> Unit,
  onMergeColor: (sourceNum: Int, targetNum: Int) -> Unit,
  onDeleteUnusedColor: (Int) -> Unit,
  onSelectFrame: (Int) -> Unit,
  onAdvancePlaybackFrame: () -> Unit,
  onTogglePlay: () -> Unit,
  onCycleSpeed: () -> Unit,
  onInsertFrame: (insertIndex: Int, copyCurrent: Boolean) -> Unit,
  onDeleteCurrentFrame: () -> Unit,
  onBuildSnapshotForExport: () -> PictureTemplate? = { null },
  onNotify: (String) -> Unit = {},
  syncState: SyncSettingsUiState = SyncSettingsUiState(),
  onVerifyAdminPin: (String) -> Boolean = { false },
  onSetupAdminPin: (String) -> Boolean = { false },
  onResetAdminPin: () -> Unit = {},
  onPublishToGitHub: ((PictureTemplate, String, (Boolean, String) -> Unit) -> Unit)? = null,
) {
  var showDiscardPrompt by remember { mutableStateOf(false) }
  var showAddColorDialog by remember { mutableStateOf(false) }
  var editingColorNum by remember { mutableStateOf<Int?>(null) }
  var mergingColorNum by remember { mutableStateOf<Int?>(null) }
  var showSwapDialog by remember { mutableStateOf(false) }
  var insertTargetIndex by remember { mutableStateOf<Int?>(null) }
  var showTitleEditDialog by remember { mutableStateOf(false) }
  var exportTemplateSnapshot by remember { mutableStateOf<PictureTemplate?>(null) }

  val handleBack = {
    if (state.hasUnsavedChanges) showDiscardPrompt = true else onDiscardAndExit()
  }
  BackHandler { handleBack() }

  LaunchedEffect(state.isPlayingPreview, state.frameMs, state.frames.size) {
    if (state.isPlayingPreview && state.frames.size > 1) {
      val stepMs = state.frameMs.toLong().coerceIn(80L, 1200L)
      while (true) {
        delay(stepMs)
        onAdvancePlaybackFrame()
      }
    }
  }

  val activeFrame =
    state.frames.getOrNull(state.activeFrameIndex) ?: state.frames.firstOrNull().orEmpty()
  val cellCounts =
    remember(activeFrame, state.palette) {
      BlockEditorEngine.countCellsPerColor(activeFrame, state.palette.keys)
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
  ) {
    // Top Header Bar
    Surface(color = Color.White.copy(alpha = 0.94f), shadowElevation = 4.dp) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = handleBack,
          modifier = Modifier.size(48.dp).testTag("editor_back_button"),
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

        Surface(
          onClick = { showTitleEditDialog = true },
          color = Color.Transparent,
          modifier = Modifier.weight(1f).testTag("editor_title_button"),
        ) {
          Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
              text = state.title.ifBlank { stringResource(R.string.block_editor_title) },
              style = MaterialTheme.typography.titleMedium,
              color = IndigoDeep,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                "${state.gridWidth}×${state.gridHeight} • ${state.palette.size} colors" +
                  if (state.frames.size > 1) " • ${state.frames.size} frames" else "",
              style = MaterialTheme.typography.bodySmall,
              color = IndigoPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        IconButton(
          onClick = onUndo,
          enabled = state.canUndo,
          modifier = Modifier.size(48.dp).testTag("editor_undo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = stringResource(R.string.undo_edit),
            tint = if (state.canUndo) IndigoDeep else SlateMuted.copy(alpha = 0.35f),
          )
        }

        IconButton(
          onClick = onRedo,
          enabled = state.canRedo,
          modifier = Modifier.size(48.dp).testTag("editor_redo_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Redo,
            contentDescription = stringResource(R.string.redo_edit),
            tint = if (state.canRedo) IndigoDeep else SlateMuted.copy(alpha = 0.35f),
          )
        }

        IconButton(
          onClick = {
            if (state.activeShape != null) {
              onNotify("Place or close your shape preview first")
            } else {
              val snap = onBuildSnapshotForExport()
              if (snap == null || snap.totalPaintableCells() == 0) {
                onNotify("Draw at least one block before sharing!")
              } else {
                exportTemplateSnapshot = snap
              }
            }
          },
          modifier = Modifier.size(48.dp).testTag("editor_export_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = stringResource(R.string.export_share),
            tint = IndigoPrimary,
          )
        }

        Button(
          onClick = onSaveOrApply,
          modifier = Modifier.height(48.dp).testTag("editor_save_button"),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
        ) {
          Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              if (state.editingExistingId != null) stringResource(R.string.save_short)
              else stringResource(R.string.apply_short),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }

    // Responsive Body: Side-by-side on wide screens/tablets, Canvas-top + Dock-bottom on compact phones
    BoxWithConstraints(
      modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
    ) {
      val isWide = maxWidth >= 720.dp

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          BlockEditorCanvas(
            gridWidth = state.gridWidth,
            gridHeight = state.gridHeight,
            frame = activeFrame,
            palette = state.palette,
            visibleColors = state.visibleColors,
            activeTool = state.activeTool,
            activeColorNumber = state.activeColorNumber,
            fillWithEraser = state.fillWithEraser,
            shapeType = state.shapeType,
            shapeFilled = state.shapeFilled,
            activeShape = state.activeShape,
            showNumbers = state.showNumbers,
            showGridLines = state.showGridLines,
            onToggleNumbers = onToggleNumbers,
            onToggleGridLines = onToggleGridLines,
            onTogglePanTool = onTogglePanTool,
            onStrokeBegin = onStrokeBegin,
            onStrokeStep = onStrokeStep,
            onTapCellAction = onTapCellAction,
            onUpdateShapeDraft = onUpdateShapeDraft,
            onCommitShapeDraft = onCommitShapeDraft,
            onDiscardShapeDraft = onDiscardShapeDraft,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )

          Surface(
            modifier = Modifier.width(348.dp).fillMaxHeight(),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.96f),
            border = BorderStroke(2.dp, BorderSoft),
            shadowElevation = 4.dp,
          ) {
            Column(
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              SectionLabel("Tools")
              EditorToolsPanel(
                activeTool = state.activeTool,
                fillWithEraser = state.fillWithEraser,
                globalReplaceAllFrames = state.globalReplaceAllFrames,
                isMultiFrame = state.frames.size > 1,
                shapeType = state.shapeType,
                shapeFilled = state.shapeFilled,
                onSelectTool = onSelectTool,
                onToggleFillWithEraser = onToggleFillWithEraser,
                onToggleGlobalScope = onToggleGlobalScope,
                onSelectShapeType = onSelectShapeType,
                onToggleShapeFilled = onToggleShapeFilled,
                compact = false,
              )

              SectionLabel("Palette, Order & Visibility")
              EditorPalettePanel(
                palette = state.palette,
                cellCounts = cellCounts,
                activeColorNumber = state.activeColorNumber,
                visibleColors = state.visibleColors,
                onSelectColor = onSelectColor,
                onToggleColorVisibility = onToggleColorVisibility,
                onShowAllColors = onShowAllColors,
                onUncolorAll = onUncolorAll,
                onRequestSwapColors = { showSwapDialog = true },
                onReorderPalette = onReorderPalette,
                onRequestAddColor = { showAddColorDialog = true },
                onRequestEditColor = { editingColorNum = it },
                onRequestMergeColor = { mergingColorNum = it },
                onDeleteUnusedColor = onDeleteUnusedColor,
                compact = false,
              )

              SectionLabel("Animation Frames")
              EditorFrameTimeline(
                frameCount = state.frames.size,
                activeFrameIndex = state.activeFrameIndex,
                frameMs = state.frameMs,
                isPlaying = state.isPlayingPreview,
                onSelectFrame = onSelectFrame,
                onTogglePlay = onTogglePlay,
                onCycleSpeed = onCycleSpeed,
                onRequestAddEnd = { insertTargetIndex = state.frames.size },
                onRequestInsertAfter = { insertTargetIndex = state.activeFrameIndex + 1 },
                onDeleteCurrentFrame = onDeleteCurrentFrame,
              )
            }
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          BlockEditorCanvas(
            gridWidth = state.gridWidth,
            gridHeight = state.gridHeight,
            frame = activeFrame,
            palette = state.palette,
            visibleColors = state.visibleColors,
            activeTool = state.activeTool,
            activeColorNumber = state.activeColorNumber,
            fillWithEraser = state.fillWithEraser,
            shapeType = state.shapeType,
            shapeFilled = state.shapeFilled,
            activeShape = state.activeShape,
            showNumbers = state.showNumbers,
            showGridLines = state.showGridLines,
            onToggleNumbers = onToggleNumbers,
            onToggleGridLines = onToggleGridLines,
            onTogglePanTool = onTogglePanTool,
            onStrokeBegin = onStrokeBegin,
            onStrokeStep = onStrokeStep,
            onTapCellAction = onTapCellAction,
            onUpdateShapeDraft = onUpdateShapeDraft,
            onCommitShapeDraft = onCommitShapeDraft,
            onDiscardShapeDraft = onDiscardShapeDraft,
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )

          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.96f),
            border = BorderStroke(1.5.dp, BorderSoft),
            shadowElevation = 6.dp,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              EditorToolsPanel(
                activeTool = state.activeTool,
                fillWithEraser = state.fillWithEraser,
                globalReplaceAllFrames = state.globalReplaceAllFrames,
                isMultiFrame = state.frames.size > 1,
                shapeType = state.shapeType,
                shapeFilled = state.shapeFilled,
                onSelectTool = onSelectTool,
                onToggleFillWithEraser = onToggleFillWithEraser,
                onToggleGlobalScope = onToggleGlobalScope,
                onSelectShapeType = onSelectShapeType,
                onToggleShapeFilled = onToggleShapeFilled,
                compact = true,
              )

              EditorPalettePanel(
                palette = state.palette,
                cellCounts = cellCounts,
                activeColorNumber = state.activeColorNumber,
                visibleColors = state.visibleColors,
                onSelectColor = onSelectColor,
                onToggleColorVisibility = onToggleColorVisibility,
                onShowAllColors = onShowAllColors,
                onUncolorAll = onUncolorAll,
                onRequestSwapColors = { showSwapDialog = true },
                onReorderPalette = onReorderPalette,
                onRequestAddColor = { showAddColorDialog = true },
                onRequestEditColor = { editingColorNum = it },
                onRequestMergeColor = { mergingColorNum = it },
                onDeleteUnusedColor = onDeleteUnusedColor,
                compact = true,
              )

              EditorFrameTimeline(
                frameCount = state.frames.size,
                activeFrameIndex = state.activeFrameIndex,
                frameMs = state.frameMs,
                isPlaying = state.isPlayingPreview,
                onSelectFrame = onSelectFrame,
                onTogglePlay = onTogglePlay,
                onCycleSpeed = onCycleSpeed,
                onRequestAddEnd = { insertTargetIndex = state.frames.size },
                onRequestInsertAfter = { insertTargetIndex = state.activeFrameIndex + 1 },
                onDeleteCurrentFrame = onDeleteCurrentFrame,
              )
            }
          }
        }
      }
    }
  }

  if (showAddColorDialog) {
    PaletteColorPickerDialog(
      initialArgb = 0xFF000000.toInt(),
      title = "Add New Color",
      onConfirm = { argb ->
        showAddColorDialog = false
        onAddColor(argb)
      },
      onDismiss = { showAddColorDialog = false },
    )
  }

  val editNum = editingColorNum
  if (editNum != null) {
    PaletteColorPickerDialog(
      initialArgb = state.palette[editNum] ?: 0xFF000000.toInt(),
      title = "Edit Color #$editNum",
      onConfirm = { argb ->
        editingColorNum = null
        onUpdateColorShade(editNum, argb)
      },
      onDismiss = { editingColorNum = null },
    )
  }

  val mergeNum = mergingColorNum
  if (mergeNum != null && state.palette.size > 1) {
    MergeColorDialog(
      sourceColorNum = mergeNum,
      palette = state.palette,
      onSelectTarget = { target ->
        mergingColorNum = null
        onMergeColor(mergeNum, target)
      },
      onDismiss = { mergingColorNum = null },
    )
  }

  if (showSwapDialog && state.palette.size > 1) {
    SwapColorDialog(
      initialSourceColorNum = state.activeColorNumber?.takeIf { it in state.palette },
      palette = state.palette,
      onSwapColors = { colorA, colorB ->
        showSwapDialog = false
        onSwapColors(colorA, colorB)
      },
      onDismiss = { showSwapDialog = false },
    )
  }

  val targetIdx = insertTargetIndex
  if (targetIdx != null) {
    val label =
      if (targetIdx >= state.frames.size) "Add Frame at End"
      else "Insert Frame After F${state.activeFrameIndex + 1}"
    InsertFrameDialog(
      title = label,
      onDuplicateCurrent = {
        insertTargetIndex = null
        onInsertFrame(targetIdx, true)
      },
      onInsertBlank = {
        insertTargetIndex = null
        onInsertFrame(targetIdx, false)
      },
      onDismiss = { insertTargetIndex = null },
    )
  }

  if (showTitleEditDialog) {
    var draftTitle by remember(state.title) { mutableStateOf(state.title) }
    AlertDialog(
      onDismissRequest = { showTitleEditDialog = false },
      title = { Text("Edit Page Title", color = IndigoDeep) },
      text = {
        OutlinedTextField(
          value = draftTitle,
          onValueChange = { if (it.length <= 24) draftTitle = it },
          label = { Text("Page Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onTitleChange(draftTitle.trim().ifEmpty { "My Drawing" })
            showTitleEditDialog = false
          }
        ) {
          Text("Done")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTitleEditDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showDiscardPrompt) {
    DiscardEditorChangesDialog(
      onDiscard = {
        showDiscardPrompt = false
        onDiscardAndExit()
      },
      onKeepEditing = { showDiscardPrompt = false },
    )
  }

  val exportTpl = exportTemplateSnapshot
  if (exportTpl != null) {
    ExportShareDialog(
      template = exportTpl,
      frameIndex = state.activeFrameIndex,
      onNotify = onNotify,
      onDismiss = { exportTemplateSnapshot = null },
      syncState = syncState,
      onVerifyAdminPin = onVerifyAdminPin,
      onSetupAdminPin = onSetupAdminPin,
      onResetAdminPin = onResetAdminPin,
      onPublishToGitHub = onPublishToGitHub,
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = SlateInk,
  )
}
