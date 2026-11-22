package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SnippetFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ExportFormat
import com.example.engine.PageExportEngine
import com.example.model.PictureTemplate
import com.example.ui.components.PixelPreviewCanvas
import com.example.ui.components.PreviewRenderMode
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateInk
import com.example.ui.theme.SlateMuted
import kotlinx.coroutines.launch

@Composable
fun ExportShareDialog(
  template: PictureTemplate,
  frameIndex: Int = 0,
  onNotify: (String) -> Unit,
  onDismiss: () -> Unit,
  syncState: SyncSettingsUiState = SyncSettingsUiState(),
  onVerifyAdminPin: (String) -> Boolean = { false },
  onSetupAdminPin: (String) -> Boolean = { false },
  onResetAdminPin: () -> Unit = {},
  onPublishToGitHub: ((PictureTemplate, String, (Boolean, String) -> Unit) -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedFormat by remember(template.id) { mutableStateOf(ExportFormat.PNG_IMAGE) }
  var transparentPng by remember(template.id) { mutableStateOf(false) }
  val initialPageId =
    remember(template.id, template.title) {
      if (template.id.startsWith("custom_")) {
        PageExportEngine.sanitizePageId(template.title).ifBlank { template.id }
      } else {
        template.id
      }
    }
  var pageIdInput by remember(template.id) { mutableStateOf(initialPageId) }
  var pageVersion by remember(template.id, template.version) {
    mutableIntStateOf(template.version.coerceAtLeast(1))
  }
  var isBusy by remember { mutableStateOf(false) }
  var showPinPromptForPublish by remember { mutableStateOf(false) }

  fun buildEffectiveTemplate(): PictureTemplate {
    val cleanId = PageExportEngine.sanitizePageId(pageIdInput).ifBlank { template.id }
    return template.copy(id = cleanId, version = pageVersion.coerceAtLeast(1))
  }

  val savePngLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument(ExportFormat.PNG_IMAGE.mimeType),
    ) { destUri ->
      if (destUri != null) {
        isBusy = true
        scope.launch {
          val ok =
            PageExportEngine.writeExportToUri(
              context = context,
              destinationUri = destUri,
              template = buildEffectiveTemplate(),
              format = ExportFormat.PNG_IMAGE,
              frameIndex = frameIndex,
              transparentPng = transparentPng,
            )
          isBusy = false
          onNotify(if (ok) "Saved PNG image!" else "Couldn't save PNG image")
          if (ok) onDismiss()
        }
      }
    }

  val saveJsonLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument(ExportFormat.COLORPOP_PAGE.mimeType),
    ) { destUri ->
      if (destUri != null) {
        isBusy = true
        scope.launch {
          val ok =
            PageExportEngine.writeExportToUri(
              context = context,
              destinationUri = destUri,
              template = buildEffectiveTemplate(),
              format = ExportFormat.COLORPOP_PAGE,
              frameIndex = frameIndex,
              transparentPng = false,
            )
          isBusy = false
          onNotify(if (ok) "Saved ColorPop page file!" else "Couldn't save page file")
          if (ok) onDismiss()
        }
      }
    }

  AlertDialog(
    onDismissRequest = { if (!isBusy) onDismiss() },
    title = {
      Text(
        text = "Share or Export",
        style = MaterialTheme.typography.headlineSmall,
        color = IndigoDeep,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Preview Header Card
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, BorderSoft),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(4.dp),
              ) {
                PixelPreviewCanvas(
                  template = template,
                  mode = PreviewRenderMode.FULL_COLOR,
                  frameIndex = if (template.frames.size > 1) frameIndex else null,
                  modifier = Modifier.fillMaxSize(),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = template.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = IndigoDeep,
                )
                val frameLabel = if (template.frames.size > 1) " • ${template.frames.size} frames" else ""
                Text(
                  text = "${template.gridWidth}×${template.gridHeight} • ${template.palette.size} colors$frameLabel",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                )
              }
            }
          }

          ExportFormatCard(
            selected = selectedFormat == ExportFormat.PNG_IMAGE,
            icon = Icons.Filled.Image,
            title = "PNG Image (.png)",
            subtitle = "Works in Texts, Email, Google Drive, Photos & Pick an Image",
            onClick = { selectedFormat = ExportFormat.PNG_IMAGE },
            testTag = "export_format_png",
          )

          if (selectedFormat == ExportFormat.PNG_IMAGE) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Transparent background",
                  style = MaterialTheme.typography.labelLarge,
                  color = SlateInk,
                )
                Text(
                  text = "Off = crisp white backdrop; On = clear empty squares",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                )
              }
              Switch(
                checked = transparentPng,
                onCheckedChange = { transparentPng = it },
                modifier = Modifier.testTag("export_transparent_switch"),
              )
            }
            if (template.frames.size > 1) {
              Text(
                text = "Note: PNG exports frame ${frameIndex + 1}. Use ColorPop Page File to keep all ${template.frames.size} frames.",
                style = MaterialTheme.typography.bodySmall,
                color = IndigoPrimary,
              )
            }
          }

          ExportFormatCard(
            selected = selectedFormat == ExportFormat.COLORPOP_PAGE,
            icon = Icons.Filled.SnippetFolder,
            title = "ColorPop Page File (.colorpop.json)",
            subtitle = "Best for GitHub official updates & Drive/Email — keeps exact ID, version, colors & frames",
            onClick = { selectedFormat = ExportFormat.COLORPOP_PAGE },
            testTag = "export_format_json",
          )

          if (selectedFormat == ExportFormat.COLORPOP_PAGE) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = pageIdInput,
                onValueChange = { if (it.length <= 40) pageIdInput = it },
                label = { Text("Page ID") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("export_page_id_input"),
                shape = RoundedCornerShape(12.dp),
              )
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, BorderSoft),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                  IconButton(
                    onClick = { if (pageVersion > 1) pageVersion-- },
                    enabled = pageVersion > 1,
                    modifier = Modifier.size(36.dp).testTag("export_version_dec"),
                  ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease version", modifier = Modifier.size(16.dp))
                  }
                  Text(
                    text = "v$pageVersion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = IndigoDeep,
                  )
                  IconButton(
                    onClick = { pageVersion++ },
                    modifier = Modifier.size(36.dp).testTag("export_version_inc"),
                  ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase version", modifier = Modifier.size(16.dp))
                  }
                }
              }
            }

            val effectiveId = PageExportEngine.sanitizePageId(pageIdInput).ifBlank { template.id }
            val isAlreadyUpToDate =
              template.isSyncedOfficial &&
                !template.isCustom &&
                effectiveId == template.id &&
                pageVersion == template.version.coerceAtLeast(1)

            if (isAlreadyUpToDate) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().testTag("export_uptodate_github_badge"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(18.dp),
                  )
                  Text(
                    text = "Up to date on GitHub (v${template.version})",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSuccess,
                  )
                }
              }
            } else if (onPublishToGitHub != null) {
              val isThisPublishing =
                isBusy ||
                  (syncState.isPublishing &&
                    (syncState.publishingTemplateId == template.id ||
                      syncState.publishingTemplateId == effectiveId ||
                      syncState.publishingTemplateId == null))
              Button(
                onClick = {
                  if (!syncState.isAdminUnlocked) {
                    showPinPromptForPublish = true
                  } else {
                    isBusy = true
                    onPublishToGitHub(buildEffectiveTemplate(), template.id) { ok, _ ->
                      isBusy = false
                      if (ok) onDismiss()
                    }
                  }
                },
                enabled = !isBusy && !syncState.isPublishing,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_publish_github_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
              ) {
                if (isThisPublishing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                } else {
                  Icon(
                    imageVector =
                      if (syncState.isAdminUnlocked) Icons.Filled.CloudUpload else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.publish_to_github))
              }
              if (!syncState.hasPublishToken) {
                Text(
                  text = "Admin setup: add your GitHub token in Parent Settings → Admin first.",
                  style = MaterialTheme.typography.bodySmall,
                  color = SlateMuted,
                )
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = IndigoContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Sharing & GitHub tip: Upload .colorpop.json files to your GitHub repo so every tablet & Android device auto-updates over Wi-Fi, or share via Drive/Email and tap Import Page File.",
              style = MaterialTheme.typography.bodySmall,
              color = IndigoDeep,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        // Primary Share & Save actions pinned visibly at bottom
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = {
              isBusy = true
              scope.launch {
                try {
                  val eff = buildEffectiveTemplate()
                  val uri =
                    PageExportEngine.writeExportFileToCache(
                      context = context,
                      template = eff,
                      format = selectedFormat,
                      frameIndex = frameIndex,
                      transparentPng = transparentPng,
                    )
                  val launched =
                    PageExportEngine.launchShareIntent(
                      context = context,
                      uri = uri,
                      format = selectedFormat,
                      title = eff.title,
                    )
                  isBusy = false
                  if (launched) {
                    onDismiss()
                  } else {
                    onNotify("No sharing app found on this device")
                  }
                } catch (_: Throwable) {
                  isBusy = false
                  onNotify("Couldn't prepare file for sharing")
                }
              }
            },
            enabled = !isBusy,
            modifier = Modifier.weight(1f).height(48.dp).testTag("export_share_now_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.share_now))
          }

          OutlinedButton(
            onClick = {
              val eff = buildEffectiveTemplate()
              val suggested = PageExportEngine.suggestedFileName(eff, selectedFormat)
              if (selectedFormat == ExportFormat.PNG_IMAGE) {
                savePngLauncher.launch(suggested)
              } else {
                saveJsonLauncher.launch(suggested)
              }
            },
            enabled = !isBusy,
            modifier = Modifier.weight(1f).height(48.dp).testTag("export_save_file_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.save_to_files))
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = { if (!isBusy) onDismiss() }) {
        Text("Close")
      }
    },
  )

  if (showPinPromptForPublish && onPublishToGitHub != null) {
    ParentPinPromptDialog(
      hasExistingPin = syncState.hasAdminPin,
      onSetupPin = { pin ->
        val ok = onSetupAdminPin(pin)
        if (ok) {
          showPinPromptForPublish = false
          if (syncState.hasPublishToken) {
            isBusy = true
            onPublishToGitHub(buildEffectiveTemplate(), template.id) { pubOk, _ ->
              isBusy = false
              if (pubOk) onDismiss()
            }
          } else {
            onNotify("PIN saved! Add your GitHub token in Parent Settings → Admin.")
          }
        }
        ok
      },
      onVerifyPin = { pin ->
        val ok = onVerifyAdminPin(pin)
        if (ok) {
          showPinPromptForPublish = false
          if (syncState.hasPublishToken) {
            isBusy = true
            onPublishToGitHub(buildEffectiveTemplate(), template.id) { pubOk, _ ->
              isBusy = false
              if (pubOk) onDismiss()
            }
          } else {
            onNotify("Add your GitHub token in Parent Settings → Admin first.")
          }
        }
        ok
      },
      onResetPinAndToken = {
        onResetAdminPin()
        showPinPromptForPublish = false
      },
      onDismiss = { showPinPromptForPublish = false },
    )
  }
}

@Composable
private fun ExportFormatCard(
  selected: Boolean,
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    shape = RoundedCornerShape(14.dp),
    color = if (selected) IndigoContainer.copy(alpha = 0.65f) else Color.White,
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) IndigoPrimary else BorderSoft),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) IndigoPrimary else SlateMuted,
        modifier = Modifier.size(22.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = IndigoDeep,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = SlateMuted,
        )
      }
    }
  }
}
