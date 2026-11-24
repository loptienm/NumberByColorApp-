package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.PageExportEngine
import com.example.engine.RemoteCatalogSyncEngine
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.KID_AVATARS
import com.example.model.PictureTemplate
import com.example.ui.components.PixelPreviewCanvas
import com.example.ui.components.PreviewRenderMode
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

@Composable
fun SettingsSectionCard(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    color = Color.White,
    border = BorderStroke(1.5.dp, BorderSoft),
    shadowElevation = 2.dp,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      content()
    }
  }
}

@Composable
fun SettingsMenuRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String,
  badgeText: String? = null,
  trailingIcon: ImageVector? = null,
  trailingTint: Color = IndigoPrimary,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFFF8FAFC),
    border = BorderStroke(1.dp, BorderSoft),
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = IndigoContainer,
        modifier = Modifier.size(42.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IndigoPrimary,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = IndigoDeep,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = SlateMuted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (!badgeText.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = IndigoContainer.copy(alpha = 0.75f),
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            color = IndigoDeep,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      if (trailingIcon != null) {
        Icon(
          imageVector = trailingIcon,
          contentDescription = null,
          tint = trailingTint,
          modifier = Modifier.size(18.dp),
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = SlateMuted,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsPage(
  profileId: String = "",
  profileStatsLabel: String = "",
  name: String,
  onNameChange: (String) -> Unit,
  selectedAvatar: String,
  onSelectAvatar: (String) -> Unit,
  selectedMode: ChallengeMode,
  selectedCustomLevelId: String?,
  customLevels: List<CustomEducationLevel>,
  onSelectLevel: (ChallengeMode, String?) -> Unit,
  soundEnabled: Boolean,
  onToggleSound: () -> Unit,
  canRemoveKid: Boolean,
  onAddKidClick: () -> Unit,
  onRequestRemoveKid: () -> Unit,
  onShareProfileClick: (() -> Unit)? = null,
  onSaveProfileFileClick: (() -> Unit)? = null,
  onImportProfileClick: (() -> Unit)? = null,
) {
  var difficultyExpanded by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val activeCustomLevel =
    remember(selectedCustomLevelId, customLevels) {
      customLevels.firstOrNull { it.id == selectedCustomLevelId }
    }
  val activeLevelLabel = activeCustomLevel?.name ?: selectedMode.label
  val activeLevelSubtitle =
    if (activeCustomLevel != null) {
      activeCustomLevel.description.ifBlank {
        val topics = activeCustomLevel.topics.joinToString(", ") { it.label }
        if (topics.isNotBlank()) topics else "Custom practice target"
      }
    } else {
      selectedMode.subtitle
    }

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SettingsSectionCard {
      Text(
        text = "Kid Profile",
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )
      OutlinedTextField(
        value = name,
        onValueChange = { if (it.length <= 16) onNameChange(it) },
        label = { Text(stringResource(R.string.name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
          KeyboardActions(
            onDone = {
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
            },
          ),
        modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
        shape = RoundedCornerShape(14.dp),
      )

      Text(
        text = stringResource(R.string.avatar_label),
        style = MaterialTheme.typography.titleMedium,
        color = SlateInk,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        KID_AVATARS.forEach { emoji ->
          val isSelected = emoji == selectedAvatar
          Surface(
            onClick = { onSelectAvatar(emoji) },
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) IndigoContainer else Color(0xFFF1F5F9),
            border =
              BorderStroke(
                width = 2.dp,
                color = if (isSelected) IndigoPrimary else Color.Transparent,
              ),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(text = emoji, fontSize = 26.sp)
            }
          }
        }
      }
    }

    SettingsSectionCard {
      Text(
        text = stringResource(R.string.challenge_level_label),
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )

      ExposedDropdownMenuBox(
        expanded = difficultyExpanded,
        onExpandedChange = { difficultyExpanded = !difficultyExpanded },
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedTextField(
          value = activeLevelLabel,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = difficultyExpanded)
          },
          supportingText = {
            Text(
              text = activeLevelSubtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = SlateMuted,
            )
          },
          shape = RoundedCornerShape(14.dp),
          modifier =
            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth()
              .testTag("challenge_level_dropdown"),
        )

        ExposedDropdownMenu(
          expanded = difficultyExpanded,
          onDismissRequest = { difficultyExpanded = false },
          containerColor = Color.White,
        ) {
          ChallengeMode.entries.forEach { mode ->
            val isSelected = activeCustomLevel == null && mode == selectedMode
            DropdownMenuItem(
              text = {
                Column {
                  Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) IndigoPrimary else SlateInk,
                  )
                  Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMuted,
                  )
                }
              },
              trailingIcon =
                if (isSelected) {
                  {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = "Selected",
                      tint = IndigoPrimary,
                    )
                  }
                } else null,
              onClick = {
                onSelectLevel(mode, null)
                difficultyExpanded = false
              },
              modifier = Modifier.testTag("challenge_mode_${mode.key}"),
            )
          }

          if (customLevels.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = BorderSoft)
            customLevels.forEach { lvl ->
              val isSelected = activeCustomLevel?.id == lvl.id
              val lvlSub =
                lvl.description.ifBlank {
                  lvl.topics.joinToString(", ") { it.label }.ifBlank { "Custom education level" }
                }
              DropdownMenuItem(
                text = {
                  Column {
                    Text(
                      text = lvl.name,
                      style = MaterialTheme.typography.titleMedium,
                      color = if (isSelected) IndigoPrimary else SlateInk,
                    )
                    Text(
                      text = lvlSub,
                      style = MaterialTheme.typography.bodyMedium,
                      color = SlateMuted,
                    )
                  }
                },
                trailingIcon =
                  if (isSelected) {
                    {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = IndigoPrimary,
                      )
                    }
                  } else null,
                onClick = {
                  onSelectLevel(ChallengeMode.PRE_K, lvl.id)
                  difficultyExpanded = false
                },
                modifier = Modifier.testTag("challenge_custom_level_${lvl.id}"),
              )
            }
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, BorderSoft),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.settings_offline_note),
          style = MaterialTheme.typography.bodyMedium,
          color = SlateMuted,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    SettingsSectionCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.sound_effects_label),
            style = MaterialTheme.typography.titleMedium,
            color = IndigoDeep,
          )
          Text(
            text = stringResource(R.string.sound_effects_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted,
          )
        }
        Switch(
          checked = soundEnabled,
          onCheckedChange = { onToggleSound() },
          modifier = Modifier.testTag("settings_sound_switch"),
        )
      }
    }

    if (onShareProfileClick != null || onSaveProfileFileClick != null || onImportProfileClick != null) {
      SettingsSectionCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.profile_backup_title),
            style = MaterialTheme.typography.titleMedium,
            color = IndigoDeep,
            modifier = Modifier.weight(1f),
          )
          if (profileId.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = IndigoContainer,
              modifier = Modifier.testTag("settings_profile_id_badge"),
            ) {
              Text(
                text = "ID #${com.example.engine.ProfileExportEngine.shortProfileCode(profileId)}",
                style = MaterialTheme.typography.labelMedium,
                color = IndigoDeep,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

        Text(
          text =
            if (profileStatsLabel.isNotBlank()) {
              "$profileStatsLabel • ${stringResource(R.string.profile_backup_subtitle)}"
            } else {
              stringResource(R.string.profile_backup_subtitle)
            },
          style = MaterialTheme.typography.bodySmall,
          color = SlateMuted,
        )

        if (onShareProfileClick != null || onSaveProfileFileClick != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (onShareProfileClick != null) {
              Button(
                onClick = onShareProfileClick,
                modifier =
                  Modifier.weight(1f).height(48.dp).testTag("settings_export_share_profile_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.share_profile_json), maxLines = 1, softWrap = false)
              }
            }
            if (onSaveProfileFileClick != null) {
              OutlinedButton(
                onClick = onSaveProfileFileClick,
                modifier =
                  Modifier.weight(1f).height(48.dp).testTag("settings_export_save_profile_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.save_profile_json), maxLines = 1, softWrap = false)
              }
            }
          }
        }

        if (onImportProfileClick != null) {
          OutlinedButton(
            onClick = onImportProfileClick,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_import_profile_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("${stringResource(R.string.import_profile_json)} (.json)", maxLines = 1, softWrap = false)
          }
        }
      }
    }

    SettingsSectionCard {
      Text(
        text = "Manage Kids",
        style = MaterialTheme.typography.titleMedium,
        color = IndigoDeep,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onAddKidClick,
          modifier = Modifier.weight(1f).height(48.dp).testTag("settings_add_kid_button"),
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = IndigoContainer,
              contentColor = IndigoDeep,
            ),
        ) {
          Text("+ Add kid", maxLines = 1, softWrap = false)
        }

        if (canRemoveKid) {
          Button(
            onClick = onRequestRemoveKid,
            modifier = Modifier.weight(1f).height(48.dp).testTag("settings_remove_kid_button"),
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE2E2),
                contentColor = Color(0xFF991B1B),
              ),
          ) {
            Text("Remove kid", maxLines = 1, softWrap = false)
          }
        }
      }
    }
  }
}

@Composable
fun AdminUnlockedBar(
  onChangePin: () -> Unit,
  onLockAdmin: () -> Unit,
  showChangePin: Boolean = true,
) {
  SettingsSectionCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = EmeraldSuccess.copy(alpha = 0.14f),
        modifier = Modifier.weight(1f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.LockOpen,
            contentDescription = null,
            tint = EmeraldSuccess,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Admin Unlocked",
            style = MaterialTheme.typography.labelLarge,
            color = EmeraldSuccess,
            maxLines = 1,
            softWrap = false,
          )
        }
      }

      if (showChangePin) {
        OutlinedButton(
          onClick = onChangePin,
          modifier = Modifier.height(44.dp).testTag("admin_change_pin_button"),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
          Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Change PIN", maxLines = 1, softWrap = false)
        }
      }

      OutlinedButton(
        onClick = onLockAdmin,
        modifier = Modifier.height(44.dp).testTag("admin_lock_button"),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
      ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Lock", maxLines = 1, softWrap = false)
      }
    }
  }
}

@Composable
fun ServerSettingsPage(
  syncState: SyncSettingsUiState,
  syncUrl: String,
  onSyncUrlChange: (String) -> Unit,
  wifiOnlyAutoSync: Boolean,
  onWifiOnlyChange: (Boolean) -> Unit,
  patInput: String,
  onPatInputChange: (String) -> Unit,
  publishFolder: String,
  onPublishFolderChange: (String) -> Unit,
  onSaveConfig: () -> Unit,
  onClearToken: () -> Unit,
  onSyncNow: () -> Unit,
  onChangePin: () -> Unit,
  onLockAdmin: () -> Unit,
) {
  var showPatPlain by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val folderFocus = remember { FocusRequester() }

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    AdminUnlockedBar(onChangePin = onChangePin, onLockAdmin = onLockAdmin, showChangePin = true)

    SettingsSectionCard {
      Text(
        text = "GitHub Server & Sync",
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )

      OutlinedTextField(
        value = syncUrl,
        onValueChange = onSyncUrlChange,
        label = { Text(stringResource(R.string.sync_url_label)) },
        placeholder = { Text(RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL) },
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions =
          KeyboardActions(
            onDone = {
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
            },
          ),
        modifier = Modifier.fillMaxWidth().testTag("settings_sync_url_input"),
        shape = RoundedCornerShape(14.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Wi-Fi only auto-updates",
            style = MaterialTheme.typography.titleSmall,
            color = SlateInk,
          )
          Text(
            text = "Always playable offline once synced",
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted,
          )
        }
        Switch(
          checked = wifiOnlyAutoSync,
          onCheckedChange = onWifiOnlyChange,
          modifier = Modifier.testTag("settings_wifi_only_switch"),
        )
      }

      Button(
        onClick = onSyncNow,
        enabled = !syncState.isSyncing && !syncState.isPublishing,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_sync_now_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        if (syncState.isSyncing) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White,
          )
        } else {
          Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.sync_now), maxLines = 1, softWrap = false)
      }
    }

    SettingsSectionCard {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = IndigoPrimary)
        Text(
          text = "Backup & Publishing Location",
          style = MaterialTheme.typography.titleLarge,
          color = IndigoDeep,
        )
      }

      Text(
        text =
          if (syncState.hasPublishToken) {
            "Token saved on this device (${syncState.maskedPublishToken}). Kids' tablets stay read-only."
          } else {
            "Add a GitHub Fine-grained PAT scoped to NumberByColor (Contents: Read & Write) to push pages and questions straight from this device."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = SlateMuted,
      )

      OutlinedTextField(
        value = patInput,
        onValueChange = onPatInputChange,
        label = {
          Text(
            if (syncState.hasPublishToken) "Replace GitHub Token (PAT)"
            else stringResource(R.string.github_pat_label)
          )
        },
        placeholder = { Text("github_pat_...") },
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { folderFocus.requestFocus() }),
        visualTransformation =
          if (showPatPlain) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { showPatPlain = !showPatPlain }) {
            Icon(
              imageVector =
                if (showPatPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = if (showPatPlain) "Hide token" else "Show token",
            )
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("admin_github_pat_input"),
        shape = RoundedCornerShape(14.dp),
      )

      val normalizedFolder = publishFolder.trim('/').ifBlank { "root" }
      OutlinedTextField(
        value = publishFolder,
        onValueChange = onPublishFolderChange,
        label = { Text(stringResource(R.string.admin_folder_label)) },
        placeholder = { Text("pages") },
        supportingText = {
          Text(
            "Pages publish to $normalizedFolder/<id>.colorpop.json • Levels to $normalizedFolder/levels/<id>.level.json • Questions to $normalizedFolder/questions/<id>.question.json"
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
          KeyboardActions(
            onDone = {
              keyboardController?.hide()
              focusManager.clearFocus(force = true)
              onSaveConfig()
            },
          ),
        modifier =
          Modifier.fillMaxWidth()
            .focusRequester(folderFocus)
            .testTag("admin_publish_folder_input"),
        shape = RoundedCornerShape(14.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onSaveConfig,
          modifier = Modifier.weight(1f).height(48.dp).testTag("admin_save_token_button"),
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = IndigoContainer,
              contentColor = IndigoDeep,
            ),
        ) {
          Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (patInput.isNotBlank()) "Save Token" else "Save Config",
            maxLines = 1,
            softWrap = false,
          )
        }

        if (syncState.hasPublishToken) {
          OutlinedButton(
            onClick = onClearToken,
            modifier = Modifier.height(48.dp).testTag("admin_clear_token_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
          ) {
            Icon(
              Icons.Filled.Delete,
              contentDescription = null,
              tint = CoralError,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear", color = CoralError, maxLines = 1, softWrap = false)
          }
        }
      }
    }

    SyncStatusCard(syncState = syncState)
  }
}

@Composable
fun PagesSettingsPage(
  syncState: SyncSettingsUiState,
  publishableTemplates: List<PictureTemplate>,
  syncedTemplates: List<PictureTemplate>,
  onSyncNow: () -> Unit,
  onPublishTemplate: (PictureTemplate) -> Unit,
  onPublishAll: (List<PictureTemplate>) -> Unit,
  onLockAdmin: () -> Unit,
) {
  val publishableIds = remember(publishableTemplates) { publishableTemplates.mapTo(mutableSetOf()) { it.id } }
  val upToDatePages =
    remember(syncedTemplates, publishableIds) {
      syncedTemplates.filter { it.id !in publishableIds }
    }

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    AdminUnlockedBar(onChangePin = {}, onLockAdmin = onLockAdmin, showChangePin = false)

    SettingsSectionCard {
      Text(
        text = "Custom & Synced Coloring Pages",
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onSyncNow,
          enabled = !syncState.isSyncing && !syncState.isPublishing,
          modifier = Modifier.weight(1f).height(48.dp).testTag("settings_sync_now_button"),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
          if (syncState.isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.sync_now), maxLines = 1, softWrap = false)
        }

        if (publishableTemplates.isNotEmpty()) {
          Button(
            onClick = { onPublishAll(publishableTemplates) },
            enabled = !syncState.isPublishing && !syncState.isSyncing,
            modifier = Modifier.weight(1f).height(48.dp).testTag("admin_publish_all_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
          ) {
            if (syncState.isPublishingAll) {
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
              Icon(
                Icons.Filled.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${stringResource(R.string.publish_all)} (${publishableTemplates.size})",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (publishableTemplates.isEmpty() && upToDatePages.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFFF8FAFC),
          border = BorderStroke(1.dp, BorderSoft),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text =
              "No edited, custom, or synced pages yet. Edit any page or draw a new one from the home gallery, then push or sync it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateMuted,
            modifier = Modifier.padding(14.dp),
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          publishableTemplates.forEach { tpl ->
            val cleanId =
              if (tpl.id.startsWith("custom_")) {
                PageExportEngine.sanitizePageId(tpl.title).ifBlank { tpl.id }
              } else {
                tpl.id
              }
            val isThisRowPublishing =
              syncState.isPublishing &&
                (syncState.publishingTemplateId == tpl.id ||
                  syncState.publishingTemplateId == cleanId ||
                  (syncState.publishingTemplateId == null && !syncState.isPublishingAll))
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFFF8FAFC),
              border = BorderStroke(1.dp, BorderSoft),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(48.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(Color.White),
                ) {
                  PixelPreviewCanvas(
                    template = tpl,
                    mode = PreviewRenderMode.FULL_COLOR,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = tpl.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IndigoDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "$cleanId.colorpop.json • v${tpl.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Button(
                  onClick = { onPublishTemplate(tpl) },
                  enabled = !syncState.isPublishing && !syncState.isSyncing,
                  modifier = Modifier.height(42.dp).testTag("admin_publish_page_${tpl.id}"),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                  if (isThisRowPublishing) {
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish", maxLines = 1, softWrap = false)
                  }
                }
              }
            }
          }

          upToDatePages.forEach { tpl ->
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = EmeraldSuccess.copy(alpha = 0.08f),
              border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.25f)),
              modifier = Modifier.fillMaxWidth().testTag("admin_uptodate_page_${tpl.id}"),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(48.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(Color.White),
                ) {
                  PixelPreviewCanvas(
                    template = tpl,
                    mode = PreviewRenderMode.FULL_COLOR,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = tpl.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IndigoDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${tpl.id}.colorpop.json • v${tpl.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = EmeraldSuccess.copy(alpha = 0.16f),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = EmeraldSuccess,
                      modifier = Modifier.size(15.dp),
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

    SyncStatusCard(syncState = syncState)
  }
}

@Composable
fun SyncStatusCard(syncState: SyncSettingsUiState) {
  val clipboardManager = LocalClipboardManager.current
  val syncDisplay =
    syncState.lastSyncStatus.ifBlank {
      "Not synced yet (${syncState.syncedCount} page(s), ${syncState.syncedLevelsCount} level(s), ${syncState.syncedQuestionsCount} question(s) cached)"
    }
  val hasAnyCached =
    syncState.syncedCount > 0 || syncState.syncedLevelsCount > 0 || syncState.syncedQuestionsCount > 0
  val fullStatusCopyText =
    buildString {
      append("Sync: ")
      append(syncDisplay)
      if (hasAnyCached && syncState.lastSyncStatus.isNotBlank()) {
        append("\nCached offline: ")
        append(syncState.syncedCount)
        append(" page(s)")
        if (syncState.syncedLevelsCount > 0) {
          append(", ")
          append(syncState.syncedLevelsCount)
          append(" level(s)")
        }
        if (syncState.syncedQuestionsCount > 0) {
          append(", ")
          append(syncState.syncedQuestionsCount)
          append(" question(s)")
        }
      }
      if (syncState.lastPublishStatus.isNotBlank()) {
        append("\nPublish: ")
        append(syncState.lastPublishStatus)
      }
    }
  val isSyncError =
    syncState.lastSyncStatus.contains("failed", ignoreCase = true) ||
      syncState.lastSyncStatus.contains("error", ignoreCase = true) ||
      syncState.lastSyncStatus.contains("HTTP 4", ignoreCase = true) ||
      syncState.lastSyncStatus.contains("HTTP 5", ignoreCase = true)

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (isSyncError) Color(0xFFFEF2F2) else Color.White,
    border =
      BorderStroke(
        width = 1.5.dp,
        color = if (isSyncError) CoralError.copy(alpha = 0.4f) else BorderSoft,
      ),
    modifier = Modifier.fillMaxWidth().testTag("admin_status_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Status",
          style = MaterialTheme.typography.titleMedium,
          color = if (isSyncError) CoralError else IndigoDeep,
        )
        TextButton(
          onClick = { clipboardManager.setText(AnnotatedString(fullStatusCopyText)) },
          modifier = Modifier.testTag("admin_copy_status_button"),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(stringResource(R.string.copy_status))
        }
      }

      SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Sync: $syncDisplay",
            style = MaterialTheme.typography.bodySmall,
            color = if (isSyncError) CoralError else SlateInk,
          )
          if (hasAnyCached && syncState.lastSyncStatus.isNotBlank()) {
            val cachedLvlPart =
              if (syncState.syncedLevelsCount > 0) ", ${syncState.syncedLevelsCount} level(s)" else ""
            val cachedQPart =
              if (syncState.syncedQuestionsCount > 0) {
                ", ${syncState.syncedQuestionsCount} question(s)"
              } else {
                ""
              }
            Text(
              text = "Cached offline: ${syncState.syncedCount} page(s)$cachedLvlPart$cachedQPart",
              style = MaterialTheme.typography.bodySmall,
              color = IndigoPrimary,
            )
          }
          if (syncState.lastPublishStatus.isNotBlank()) {
            Text(
              text = "Publish: ${syncState.lastPublishStatus}",
              style = MaterialTheme.typography.bodySmall,
              color = SlateInk,
            )
          }
        }
      }
    }
  }
}
