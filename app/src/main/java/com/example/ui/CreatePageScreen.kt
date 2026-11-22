package com.example.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PictureTemplate
import com.example.model.contrastInkForArgb
import com.example.ui.components.PixelPreviewCanvas
import com.example.ui.components.PreviewRenderMode
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SkyTint
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.delay

private val GRID_DETAIL_OPTIONS =
  listOf(
    0 to "Auto",
    24 to "Low",
    40 to "Med",
    64 to "High",
  )

private val COLOR_COUNT_STOPS = listOf(0) + (4..20).toList()

private enum class PreviewTab {
  COLOR,
  NUMBERED_GRID,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePageScreen(
  state: CreatePageUiState,
  onBack: () -> Unit,
  onImagePicked: (Uri) -> Unit,
  onTitleChange: (String) -> Unit,
  onGridSizeChange: (Int) -> Unit,
  onColorCountChange: (Int) -> Unit,
  onTrimBackgroundChange: (Boolean) -> Unit,
  onCreateBlankPage: (title: String, width: Int, height: Int) -> Unit,
  onEditBlocks: () -> Unit,
  onSaveAndColor: () -> Unit,
  onNotify: (String) -> Unit = {},
  syncState: SyncSettingsUiState = SyncSettingsUiState(),
  onVerifyAdminPin: (String) -> Boolean = { false },
  onSetupAdminPin: (String) -> Boolean = { false },
  onResetAdminPin: () -> Unit = {},
  onPublishToGitHub: ((PictureTemplate, String, (Boolean, String) -> Unit) -> Unit)? = null,
) {
  BackHandler { onBack() }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
      if (uri != null) {
        onImagePicked(uri)
      }
    }

  val openDocumentLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri != null) {
        onImagePicked(uri)
      }
    }

  val launchImportFile = {
    openDocumentLauncher.launch(
      arrayOf("application/json", "text/plain", "image/*", "application/octet-stream")
    )
  }

  var previewTab by remember { mutableStateOf(PreviewTab.COLOR) }
  var showBlankPageDialog by remember { mutableStateOf(false) }
  var showExportDialog by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
  ) {
    // Header
    Surface(color = Color.White.copy(alpha = 0.88f), shadowElevation = 3.dp) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("create_back_button"),
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
            text = stringResource(R.string.create_page_title),
            style = MaterialTheme.typography.headlineSmall,
            color = IndigoDeep,
          )
          Text(
            text = "Turn any photo or drawing into a coloring page",
            style = MaterialTheme.typography.bodyMedium,
            color = IndigoPrimary,
          )
        }
        val canExport =
          state.previewTemplate != null &&
            !state.isProcessing &&
            state.previewTemplate.totalPaintableCells() > 0
        if (canExport) {
          IconButton(
            onClick = { showExportDialog = true },
            modifier = Modifier.size(48.dp).testTag("create_export_button"),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = IndigoContainer,
                contentColor = IndigoPrimary,
              ),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = stringResource(R.string.export_share),
            )
          }
        }
      }
    }

    BoxWithConstraints(
      modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
    ) {
      val isWide = maxWidth >= 680.dp

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(18.dp),
          horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          PreviewPane(
            state = state,
            previewTab = previewTab,
            onTabChange = { previewTab = it },
            onPickImage = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onOpenBlankDialog = { showBlankPageDialog = true },
            onImportPageFile = launchImportFile,
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
          ControlsPane(
            state = state,
            onPickImage = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onOpenBlankDialog = { showBlankPageDialog = true },
            onImportPageFile = launchImportFile,
            onTitleChange = onTitleChange,
            onGridSizeChange = onGridSizeChange,
            onColorCountChange = onColorCountChange,
            onTrimBackgroundChange = onTrimBackgroundChange,
            onEditBlocks = onEditBlocks,
            onSaveAndColor = onSaveAndColor,
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
          )
        }
      } else {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          PreviewPane(
            state = state,
            previewTab = previewTab,
            onTabChange = { previewTab = it },
            onPickImage = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onOpenBlankDialog = { showBlankPageDialog = true },
            onImportPageFile = launchImportFile,
            modifier = Modifier.fillMaxWidth(),
          )
          ControlsPane(
            state = state,
            onPickImage = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onOpenBlankDialog = { showBlankPageDialog = true },
            onImportPageFile = launchImportFile,
            onTitleChange = onTitleChange,
            onGridSizeChange = onGridSizeChange,
            onColorCountChange = onColorCountChange,
            onTrimBackgroundChange = onTrimBackgroundChange,
            onEditBlocks = onEditBlocks,
            onSaveAndColor = onSaveAndColor,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }

  val exportTpl = state.previewTemplate
  if (showExportDialog && exportTpl != null) {
    ExportShareDialog(
      template = exportTpl.copy(title = state.title.trim().ifEmpty { exportTpl.title }),
      frameIndex = 0,
      onNotify = onNotify,
      onDismiss = { showExportDialog = false },
      syncState = syncState,
      onVerifyAdminPin = onVerifyAdminPin,
      onSetupAdminPin = onSetupAdminPin,
      onResetAdminPin = onResetAdminPin,
      onPublishToGitHub = onPublishToGitHub,
    )
  }

  if (showBlankPageDialog) {
    CreateBlankPageDialog(
      initialTitle = state.title,
      onCreateBlankPage = { title, width, height ->
        showBlankPageDialog = false
        onCreateBlankPage(title, width, height)
      },
      onDismiss = { showBlankPageDialog = false },
    )
  }
}

@Composable
private fun PreviewPane(
  state: CreatePageUiState,
  previewTab: PreviewTab,
  onTabChange: (PreviewTab) -> Unit,
  onPickImage: () -> Unit,
  onOpenBlankDialog: () -> Unit,
  onImportPageFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = Color.White,
    border = BorderStroke(2.dp, BorderSoft),
    shadowElevation = 4.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.previewTemplate != null) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = previewTab == PreviewTab.COLOR,
            onClick = { onTabChange(PreviewTab.COLOR) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
          ) {
            Text("Color Preview")
          }
          SegmentedButton(
            selected = previewTab == PreviewTab.NUMBERED_GRID,
            onClick = { onTabChange(PreviewTab.NUMBERED_GRID) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.GridOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
          ) {
            Text("Number Grid")
          }
        }
      }

      val isEmptyPreview = state.previewTemplate == null && !state.isProcessing
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .then(if (isEmptyPreview) Modifier else Modifier.aspectRatio(1f))
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center,
      ) {
        val previewTpl = state.previewTemplate
        var gifFrameIndex by remember(previewTpl?.id, previewTpl?.frames?.size) { mutableIntStateOf(0) }
        LaunchedEffect(previewTpl?.id, previewTpl?.frames?.size, previewTab) {
          if (previewTpl != null && previewTpl.frames.size > 1 && previewTab == PreviewTab.COLOR) {
            val stepMs = previewTpl.frameMs.toLong().coerceIn(80L, 1200L)
            while (true) {
              delay(stepMs)
              gifFrameIndex = (gifFrameIndex + 1) % previewTpl.frames.size
            }
          } else {
            gifFrameIndex = 0
          }
        }

        when {
          state.isProcessing -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = IndigoPrimary)
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Converting into coloring squares...",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateMuted,
              )
            }
          }
          previewTpl != null -> {
            if (previewTab == PreviewTab.COLOR) {
              PixelPreviewCanvas(
                template = previewTpl,
                mode = PreviewRenderMode.FULL_COLOR,
                frameIndex = if (previewTpl.frames.size > 1) gifFrameIndex else null,
                modifier = Modifier.fillMaxSize().padding(10.dp),
              )
            } else {
              NumberedGridPreviewCanvas(
                template = previewTpl,
                modifier = Modifier.fillMaxSize().padding(10.dp),
              )
            }
          }
          else -> {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = IndigoPrimary,
                modifier = Modifier.size(56.dp),
              )
              Text(
                text = "Import pixel art or a GIF, or start with a blank grid to draw your own",
                style = MaterialTheme.typography.titleMedium,
                color = IndigoDeep,
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = onPickImage,
                modifier = Modifier.height(50.dp).testTag("create_pick_image_empty_button"),
                shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.pick_image))
              }
              OutlinedButton(
                onClick = onOpenBlankDialog,
                modifier = Modifier.height(50.dp).testTag("create_blank_page_empty_button"),
                shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.Brush, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_blank_page))
              }
              TextButton(
                onClick = onImportPageFile,
                modifier = Modifier.height(48.dp).testTag("create_import_file_empty_button"),
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Saved File (.json / Drive)")
              }
            }
          }
        }
      }

      if (state.errorMessage != null) {
        Text(
          text = state.errorMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = CoralError,
          textAlign = TextAlign.Center,
        )
      }

      if (state.previewTemplate != null) {
        val tpl = state.previewTemplate
        val pxSize = state.detectedPixelSizePx
        val pixelInfo =
          if (pxSize != null) " • ~${String.format(Locale.US, "%.1f", pxSize)} px blocks" else ""
        val gifInfo = if (tpl.frames.size > 1) " • ${tpl.frames.size} GIF frames" else ""
        Text(
          text =
            "${tpl.gridWidth} × ${tpl.gridHeight} grid$pixelInfo$gifInfo • ${tpl.palette.size} colors • ${tpl.totalPaintableCells()} squares",
          style = MaterialTheme.typography.bodyMedium,
          color = SlateMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("create_preview_summary"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsPane(
  state: CreatePageUiState,
  onPickImage: () -> Unit,
  onOpenBlankDialog: () -> Unit,
  onImportPageFile: () -> Unit,
  onTitleChange: (String) -> Unit,
  onGridSizeChange: (Int) -> Unit,
  onColorCountChange: (Int) -> Unit,
  onTrimBackgroundChange: (Boolean) -> Unit,
  onEditBlocks: () -> Unit,
  onSaveAndColor: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = Color.White,
    border = BorderStroke(2.dp, BorderSoft),
    shadowElevation = 4.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onPickImage,
          modifier = Modifier.weight(1f).height(50.dp).testTag("create_pick_image_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              if (state.sourceBitmap == null) "Pick Image"
              else "New Image",
            maxLines = 1,
          )
        }
        OutlinedButton(
          onClick = onOpenBlankDialog,
          modifier = Modifier.weight(1f).height(50.dp).testTag("create_blank_page_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.blank_page),
            maxLines = 1,
          )
        }
      }

      OutlinedButton(
        onClick = onImportPageFile,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_import_file_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Import Page File or Drive Image")
      }

      OutlinedTextField(
        value = state.title,
        onValueChange = { if (it.length <= 24) onTitleChange(it) },
        label = { Text("Page Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("create_title_input"),
        shape = RoundedCornerShape(14.dp),
      )

      val isBlankMode = state.sourceBitmap == null && state.previewTemplate != null
      if (isBlankMode) {
        val tpl = state.previewTemplate!!
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = IndigoContainer.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Custom Canvas: ${tpl.gridWidth} × ${tpl.gridHeight} blocks • Edit in Block Editor anytime",
            style = MaterialTheme.typography.bodyMedium,
            color = IndigoDeep,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Grid Detail",
            style = MaterialTheme.typography.titleMedium,
            color = SlateInk,
          )
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GRID_DETAIL_OPTIONS.forEachIndexed { idx, (dim, label) ->
              SegmentedButton(
                selected = state.gridSize == dim,
                onClick = { onGridSizeChange(dim) },
                shape =
                  SegmentedButtonDefaults.itemShape(
                    index = idx,
                    count = GRID_DETAIL_OPTIONS.size,
                  ),
                modifier = Modifier.testTag("create_grid_$dim"),
              ) {
                Text(label, maxLines = 1)
              }
            }
          }
          Text(
            text = "Auto matches pixel art 1:1; Low / Med / High resize drawings",
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted,
          )
        }

        Column {
          val colorStopIdx =
            COLOR_COUNT_STOPS.indexOfFirst { it == state.colorCount }.let { idx ->
              if (idx >= 0) idx else 0
            }
          val colorBadgeText =
            if (state.colorCount <= 0) {
              val found = state.previewTemplate?.palette?.size
              if (found != null) "Auto ($found)" else "Auto"
            } else {
              "${state.colorCount} colors"
            }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Max Colors",
              style = MaterialTheme.typography.titleMedium,
              color = SlateInk,
            )
            Surface(shape = CircleShape, color = IndigoContainer) {
              Text(
                text = colorBadgeText,
                style = MaterialTheme.typography.labelLarge,
                color = IndigoDeep,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              )
            }
          }
          Slider(
            value = colorStopIdx.toFloat(),
            onValueChange = { raw ->
              val stop = COLOR_COUNT_STOPS[raw.roundToInt().coerceIn(0, COLOR_COUNT_STOPS.lastIndex)]
              if (stop != state.colorCount) {
                onColorCountChange(stop)
              }
            },
            valueRange = 0f..COLOR_COUNT_STOPS.lastIndex.toFloat(),
            steps = COLOR_COUNT_STOPS.size - 2,
            modifier = Modifier.fillMaxWidth().testTag("create_colors_slider"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Trim light background",
              style = MaterialTheme.typography.titleMedium,
              color = SlateInk,
            )
            Text(
              text = "Keeps blank white/clear edges unpainted",
              style = MaterialTheme.typography.bodyMedium,
              color = SlateMuted,
            )
          }
          Switch(
            checked = state.trimBackground,
            onCheckedChange = onTrimBackgroundChange,
            modifier = Modifier.testTag("create_trim_switch"),
          )
        }
      }

      val previewTpl = state.previewTemplate
      if (previewTpl != null && previewTpl.palette.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Generated Palette",
            style = MaterialTheme.typography.labelLarge,
            color = SlateInk,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            previewTpl.palette.keys.sorted().forEach { num ->
              val argb = previewTpl.palette[num] ?: 0xFF000000.toInt()
              Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = Color(argb),
                border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = num.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contrastInkForArgb(argb),
                  )
                }
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = onEditBlocks,
        enabled = state.previewTemplate != null && !state.isProcessing,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_edit_blocks_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Edit Blocks & Color Order",
          style = MaterialTheme.typography.titleSmall,
        )
      }

      val hasPaintableBlocks = (state.previewTemplate?.totalPaintableCells() ?: 0) > 0
      if (state.previewTemplate != null && !hasPaintableBlocks) {
        Text(
          text = "Draw at least one block in the Block Editor before saving to play.",
          style = MaterialTheme.typography.bodySmall,
          color = SlateMuted,
        )
      }

      Button(
        onClick = onSaveAndColor,
        enabled = state.previewTemplate != null && !state.isProcessing && hasPaintableBlocks,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("create_save_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
      ) {
        Text(
          text = stringResource(R.string.save_and_color),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

@Composable
private fun NumberedGridPreviewCanvas(
  template: PictureTemplate,
  modifier: Modifier = Modifier,
) {
  val numberPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Canvas(modifier = modifier) {
    val maxDim = max(template.gridWidth, template.gridHeight).coerceAtLeast(1)
    val cellSize = minOf(size.width, size.height) / maxDim
    val offsetX = (size.width - template.gridWidth * cellSize) / 2f
    val offsetY = (size.height - template.gridHeight * cellSize) / 2f
    val showNumbers = cellSize >= 11f
    if (showNumbers) {
      numberPaint.textSize = (cellSize * 0.52f).coerceIn(7f, 22f)
    }
    val vCenter = if (showNumbers) -(numberPaint.descent() + numberPaint.ascent()) / 2f else 0f

    for (r in 0 until template.gridHeight) {
      for (c in 0 until template.gridWidth) {
        val target = template.cellNumber(r, c)
        if (target <= 0) continue
        val x = offsetX + c * cellSize
        val y = offsetY + r * cellSize

        drawRect(
          color = Color.White,
          topLeft = Offset(x, y),
          size = Size(cellSize, cellSize),
        )
        drawRect(
          color = Color(0xFFCBD5E1),
          topLeft = Offset(x, y),
          size = Size(cellSize, cellSize),
          style = Stroke(width = 0.9f),
        )
        if (showNumbers) {
          numberPaint.color = 0xFF334155.toInt()
          drawContext.canvas.nativeCanvas.drawText(
            target.toString(),
            x + cellSize / 2f,
            y + cellSize / 2f + vCenter,
            numberPaint,
          )
        }
      }
    }
  }
}
