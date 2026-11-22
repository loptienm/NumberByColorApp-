package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.LevelExportEngine
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.PracticeTopic
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

@Composable
fun CustomLevelsAdminSection(
  levels: List<CustomEducationLevel>,
  customQuestions: List<CustomQuestion>,
  syncState: SyncSettingsUiState,
  onCreateNew: () -> Unit,
  onEditLevel: (CustomEducationLevel) -> Unit,
  onDeleteOrRevertLevel: (String) -> Unit,
  onPublishLevel: (CustomEducationLevel) -> Unit,
  onPublishAllLevels: (List<CustomEducationLevel>) -> Unit,
) {
  val publishableLevels = remember(levels) { levels.filter { it.isCustom } }
  val upToDateLevels = remember(levels) { levels.filter { !it.isCustom && it.isSyncedOfficial } }

  fun levelSummaryText(lvl: CustomEducationLevel): String {
    val qCount = customQuestions.count { lvl.id in it.customLevelIds }
    val topicLabels = lvl.topics.joinToString(", ") { it.label }
    return buildList {
      if (topicLabels.isNotEmpty()) add(topicLabels)
      if (qCount > 0) add("$qCount custom question(s)")
      if (topicLabels.isEmpty() && qCount == 0) add("Add topics or assign custom questions")
    }.joinToString(" • ")
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = stringResource(R.string.custom_levels_title),
      style = MaterialTheme.typography.titleLarge,
      color = IndigoDeep,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onCreateNew,
        modifier = Modifier.weight(1f).height(48.dp).testTag("admin_new_level_button"),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.new_level),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (publishableLevels.isNotEmpty()) {
        Button(
          onClick = { onPublishAllLevels(publishableLevels) },
          enabled = !syncState.isPublishing && !syncState.isSyncing,
          modifier =
            Modifier.weight(1f).height(48.dp).testTag("admin_publish_all_levels_button"),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
          if (syncState.isPublishingAll && syncState.publishingLevelId != null) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = Color.White,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = syncState.publishProgressText.ifBlank { "Publishing…" },
              maxLines = 1,
              softWrap = false,
            )
          } else {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Publish (${publishableLevels.size})",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (publishableLevels.isEmpty() && upToDateLevels.isEmpty()) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderSoft),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text =
            "No custom education levels yet. Tap \"New Level\" to build a focused practice target like \"Targeted Spanish\" or \"Younger Spelling\", select it in Profile Settings, and sync it to GitHub.",
          style = MaterialTheme.typography.bodyMedium,
          color = SlateMuted,
          modifier = Modifier.padding(14.dp),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        publishableLevels.forEach { lvl ->
          val cleanId =
            if (lvl.id.startsWith("lvl_")) {
              LevelExportEngine.sanitizeLevelId(lvl.name).ifBlank { lvl.id }
            } else {
              lvl.id
            }
          val isThisPublishing =
            syncState.isPublishing &&
              (syncState.publishingLevelId == lvl.id || syncState.publishingLevelId == cleanId)
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.fillMaxWidth().testTag("admin_custom_level_${lvl.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = lvl.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (lvl.description.isNotBlank()) {
                  Text(
                    text = lvl.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = levelSummaryText(lvl),
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              IconButton(
                onClick = { onEditLevel(lvl) },
                modifier = Modifier.size(40.dp).testTag("admin_edit_level_${lvl.id}"),
              ) {
                Icon(
                  Icons.Filled.Edit,
                  contentDescription = "Edit education level",
                  tint = IndigoPrimary,
                  modifier = Modifier.size(18.dp),
                )
              }

              IconButton(
                onClick = { onDeleteOrRevertLevel(lvl.id) },
                modifier = Modifier.size(40.dp).testTag("admin_delete_level_${lvl.id}"),
              ) {
                Icon(
                  Icons.Filled.Delete,
                  contentDescription = "Delete education level",
                  tint = CoralError,
                  modifier = Modifier.size(18.dp),
                )
              }

              Button(
                onClick = { onPublishLevel(lvl) },
                enabled = !syncState.isPublishing && !syncState.isSyncing,
                modifier = Modifier.height(40.dp).testTag("admin_publish_level_${lvl.id}"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
              ) {
                if (isThisPublishing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                } else {
                  Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Publish", maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        upToDateLevels.forEach { lvl ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = EmeraldSuccess.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("admin_uptodate_level_${lvl.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = lvl.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (lvl.description.isNotBlank()) {
                  Text(
                    text = lvl.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = levelSummaryText(lvl),
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { onEditLevel(lvl) },
                modifier = Modifier.size(40.dp).testTag("admin_edit_synced_level_${lvl.id}"),
              ) {
                Icon(
                  Icons.Filled.Edit,
                  contentDescription = "Edit synced level",
                  tint = IndigoPrimary,
                  modifier = Modifier.size(18.dp),
                )
              }
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = EmeraldSuccess.copy(alpha = 0.16f),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "Up to date",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSuccess,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomLevelEditorDialog(
  initialLevel: CustomEducationLevel?,
  onSave: (level: CustomEducationLevel, editingExisting: CustomEducationLevel?) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember(initialLevel) { mutableStateOf(initialLevel?.name.orEmpty()) }
  var description by remember(initialLevel) { mutableStateOf(initialLevel?.description.orEmpty()) }
  var selectedTopics by remember(initialLevel) {
    mutableStateOf(initialLevel?.topics ?: emptySet())
  }
  var validationError by remember { mutableStateOf<String?>(null) }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val descFocus = remember { FocusRequester() }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp)
          .widthIn(max = 500.dp)
          .testTag("custom_level_editor_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      border = BorderStroke(3.dp, BorderSoft),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = if (initialLevel == null) "Create Education Level" else "Edit Education Level",
            style = MaterialTheme.typography.headlineSmall,
            color = IndigoDeep,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = {
              if (it.length <= 40) name = it
              validationError = null
            },
            label = { Text("Level Name *") },
            placeholder = { Text("e.g. Targeted Spanish or Younger Spelling") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { descFocus.requestFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("level_name_input"),
            shape = RoundedCornerShape(14.dp),
          )

          OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 120) description = it },
            label = { Text("Description (optional)") },
            placeholder = { Text("e.g. Everyday Spanish words and phrases") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  keyboardController?.hide()
                  focusManager.clearFocus(force = true)
                },
              ),
            modifier =
              Modifier.fillMaxWidth()
                .focusRequester(descFocus)
                .testTag("level_description_input"),
            shape = RoundedCornerShape(14.dp),
          )

          Text(
            text = "Built-in Practice Targets",
            style = MaterialTheme.typography.labelLarge,
            color = SlateInk,
          )
          Text(
            text = "Select topics to practice automatically, or leave empty to use only custom questions assigned to this level.",
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted,
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            PracticeTopic.entries.forEach { topic ->
              val selected = topic in selectedTopics
              FilterChip(
                selected = selected,
                onClick = {
                  selectedTopics =
                    if (selected) selectedTopics - topic else selectedTopics + topic
                  validationError = null
                },
                leadingIcon =
                  if (selected) {
                    {
                      Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  } else null,
                label = { Text(topic.label, maxLines = 1) },
                modifier = Modifier.testTag("level_topic_chip_${topic.key}"),
              )
            }
          }

          if (validationError != null) {
            Text(
              text = validationError!!,
              style = MaterialTheme.typography.bodySmall,
              color = CoralError,
              modifier = Modifier.testTag("level_validation_error"),
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(0.8f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
          ) {
            Text(text = "Cancel", maxLines = 1, softWrap = false)
          }
          Button(
            onClick = {
              val cleanName = name.trim()
              if (cleanName.isEmpty()) {
                validationError = "Enter a level name"
              } else {
                val draft =
                  CustomEducationLevel(
                    id = initialLevel?.id ?: "",
                    name = cleanName,
                    description = description.trim(),
                    topics = selectedTopics,
                    version = initialLevel?.version ?: 1,
                  )
                onSave(draft, initialLevel)
                onDismiss()
              }
            },
            modifier = Modifier.weight(1.3f).height(48.dp).testTag("level_save_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
          ) {
            Text(
              text = if (initialLevel == null) "Save Level" else "Save Changes",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
