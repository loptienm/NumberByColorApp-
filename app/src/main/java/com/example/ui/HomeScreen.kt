package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ALL_BUILT_IN_TEMPLATES
import com.example.data.ProfileProgressState
import com.example.model.KidProfile
import com.example.model.PictureTemplate
import com.example.ui.components.PixelPreviewCanvas
import com.example.ui.components.PreviewRenderMode
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.BorderSoft
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MarigoldAccent
import com.example.ui.theme.SkyTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  profile: KidProfile?,
  templates: List<PictureTemplate>,
  progressState: ProfileProgressState,
  onOpenPicture: (PictureTemplate) -> Unit,
  onOpenCreatePage: () -> Unit,
  onEditCustom: (PictureTemplate) -> Unit,
  onDeleteCustom: (String) -> Unit,
  onOpenProfiles: () -> Unit,
  onOpenStickers: () -> Unit,
  onToggleSound: () -> Unit,
  onOpenSettings: () -> Unit,
  onNotify: (String) -> Unit = {},
  syncState: SyncSettingsUiState = SyncSettingsUiState(),
  onVerifyAdminPin: (String) -> Boolean = { false },
  onSetupAdminPin: (String) -> Boolean = { false },
  onResetAdminPin: () -> Unit = {},
  onPublishToGitHub: ((PictureTemplate, String, (Boolean, String) -> Unit) -> Unit)? = null,
) {
  var isEditMode by remember { mutableStateOf(false) }
  var pendingDeleteTemplate by remember { mutableStateOf<PictureTemplate?>(null) }
  var pendingShareTemplate by remember { mutableStateOf<PictureTemplate?>(null) }
  val builtInIds = remember { ALL_BUILT_IN_TEMPLATES.mapTo(mutableSetOf()) { it.id } }

  BackHandler(enabled = isEditMode) { isEditMode = false }

  val completedCount = templates.count { it.id in progressState.completedIds }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
  ) {
    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = onOpenCreatePage,
          modifier = Modifier.navigationBarsPadding().testTag("create_page_fab"),
          containerColor = IndigoPrimary,
          contentColor = Color.White,
          shape = RoundedCornerShape(20.dp),
          icon = {
            Icon(
              imageVector = Icons.Filled.AddPhotoAlternate,
              contentDescription = null,
            )
          },
          text = {
            Text(
              text = stringResource(R.string.create_page),
              style = MaterialTheme.typography.labelLarge,
            )
          },
        )
      },
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize().padding(innerPadding).statusBarsPadding().padding(horizontal = 16.dp)
      ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "🎨 ${stringResource(R.string.home_title)}",
              style = MaterialTheme.typography.displaySmall,
              color = IndigoDeep,
            )
            Text(
              text = stringResource(R.string.home_subtitle),
              style = MaterialTheme.typography.bodyLarge,
              color = IndigoPrimary,
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
              onClick = { isEditMode = !isEditMode },
              modifier = Modifier.size(48.dp).testTag("home_edit_mode_button"),
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = if (isEditMode) IndigoPrimary else IndigoContainer,
                  contentColor = if (isEditMode) Color.White else IndigoPrimary,
                ),
            ) {
              Icon(
                imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription =
                  if (isEditMode) stringResource(R.string.done_editing)
                  else stringResource(R.string.edit_pages),
              )
            }

            val soundOn = profile?.soundEnabled != false
            IconButton(
              onClick = onToggleSound,
              modifier = Modifier.size(48.dp).testTag("home_sound_button"),
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
              modifier = Modifier.size(48.dp).testTag("home_settings_button"),
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

        Spacer(modifier = Modifier.height(10.dp))

        // Kid Profile & Sticker Book Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            onClick = onOpenProfiles,
            modifier = Modifier.height(48.dp).testTag("profile_chip_button"),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, BorderSoft),
            shadowElevation = 3.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(text = profile?.avatar ?: "🐱", fontSize = 22.sp)
              Text(
                text = profile?.name ?: "Player 1",
                style = MaterialTheme.typography.titleMedium,
                color = IndigoDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Surface(
            onClick = onOpenStickers,
            modifier = Modifier.height(48.dp).testTag("stickers_chip_button"),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, BorderSoft),
            shadowElevation = 3.dp,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(text = "📒", fontSize = 20.sp)
              Text(
                text = "$completedCount/${templates.size}",
                style = MaterialTheme.typography.titleMedium,
                color = IndigoDeep,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditMode) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = IndigoContainer,
            border = BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("home_edit_mode_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = null,
                  tint = IndigoPrimary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Tap any page to edit its blocks",
                  style = MaterialTheme.typography.titleSmall,
                  color = IndigoDeep,
                )
              }
              TextButton(
                onClick = { isEditMode = false },
                modifier = Modifier.testTag("home_edit_done_button"),
              ) {
                Text(
                  text = stringResource(R.string.done_editing),
                  color = IndigoPrimary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Responsive Gallery Grid (adapts naturally from phones to Fire tablets)
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 152.dp),
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("home_gallery_grid"),
          contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          items(templates, key = { it.id }) { tpl ->
            val isCompleted = tpl.id in progressState.completedIds
            val inProgress = progressState.inProgressGrids[tpl.id]

            PictureGalleryCard(
              template = tpl,
              isCompleted = isCompleted,
              isEditMode = isEditMode,
              inProgressGrid = inProgress,
              onClick = {
                if (isEditMode) {
                  isEditMode = false
                  onEditCustom(tpl)
                } else {
                  onOpenPicture(tpl)
                }
              },
              onShareCustom = { pendingShareTemplate = tpl },
              onDeleteCustom =
                if (isEditMode && tpl.isCustom) {
                  { pendingDeleteTemplate = tpl }
                } else null,
            )
          }
        }
      }
    }
  }

  val shareTarget = pendingShareTemplate
  if (shareTarget != null) {
    ExportShareDialog(
      template = shareTarget,
      frameIndex = 0,
      onNotify = onNotify,
      onDismiss = { pendingShareTemplate = null },
      syncState = syncState,
      onVerifyAdminPin = onVerifyAdminPin,
      onSetupAdminPin = onSetupAdminPin,
      onResetAdminPin = onResetAdminPin,
      onPublishToGitHub = onPublishToGitHub,
    )
  }

  val deleteTarget = pendingDeleteTemplate
  if (deleteTarget != null) {
    val isRevert = deleteTarget.id in builtInIds
    AlertDialog(
      onDismissRequest = { pendingDeleteTemplate = null },
      title = { Text(if (isRevert) "Revert to Original Page?" else "Delete Custom Page?") },
      text = {
        Text(
          if (isRevert) {
            "Remove your local edits to \"${deleteTarget.title}\" and restore the original page?"
          } else {
            "Remove \"${deleteTarget.title}\" and any coloring progress on it?"
          }
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteCustom(deleteTarget.id)
            pendingDeleteTemplate = null
          }
        ) {
          Text(
            text = if (isRevert) "Revert" else "Delete",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteTemplate = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun PictureGalleryCard(
  template: PictureTemplate,
  isCompleted: Boolean,
  isEditMode: Boolean,
  inProgressGrid: Array<IntArray>?,
  onClick: () -> Unit,
  onShareCustom: (() -> Unit)?,
  onDeleteCustom: (() -> Unit)?,
) {
  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag("picture_card_${template.id}"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border =
      BorderStroke(
        width = 3.dp,
        color =
          when {
            isEditMode -> IndigoPrimary.copy(alpha = 0.85f)
            isCompleted -> MarigoldAccent.copy(alpha = 0.65f)
            else -> Color.White
          },
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0xFFF1F5F9))
              .padding(6.dp)
        ) {
          PixelPreviewCanvas(
            template = template,
            mode =
              if (isCompleted) PreviewRenderMode.FULL_COLOR
              else PreviewRenderMode.GRAY_WITH_PROGRESS,
            paintedGrid = inProgressGrid,
            modifier = Modifier.fillMaxSize(),
          )
          if (template.frames.size > 1) {
            Surface(
              modifier = Modifier.align(Alignment.BottomEnd),
              shape = CircleShape,
              color = IndigoContainer.copy(alpha = 0.92f),
              shadowElevation = 2.dp,
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Animated",
                tint = IndigoPrimary,
                modifier = Modifier.padding(4.dp).size(14.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = template.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (onShareCustom != null) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (onShareCustom != null) {
            IconButton(
              onClick = onShareCustom,
              modifier = Modifier.size(36.dp).testTag("share_custom_${template.id}"),
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = IndigoContainer.copy(alpha = 0.7f),
                  contentColor = IndigoPrimary,
                ),
            ) {
              Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.export_share),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      if (isCompleted) {
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
          shape = CircleShape,
          color = MarigoldAccent,
          shadowElevation = 2.dp,
        ) {
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Completed",
            tint = Color.White,
            modifier = Modifier.padding(4.dp).size(18.dp),
          )
        }
      }

      if (onDeleteCustom != null) {
        IconButton(
          onClick = onDeleteCustom,
          modifier = Modifier.align(Alignment.TopEnd).size(40.dp).testTag("delete_custom_${template.id}"),
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = Color.White.copy(alpha = 0.90f),
              contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete custom page",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
