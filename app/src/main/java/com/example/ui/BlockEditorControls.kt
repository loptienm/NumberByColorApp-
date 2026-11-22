package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.BlockEditorEngine
import com.example.engine.EditorShapeType
import com.example.engine.EditorTool
import com.example.model.contrastInkForArgb
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorToolsPanel(
  activeTool: EditorTool,
  fillWithEraser: Boolean,
  globalReplaceAllFrames: Boolean,
  isMultiFrame: Boolean,
  shapeType: EditorShapeType,
  shapeFilled: Boolean,
  onSelectTool: (EditorTool) -> Unit,
  onToggleFillWithEraser: () -> Unit,
  onToggleGlobalScope: () -> Unit,
  onSelectShapeType: (EditorShapeType) -> Unit,
  onToggleShapeFilled: () -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val tools =
      listOf(
        Triple(EditorTool.DRAW, "Draw", Icons.Filled.Brush),
        Triple(EditorTool.ERASE, "Erase", Icons.Filled.AutoFixHigh),
        Triple(EditorTool.EYEDROPPER, "Pick", Icons.Filled.Colorize),
        Triple(EditorTool.FLOOD_FILL, "Bucket", Icons.Filled.FormatColorFill),
        Triple(EditorTool.GLOBAL_REPLACE, "Replace", Icons.Filled.Layers),
        Triple(EditorTool.SHAPE, "Shapes", Icons.Filled.Category),
      )

    if (compact) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tools.forEach { (tool, label, icon) ->
          ToolPill(
            selected = activeTool == tool,
            label = label,
            icon = icon,
            onClick = { onSelectTool(tool) },
            testTag = "editor_tool_${tool.name.lowercase()}",
          )
        }
      }
    } else {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        tools.forEach { (tool, label, icon) ->
          ToolPill(
            selected = activeTool == tool,
            label = label,
            icon = icon,
            onClick = { onSelectTool(tool) },
            testTag = "editor_tool_${tool.name.lowercase()}",
          )
        }
      }
    }

    if (activeTool == EditorTool.FLOOD_FILL || activeTool == EditorTool.GLOBAL_REPLACE) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = fillWithEraser,
          onClick = onToggleFillWithEraser,
          label = { Text(if (fillWithEraser) "Fill with: Eraser" else "Fill with: Color") },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = CoralError.copy(alpha = 0.16f),
              selectedLabelColor = CoralError,
            ),
          modifier = Modifier.testTag("editor_fill_eraser_chip"),
        )
        if (activeTool == EditorTool.GLOBAL_REPLACE && isMultiFrame) {
          FilterChip(
            selected = globalReplaceAllFrames,
            onClick = onToggleGlobalScope,
            label = { Text(if (globalReplaceAllFrames) "All Frames" else "This Frame") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = IndigoContainer,
                selectedLabelColor = IndigoDeep,
              ),
            modifier = Modifier.testTag("editor_global_scope_chip"),
          )
        }
      }
    }

    if (activeTool == EditorTool.SHAPE) {
      val shapeOptions =
        listOf(
          Triple(EditorShapeType.LINE, "Line", Icons.Filled.ShowChart),
          Triple(EditorShapeType.RECTANGLE, "Box", Icons.Filled.CheckBoxOutlineBlank),
          Triple(EditorShapeType.OVAL, "Oval", Icons.Filled.RadioButtonUnchecked),
          Triple(EditorShapeType.TRIANGLE, "Tri", Icons.Filled.ChangeHistory),
        )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        shapeOptions.forEach { (type, label, icon) ->
          MiniActionChip(
            label = label,
            icon = icon,
            selected = shapeType == type,
            onClick = { onSelectShapeType(type) },
            testTag = "editor_shape_${type.name.lowercase()}",
          )
        }
        if (shapeType != EditorShapeType.LINE) {
          MiniActionChip(
            label = if (shapeFilled) "Filled" else "Outline",
            selected = shapeFilled,
            onClick = onToggleShapeFilled,
            testTag = "editor_shape_fill_toggle",
          )
        }
      }
    }
  }
}

@Composable
private fun ToolPill(
  selected: Boolean,
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (selected) IndigoPrimary else Color(0xFFF1F5F9),
    border = BorderStroke(1.5.dp, if (selected) IndigoPrimary else BorderSoft),
    modifier = Modifier.height(48.dp).testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (selected) Color.White else IndigoDeep,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else IndigoDeep,
        maxLines = 1,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorPalettePanel(
  palette: Map<Int, Int>,
  cellCounts: Map<Int, Int>,
  activeColorNumber: Int?,
  visibleColors: Set<Int>,
  onSelectColor: (Int) -> Unit,
  onToggleColorVisibility: (Int) -> Unit,
  onShowAllColors: () -> Unit,
  onUncolorAll: () -> Unit,
  onRequestSwapColors: () -> Unit,
  onReorderPalette: (List<Int>) -> Unit,
  onRequestAddColor: () -> Unit,
  onRequestEditColor: (Int) -> Unit,
  onRequestMergeColor: (Int) -> Unit,
  onDeleteUnusedColor: (Int) -> Unit,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val sortedKeys = remember(palette) { palette.keys.sorted() }
  val swatchBounds = remember { mutableStateMapOf<Int, Rect>() }
  var draggedColor by remember { mutableStateOf<Int?>(null) }
  var dropTargetColor by remember { mutableStateOf<Int?>(null) }
  var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }

  val currentSortedKeys by rememberUpdatedState(sortedKeys)
  val currentOnReorderPalette by rememberUpdatedState(onReorderPalette)

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Header controls: Color All, Uncolor All, + Add
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val allLit = visibleColors.size == sortedKeys.size && sortedKeys.isNotEmpty()
      val noneLit = visibleColors.isEmpty()

      MiniActionChip(
        label = stringResource(R.string.show_all_colors),
        selected = allLit,
        onClick = onShowAllColors,
        testTag = "editor_show_all_colors",
      )
      MiniActionChip(
        label = stringResource(R.string.uncolor_all),
        selected = noneLit,
        onClick = onUncolorAll,
        testTag = "editor_uncolor_all",
      )
      if (palette.size < BlockEditorEngine.MAX_PALETTE_COLORS) {
        MiniActionChip(
          label = "Add",
          icon = Icons.Filled.Add,
          selected = false,
          onClick = onRequestAddColor,
          testTag = "editor_add_color_button",
        )
      }
    }

    val statusHint =
      if (draggedColor != null) {
        "Dragging #$draggedColor -> drop onto another # to reorder"
      } else {
        "${visibleColors.size}/${sortedKeys.size} lit • Long-press & drag to reorder"
      }
    Text(
      text = statusHint,
      style = MaterialTheme.typography.labelSmall,
      color = if (draggedColor != null) IndigoPrimary else SlateMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    // Draggable FlowRow of numbered swatches with individual Eye toggles
    Box(
      modifier =
        if (compact) Modifier.fillMaxWidth().heightIn(max = 128.dp).verticalScroll(rememberScrollState())
        else Modifier.fillMaxWidth()
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        sortedKeys.forEach { num ->
          val argb = palette[num] ?: 0xFF000000.toInt()
          val count = cellCounts[num] ?: 0
          val isActive = num == activeColorNumber
          val isVisible = num in visibleColors
          val isDragged = draggedColor == num
          val isDropTarget = draggedColor != null && dropTargetColor == num && draggedColor != num

          val borderColor =
            when {
              isDropTarget -> IndigoPrimary
              isActive -> IndigoPrimary
              else -> BorderSoft
            }

          Surface(
            modifier =
              Modifier.height(48.dp)
                .onGloballyPositioned { coords -> swatchBounds[num] = coords.boundsInRoot() }
                .pointerInput(num) {
                  detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                      val rect = swatchBounds[num]
                      draggedColor = num
                      dropTargetColor = num
                      dragPointerRoot = (rect?.topLeft ?: Offset.Zero) + localOffset
                    },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      dragPointerRoot += dragAmount
                      val hit =
                        swatchBounds.entries.firstOrNull { (_, rect) ->
                          rect.contains(dragPointerRoot)
                        }?.key
                      if (hit != null) {
                        dropTargetColor = hit
                      }
                    },
                    onDragEnd = {
                      val from = draggedColor
                      val to = dropTargetColor
                      draggedColor = null
                      dropTargetColor = null
                      if (from != null && to != null && from != to) {
                        val list = currentSortedKeys.toMutableList()
                        val fromIdx = list.indexOf(from)
                        val toIdx = list.indexOf(to)
                        if (fromIdx >= 0 && toIdx >= 0) {
                          val moved = list.removeAt(fromIdx)
                          list.add(toIdx, moved)
                          currentOnReorderPalette(list)
                        }
                      }
                    },
                    onDragCancel = {
                      draggedColor = null
                      dropTargetColor = null
                    },
                  )
                },
            shape = RoundedCornerShape(14.dp),
            color =
              when {
                isDragged -> IndigoContainer
                isDropTarget -> IndigoContainer.copy(alpha = 0.7f)
                isActive -> IndigoContainer.copy(alpha = 0.6f)
                else -> Color.White
              },
            border = BorderStroke(if (isActive || isDropTarget) 2.dp else 1.5.dp, borderColor),
            shadowElevation = if (isDragged) 6.dp else 1.dp,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Row(
                modifier =
                  Modifier.height(48.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .clickable { onSelectColor(num) }
                    .padding(start = 6.dp, end = 6.dp)
                    .testTag("editor_swatch_$num"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Surface(
                  modifier = Modifier.size(28.dp),
                  shape = CircleShape,
                  color = Color(argb),
                  border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = num.toString(),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = contrastInkForArgb(argb),
                    )
                  }
                }

                Text(
                  text = count.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (count == 0) SlateMuted.copy(alpha = 0.65f) else SlateInk,
                )
              }

              Box(
                modifier =
                  Modifier.size(40.dp)
                    .clip(CircleShape)
                    .clickable { onToggleColorVisibility(num) }
                    .semantics {
                      contentDescription = if (isVisible) "Hide color $num" else "Show color $num"
                    }
                    .testTag("editor_eye_$num"),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                  contentDescription = if (isVisible) "Hide color $num" else "Show color $num",
                  tint = if (isVisible) IndigoPrimary else SlateMuted.copy(alpha = 0.55f),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }

    // Color actions: Edit Shade, Swap, Merge, Remove Unused
    val selectedNum = activeColorNumber?.takeIf { it in palette }
    if (selectedNum != null || palette.size > 1) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (selectedNum != null) {
          MiniActionChip(
            label = "Edit #$selectedNum",
            icon = Icons.Filled.Edit,
            selected = false,
            onClick = { onRequestEditColor(selectedNum) },
            testTag = "editor_edit_color_button",
          )
        }
        if (palette.size > 1) {
          MiniActionChip(
            label = stringResource(R.string.swap_colors),
            icon = Icons.Filled.SwapHoriz,
            selected = false,
            onClick = onRequestSwapColors,
            testTag = "editor_swap_button",
          )
          if (selectedNum != null) {
            MiniActionChip(
              label = "Merge #$selectedNum",
              icon = Icons.AutoMirrored.Filled.MergeType,
              selected = false,
              onClick = { onRequestMergeColor(selectedNum) },
              testTag = "editor_merge_color_button",
            )
            val activeCount = cellCounts[selectedNum] ?: 0
            if (activeCount == 0) {
              MiniActionChip(
                label = "Remove Unused",
                icon = Icons.Filled.DeleteOutline,
                selected = false,
                onClick = { onDeleteUnusedColor(selectedNum) },
                testTag = "editor_delete_unused_color_button",
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MiniActionChip(
  label: String,
  icon: ImageVector? = null,
  selected: Boolean,
  selectedColor: Color = IndigoPrimary,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (selected) selectedColor else Color(0xFFF1F5F9),
    border = BorderStroke(1.dp, if (selected) selectedColor else BorderSoft),
    modifier = Modifier.height(42.dp).testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) Color.White else IndigoDeep,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else IndigoDeep,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun EditorFrameTimeline(
  frameCount: Int,
  activeFrameIndex: Int,
  frameMs: Int,
  isPlaying: Boolean,
  onSelectFrame: (Int) -> Unit,
  onTogglePlay: () -> Unit,
  onCycleSpeed: () -> Unit,
  onRequestAddEnd: () -> Unit,
  onRequestInsertAfter: () -> Unit,
  onDeleteCurrentFrame: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (frameCount > 1) {
        Surface(
          onClick = onTogglePlay,
          shape = CircleShape,
          color = if (isPlaying) IndigoPrimary else IndigoContainer,
          modifier = Modifier.height(42.dp).testTag("editor_play_frames_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (isPlaying) "Pause preview" else "Play preview",
              tint = if (isPlaying) Color.White else IndigoDeep,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (isPlaying) "Stop" else "Play",
              style = MaterialTheme.typography.labelMedium,
              color = if (isPlaying) Color.White else IndigoDeep,
            )
          }
        }

        MiniActionChip(
          label = "${frameMs}ms",
          selected = false,
          onClick = onCycleSpeed,
          testTag = "editor_frame_speed_button",
        )
      } else {
        Text(
          text = "Frames (1)",
          style = MaterialTheme.typography.labelLarge,
          color = IndigoDeep,
        )
      }

      if (frameCount < BlockEditorEngine.MAX_FRAMES) {
        MiniActionChip(
          label = stringResource(R.string.add_frame),
          icon = Icons.Filled.Add,
          selected = false,
          onClick = onRequestAddEnd,
          testTag = "editor_add_frame_end",
        )
        MiniActionChip(
          label = stringResource(R.string.insert_frame_between),
          icon = Icons.Filled.Add,
          selected = false,
          onClick = onRequestInsertAfter,
          testTag = "editor_insert_frame_after",
        )
      }

      if (frameCount > 1) {
        IconButton(
          onClick = onDeleteCurrentFrame,
          modifier = Modifier.size(48.dp).testTag("editor_delete_frame_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = stringResource(R.string.delete_frame),
            tint = CoralError,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }

    if (frameCount > 1) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
      ) {
        itemsIndexed(List(frameCount) { it }) { idx, _ ->
          val isCurrent = idx == activeFrameIndex
          Surface(
            onClick = { onSelectFrame(idx) },
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) IndigoPrimary else Color(0xFFF1F5F9),
            border = BorderStroke(1.5.dp, if (isCurrent) IndigoPrimary else BorderSoft),
            modifier = Modifier.height(42.dp).testTag("editor_frame_chip_$idx"),
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 12.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "F${idx + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) Color.White else IndigoDeep,
              )
            }
          }
        }
      }
    }
  }
}
