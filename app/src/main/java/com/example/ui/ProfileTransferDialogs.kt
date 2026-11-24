package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProfileSummaryCounts
import com.example.engine.ProfileExportBundle
import com.example.engine.ProfileExportEngine
import com.example.model.KidProfile
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.CoralError
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted

data class ExistingProfileMatch(
  val profile: KidProfile,
  val summary: ProfileSummaryCounts,
)

data class ProfileImportPreviewUiState(
  val bundle: ProfileExportBundle,
  val exactIdMatch: ExistingProfileMatch? = null,
  val sameNameMatches: List<ExistingProfileMatch> = emptyList(),
)

@Composable
fun ProfileImportConfirmationDialog(
  preview: ProfileImportPreviewUiState,
  onOverwriteExisting: (targetProfileId: String) -> Unit,
  onImportAsNew: () -> Unit,
  onDismiss: () -> Unit,
) {
  val bundle = preview.bundle
  val importedCode = ProfileExportEngine.shortProfileCode(bundle.profile.id)
  val exactMatch = preview.exactIdMatch
  val sameNameMatches = preview.sameNameMatches
  var selectedNameMatchId by
    remember(bundle.profile.id, sameNameMatches) {
      mutableStateOf(sameNameMatches.firstOrNull()?.profile?.id)
    }

  val hasExactConflict = exactMatch != null
  val hasNameConflict = exactMatch == null && sameNameMatches.isNotEmpty()
  val titleText =
    when {
      hasExactConflict -> "Overwrite Matching Profile?"
      hasNameConflict -> "Same Name Found"
      else -> "Import Kid Profile?"
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("profile_import_dialog"),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector =
            if (hasExactConflict || hasNameConflict) Icons.Filled.Warning else Icons.Filled.PersonAdd,
          contentDescription = null,
          tint =
            when {
              hasExactConflict -> CoralError
              hasNameConflict -> Color(0xFFD97706)
              else -> IndigoPrimary
            },
          modifier = Modifier.size(26.dp),
        )
        Text(
          text = titleText,
          style = MaterialTheme.typography.headlineSmall,
          color = IndigoDeep,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (hasExactConflict) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFEF2F2),
            border = BorderStroke(1.5.dp, CoralError.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().testTag("profile_import_overwrite_warning"),
          ) {
            Text(
              text =
                "A matching profile (#$importedCode) already exists on this device. Overwriting will replace ALL coloring progress, sticker book pages, and settings for \"${exactMatch!!.profile.name}\".",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF991B1B),
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else if (hasNameConflict) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFFFBEB),
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth().testTag("profile_import_name_warning"),
          ) {
            Text(
              text =
                "Another profile named \"${bundle.profile.name}\" exists on this device with a different Profile ID. Choose whether to keep both as separate profiles or overwrite an existing one (erasing its progress and settings).",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF92400E),
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = IndigoContainer.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, BorderSoft),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "FROM FILE",
              style = MaterialTheme.typography.labelSmall,
              color = IndigoPrimary,
              fontWeight = FontWeight.Bold,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(text = bundle.profile.avatar, fontSize = 28.sp)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${bundle.profile.name} (#$importedCode)",
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    "${bundle.profile.challengeMode.label} • ${if (bundle.profile.soundEnabled) "Sound on" else "Muted"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                )
              }
            }
            Text(
              text =
                "${bundle.completedCount} completed • ${bundle.inProgressCount} in progress • ${bundle.stickerCount} sticker(s)",
              style = MaterialTheme.typography.bodySmall,
              color = SlateInk,
              fontWeight = FontWeight.Medium,
            )
            val extrasCount =
              bundle.customTemplates.size + bundle.customLevels.size + bundle.customQuestions.size
            if (extrasCount > 0) {
              Text(
                text =
                  "Includes ${bundle.customTemplates.size} custom page(s), ${bundle.customLevels.size} custom level(s)",
                style = MaterialTheme.typography.bodySmall,
                color = IndigoPrimary,
              )
            }
          }
        }

        if (exactMatch != null) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "CURRENT ON THIS DEVICE (WILL BE REPLACED)",
                style = MaterialTheme.typography.labelSmall,
                color = CoralError,
                fontWeight = FontWeight.Bold,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(text = exactMatch.profile.avatar, fontSize = 26.sp)
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      "${exactMatch.profile.name} (#${ProfileExportEngine.shortProfileCode(exactMatch.profile.id)})",
                    style = MaterialTheme.typography.titleMedium,
                    color = SlateInk,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text =
                      "${exactMatch.summary.completedCount} completed • ${exactMatch.summary.inProgressCount} in progress • ${exactMatch.summary.stickerCount} sticker(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted,
                  )
                }
              }
            }
          }
        } else if (hasNameConflict) {
          Text(
            text = "Existing \"${bundle.profile.name}\" profile(s) on this device:",
            style = MaterialTheme.typography.labelLarge,
            color = IndigoDeep,
          )
          sameNameMatches.forEach { match ->
            val matchCode = ProfileExportEngine.shortProfileCode(match.profile.id)
            val isSelected = selectedNameMatchId == match.profile.id
            Surface(
              onClick = { selectedNameMatchId = match.profile.id },
              shape = RoundedCornerShape(14.dp),
              color = if (isSelected) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
              border =
                BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) CoralError else BorderSoft,
                ),
              modifier = Modifier.fillMaxWidth().testTag("profile_import_match_${match.profile.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                RadioButton(selected = isSelected, onClick = { selectedNameMatchId = match.profile.id })
                Text(text = match.profile.avatar, fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${match.profile.name} (#$matchCode)",
                    style = MaterialTheme.typography.titleSmall,
                    color = IndigoDeep,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text =
                      "${match.summary.completedCount} completed • ${match.summary.inProgressCount} in progress • ${match.summary.stickerCount} sticker(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted,
                  )
                }
              }
            }
          }
        }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          if (exactMatch != null) {
            Button(
              onClick = { onOverwriteExisting(exactMatch.profile.id) },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_import_confirm_overwrite_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = CoralError),
            ) {
              Text("Overwrite Profile (#$importedCode)", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
              onClick = onImportAsNew,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_import_as_new_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Keep Both (Import as Separate Profile)")
            }
          } else if (hasNameConflict) {
            Button(
              onClick = onImportAsNew,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_import_as_new_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
            ) {
              Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Keep Both (Import as New Profile)", fontWeight = FontWeight.Bold)
            }
            val targetId = selectedNameMatchId
            if (targetId != null) {
              val targetCode = ProfileExportEngine.shortProfileCode(targetId)
              OutlinedButton(
                onClick = { onOverwriteExisting(targetId) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_import_confirm_overwrite_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, CoralError),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
              ) {
                Text("Overwrite Existing (#$targetCode)", fontWeight = FontWeight.Bold)
              }
            }
          } else {
            Button(
              onClick = onImportAsNew,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_import_confirm_new_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Import Profile", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("profile_import_cancel_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}
