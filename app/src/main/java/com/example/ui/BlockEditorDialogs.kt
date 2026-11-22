package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.argbToHex
import com.example.model.contrastInkForArgb
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

private val QUICK_COLOR_PRESETS =
  listOf(
    0xFF000000.toInt(), // Pure Black
    0xFF1E293B.toInt(), // Charcoal
    0xFF64748B.toInt(), // Slate Gray
    0xFFFFFFFF.toInt(), // Pure White
    0xFFFEF3C7.toInt(), // Cream
    0xFFEF4444.toInt(), // Red
    0xFFF97316.toInt(), // Orange
    0xFFFACC15.toInt(), // Yellow
    0xFF22C55E.toInt(), // Green
    0xFF14B8A6.toInt(), // Teal
    0xFF38BDF8.toInt(), // Sky Blue
    0xFF2563EB.toInt(), // Royal Blue
    0xFF8B5CF6.toInt(), // Violet
    0xFFEC4899.toInt(), // Pink
    0xFFFCA5A5.toInt(), // Peach / Blush
    0xFF78350F.toInt(), // Brown
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteColorPickerDialog(
  initialArgb: Int,
  title: String,
  onConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val initialHsv = remember(initialArgb) {
    FloatArray(3).also { AndroidColor.colorToHSV(initialArgb, it) }
  }
  var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv[0]) }
  var sat by remember(initialArgb) { mutableFloatStateOf(initialHsv[1]) }
  var value by remember(initialArgb) { mutableFloatStateOf(initialHsv[2]) }

  val currentArgb =
    remember(hue, sat, value) {
      AndroidColor.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          modifier = Modifier.size(36.dp),
          shape = CircleShape,
          color = Color(currentArgb),
          border = BorderStroke(2.dp, BorderSoft),
          shadowElevation = 2.dp,
        ) {}
        Column {
          Text(text = title, style = MaterialTheme.typography.titleLarge, color = IndigoDeep)
          Text(
            text = argbToHex(currentArgb).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = SlateMuted,
          )
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Quick Shades",
          style = MaterialTheme.typography.labelLarge,
          color = SlateInk,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          QUICK_COLOR_PRESETS.forEach { preset ->
            val isMatch = (preset and 0xFFFFFF) == (currentArgb and 0xFFFFFF)
            Surface(
              modifier =
                Modifier.size(32.dp)
                  .clip(CircleShape)
                  .clickable {
                    val hsv = FloatArray(3)
                    AndroidColor.colorToHSV(preset, hsv)
                    hue = hsv[0]
                    sat = hsv[1]
                    value = hsv[2]
                  },
              shape = CircleShape,
              color = Color(preset),
              border =
                BorderStroke(
                  width = if (isMatch) 2.5.dp else 1.5.dp,
                  color = if (isMatch) IndigoPrimary else Color(0xFFCBD5E1),
                ),
            ) {}
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        ColorChannelSlider(
          label = "Hue",
          value = hue,
          valueRange = 0f..360f,
          onValueChange = { hue = it },
          thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
        )
        ColorChannelSlider(
          label = "Saturation",
          value = sat,
          valueRange = 0f..1f,
          onValueChange = { sat = it },
          thumbColor = Color(currentArgb),
        )
        ColorChannelSlider(
          label = "Brightness",
          value = value,
          valueRange = 0f..1f,
          onValueChange = { value = it },
          thumbColor = Color(currentArgb),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(currentArgb) },
        modifier = Modifier.testTag("editor_color_picker_confirm"),
      ) {
        Text("Use Color")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun ColorChannelSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  thumbColor: Color,
) {
  Column {
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = SlateInk)
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors =
        SliderDefaults.colors(
          thumbColor = thumbColor,
          activeTrackColor = IndigoPrimary,
        ),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergeColorDialog(
  sourceColorNum: Int,
  palette: Map<Int, Int>,
  onSelectTarget: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val sourceArgb = palette[sourceColorNum] ?: 0xFF000000.toInt()
  val candidates = palette.keys.sorted().filter { it != sourceColorNum }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Merge Color #$sourceColorNum", color = IndigoDeep) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = Color(sourceArgb),
            border = BorderStroke(1.5.dp, BorderSoft),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = sourceColorNum.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = contrastInkForArgb(sourceArgb),
              )
            }
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = IndigoPrimary,
          )
          Text(
            text = "Pick which color replaces all #$sourceColorNum blocks across every frame:",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateInk,
            modifier = Modifier.weight(1f),
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          candidates.forEach { targetNum ->
            val argb = palette[targetNum] ?: 0xFF000000.toInt()
            Surface(
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .clickable { onSelectTarget(targetNum) }
                  .testTag("merge_target_$targetNum"),
              shape = CircleShape,
              color = Color(argb),
              border = BorderStroke(2.dp, Color(0xFFCBD5E1)),
              shadowElevation = 2.dp,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = targetNum.toString(),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = contrastInkForArgb(argb),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwapColorDialog(
  initialSourceColorNum: Int?,
  palette: Map<Int, Int>,
  onSwapColors: (Int, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var firstColor by remember(initialSourceColorNum) {
    mutableStateOf(initialSourceColorNum?.takeIf { it in palette })
  }
  val sortedKeys = remember(palette) { palette.keys.sorted() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      val first = firstColor
      Text(
        text = if (first != null) "Swap Color #$first" else "Swap Two Colors",
        color = IndigoDeep,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val first = firstColor
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (first != null) {
            val firstArgb = palette[first] ?: 0xFF000000.toInt()
            Surface(
              modifier = Modifier.size(34.dp),
              shape = CircleShape,
              color = Color(firstArgb),
              border = BorderStroke(1.5.dp, IndigoPrimary),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = first.toString(),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = contrastInkForArgb(firstArgb),
                )
              }
            }
          } else {
            Surface(
              modifier = Modifier.size(34.dp),
              shape = CircleShape,
              color = IndigoContainer,
              border = BorderStroke(1.5.dp, IndigoPrimary),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = "?",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = IndigoDeep,
                )
              }
            }
          }

          Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = IndigoPrimary,
          )

          Text(
            text =
              if (first != null) {
                "Pick which color swaps number positions with #$first:"
              } else {
                "Tap the first color, then the second color to swap positions:"
              },
            style = MaterialTheme.typography.bodyMedium,
            color = SlateInk,
            modifier = Modifier.weight(1f),
          )
        }

        val candidates = if (first != null) sortedKeys.filter { it != first } else sortedKeys
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          candidates.forEach { colorNum ->
            val argb = palette[colorNum] ?: 0xFF000000.toInt()
            Surface(
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .clickable {
                    val curFirst = firstColor
                    if (curFirst == null) {
                      firstColor = colorNum
                    } else if (curFirst != colorNum) {
                      onSwapColors(curFirst, colorNum)
                    }
                  }
                  .testTag("swap_target_$colorNum"),
              shape = CircleShape,
              color = Color(argb),
              border = BorderStroke(2.dp, Color(0xFFCBD5E1)),
              shadowElevation = 2.dp,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = colorNum.toString(),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = contrastInkForArgb(argb),
                )
              }
            }
          }
        }

        if (first != null && palette.size >= 3) {
          TextButton(
            onClick = { firstColor = null },
            modifier = Modifier.testTag("swap_pick_different_first"),
          ) {
            Text("Pick two different colors instead")
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

private enum class BlankAspect(val label: String) {
  SQUARE("Square"),
  TALL("Tall 3:4"),
  WIDE("Wide 4:3"),
}

private val BLANK_SIZE_PRESETS = listOf(16, 24, 32, 48, 64)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBlankPageDialog(
  initialTitle: String,
  onCreateBlankPage: (title: String, width: Int, height: Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember(initialTitle) { mutableStateOf(initialTitle) }
  var baseSize by remember { mutableIntStateOf(24) }
  var aspect by remember { mutableStateOf(BlankAspect.SQUARE) }

  val (gridW, gridH) =
    when (aspect) {
      BlankAspect.SQUARE -> baseSize to baseSize
      BlankAspect.TALL -> ((baseSize * 3) / 4).coerceAtLeast(8) to baseSize
      BlankAspect.WIDE -> baseSize to ((baseSize * 3) / 4).coerceAtLeast(8)
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Blank Page", color = IndigoDeep) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 24) title = it },
          label = { Text("Page Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("blank_title_input"),
          shape = RoundedCornerShape(14.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "Grid Size", style = MaterialTheme.typography.labelLarge, color = SlateInk)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            BLANK_SIZE_PRESETS.forEach { size ->
              FilterChip(
                selected = baseSize == size,
                onClick = { baseSize = size },
                label = { Text("$size") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoContainer,
                    selectedLabelColor = IndigoDeep,
                  ),
                modifier = Modifier.testTag("blank_size_$size"),
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(text = "Canvas Shape", style = MaterialTheme.typography.labelLarge, color = SlateInk)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            BlankAspect.entries.forEach { option ->
              FilterChip(
                selected = aspect == option,
                onClick = { aspect = option },
                label = { Text(option.label) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IndigoContainer,
                    selectedLabelColor = IndigoDeep,
                  ),
                modifier = Modifier.testTag("blank_aspect_${option.name.lowercase()}"),
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = IndigoContainer.copy(alpha = 0.65f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Canvas: $gridW × $gridH blocks • 10 starter colors",
            style = MaterialTheme.typography.labelLarge,
            color = IndigoDeep,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreateBlankPage(title.trim().ifEmpty { "My Drawing" }, gridW, gridH) },
        modifier = Modifier.testTag("blank_create_confirm"),
      ) {
        Text("Start Drawing")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun InsertFrameDialog(
  title: String,
  onDuplicateCurrent: () -> Unit,
  onInsertBlank: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, color = IndigoDeep) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Start the new frame by copying the current frame or with a clean blank canvas?",
          style = MaterialTheme.typography.bodyMedium,
          color = SlateInk,
        )
        Button(
          onClick = onDuplicateCurrent,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("frame_insert_copy"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Copy Current Frame")
        }
        OutlinedButton(
          onClick = onInsertBlank,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("frame_insert_blank"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Blank Frame")
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun DiscardEditorChangesDialog(
  onDiscard: () -> Unit,
  onKeepEditing: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onKeepEditing,
    title = { Text("Discard Edits?", color = IndigoDeep) },
    text = {
      Text(
        text = "You have unsaved block or palette changes. Go back without saving?",
        style = MaterialTheme.typography.bodyMedium,
        color = SlateInk,
      )
    },
    confirmButton = {
      TextButton(
        onClick = onDiscard,
        colors = ButtonDefaults.textButtonColors(contentColor = CoralError),
      ) {
        Text("Discard")
      }
    },
    dismissButton = {
      TextButton(onClick = onKeepEditing) { Text("Keep Editing") }
    },
  )
}
