package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.BlockEditorScreen
import com.example.ui.ChallengeDialog
import com.example.ui.ColorPopViewModel
import com.example.ui.CreatePageScreen
import com.example.ui.HomeScreen
import com.example.ui.PlayScreen
import com.example.ui.ProfileSwitcherDialog
import com.example.ui.SettingsScreen
import com.example.ui.StickerBookScreen
import com.example.ui.theme.ColorPopTheme
import com.example.ui.theme.IndigoDeep

class MainActivity : ComponentActivity() {
  private val viewModel: ColorPopViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ColorPopTheme {
        ColorPopApp(viewModel = viewModel)
      }
    }
  }

  override fun onStop() {
    super.onStop()
    viewModel.saveCurrentProgressNow()
  }
}

@Composable
fun ColorPopApp(viewModel: ColorPopViewModel) {
  val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
  val profiles by viewModel.profiles.collectAsStateWithLifecycle()
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
  val templates by viewModel.allTemplates.collectAsStateWithLifecycle()
  val customQuestions by viewModel.allQuestions.collectAsStateWithLifecycle()
  val customLevels by viewModel.allLevels.collectAsStateWithLifecycle()
  val progressState by viewModel.progressState.collectAsStateWithLifecycle()
  val playState by viewModel.playState.collectAsStateWithLifecycle()
  val createPageState by viewModel.createPageState.collectAsStateWithLifecycle()
  val blockEditorState by viewModel.blockEditorState.collectAsStateWithLifecycle()

  val stickerPages by viewModel.stickerPages.collectAsStateWithLifecycle()
  val activeStickerPageId by viewModel.activeStickerPageId.collectAsStateWithLifecycle()
  val placedStickers by viewModel.placedStickers.collectAsStateWithLifecycle()

  val showProfiles by viewModel.showProfilesDialog.collectAsStateWithLifecycle()
  val syncSettingsState by viewModel.syncSettingsState.collectAsStateWithLifecycle()
  val challengeState by viewModel.challengeState.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

  Box(modifier = Modifier.fillMaxSize()) {
    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
      when (screen) {
        AppScreen.Home -> {
          HomeScreen(
            profile = activeProfile,
            templates = templates,
            progressState = progressState,
            onOpenPicture = viewModel::openPicture,
            onOpenCreatePage = viewModel::openCreatePage,
            onEditCustom = viewModel::openBlockEditorForTemplate,
            onDeleteCustom = viewModel::deleteCustomTemplate,
            onOpenProfiles = viewModel::openProfilesDialog,
            onOpenStickers = viewModel::openStickerBook,
            onToggleSound = viewModel::toggleSound,
            onOpenSettings = viewModel::openSettings,
            onNotify = viewModel::showToast,
            syncState = syncSettingsState,
            onVerifyAdminPin = viewModel::unlockAdminWithPin,
            onSetupAdminPin = viewModel::setupAdminPin,
            onResetAdminPin = viewModel::resetAdminPinAndClearToken,
            onPublishToGitHub = { tpl, localId, onDone ->
              viewModel.publishTemplateToGitHub(tpl, localTemplateId = localId, onComplete = onDone)
            },
          )
        }
        AppScreen.Play -> {
          PlayScreen(
            profile = activeProfile,
            playState = playState,
            onBack = viewModel::goHome,
            onSelectColor = viewModel::selectColor,
            onSelectBrush = viewModel::selectBrush,
            onPaintStroke = viewModel::paintStroke,
            onReplay = viewModel::replayCurrentPicture,
            onToggleSound = viewModel::toggleSound,
            onOpenSettings = viewModel::openSettings,
          )
        }
        AppScreen.CreatePage -> {
          CreatePageScreen(
            state = createPageState,
            onBack = viewModel::closeCreatePage,
            onImagePicked = viewModel::onImageSelected,
            onTitleChange = viewModel::updateCreateTitle,
            onGridSizeChange = viewModel::updateCreateGridSize,
            onColorCountChange = viewModel::updateCreateColorCount,
            onTrimBackgroundChange = viewModel::updateCreateTrimBackground,
            onCreateBlankPage = viewModel::createBlankPage,
            onEditBlocks = viewModel::openBlockEditorFromCreatePage,
            onSaveAndColor = viewModel::saveAndColorCustomPage,
            onNotify = viewModel::showToast,
            syncState = syncSettingsState,
            onVerifyAdminPin = viewModel::unlockAdminWithPin,
            onSetupAdminPin = viewModel::setupAdminPin,
            onResetAdminPin = viewModel::resetAdminPinAndClearToken,
            onPublishToGitHub = { tpl, localId, onDone ->
              viewModel.publishTemplateToGitHub(tpl, localTemplateId = localId, onComplete = onDone)
            },
          )
        }
        AppScreen.BlockEditor -> {
          val editor = viewModel.blockEditorController
          BlockEditorScreen(
            state = blockEditorState,
            onDiscardAndExit = viewModel::closeBlockEditor,
            onSaveOrApply = viewModel::saveBlockEditorChanges,
            onTitleChange = editor::updateTitle,
            onUndo = editor::undo,
            onRedo = editor::redo,
            onSelectTool = editor::selectTool,
            onToggleFillWithEraser = editor::toggleFillWithEraser,
            onToggleGlobalScope = editor::toggleGlobalScope,
            onSelectShapeType = editor::selectShapeType,
            onToggleShapeFilled = editor::toggleShapeFilled,
            onUpdateShapeDraft = editor::updateShapeDraft,
            onCommitShapeDraft = editor::commitShapeDraft,
            onDiscardShapeDraft = editor::discardShapeDraft,
            onToggleNumbers = editor::toggleNumbers,
            onToggleGridLines = editor::toggleGridLines,
            onTogglePanTool = editor::togglePanTool,
            onStrokeBegin = editor::beginStroke,
            onStrokeStep = editor::stepStroke,
            onTapCellAction = editor::tapCellAction,
            onSelectColor = editor::selectColor,
            onToggleColorVisibility = editor::toggleColorVisibility,
            onShowAllColors = editor::showAllColors,
            onUncolorAll = editor::uncolorAll,
            onSwapColors = editor::swapColors,
            onReorderPalette = editor::reorderPalette,
            onAddColor = editor::addColor,
            onUpdateColorShade = editor::updateColorShade,
            onMergeColor = editor::mergeColor,
            onDeleteUnusedColor = editor::deleteUnusedColor,
            onSelectFrame = editor::selectFrame,
            onAdvancePlaybackFrame = editor::advancePlaybackFrame,
            onTogglePlay = editor::togglePlay,
            onCycleSpeed = editor::cycleSpeed,
            onInsertFrame = editor::insertFrame,
            onDeleteCurrentFrame = editor::deleteCurrentFrame,
            onBuildSnapshotForExport = editor::buildEditedTemplate,
            onNotify = viewModel::showToast,
            syncState = syncSettingsState,
            onVerifyAdminPin = viewModel::unlockAdminWithPin,
            onSetupAdminPin = viewModel::setupAdminPin,
            onResetAdminPin = viewModel::resetAdminPinAndClearToken,
            onPublishToGitHub = { tpl, localId, onDone ->
              viewModel.publishTemplateToGitHub(tpl, localTemplateId = localId, onComplete = onDone)
            },
          )
        }
        AppScreen.StickerBook -> {
          StickerBookScreen(
            profileName = activeProfile?.name ?: "Player 1",
            templates = templates,
            completedIds = progressState.completedIds,
            pages = stickerPages,
            activePageId = activeStickerPageId,
            placedStickers = placedStickers,
            onBack = viewModel::closeStickerBook,
            onSelectPage = viewModel::selectStickerPage,
            onAddPage = viewModel::addStickerPage,
            onCyclePagePaper = viewModel::cycleStickerPagePaper,
            onPlaceSticker = { pageId, templateId, xFrac, yFrac ->
              viewModel.placeSticker(pageId, templateId, xFrac, yFrac)
            },
            onUpdateStickerTransform = { sticker, xFrac, yFrac, scale ->
              viewModel.updateStickerTransform(sticker, xFrac, yFrac, scale)
            },
            onBringStickerToFront = viewModel::bringStickerToFront,
            onToggleMirror = viewModel::toggleStickerMirror,
            onRemoveSticker = viewModel::removePlacedSticker,
            onClearPage = viewModel::clearStickerPage,
            onLockedStickerClick = viewModel::openLockedStickerPicture,
          )
        }
        AppScreen.Settings -> {
          val currentProfile = activeProfile
          if (currentProfile != null) {
            SettingsScreen(
              profile = currentProfile,
              canRemoveKid = profiles.size > 1,
              onSave = viewModel::saveSettings,
              onAddAnotherKid = viewModel::addNewProfile,
              onRemoveKid = viewModel::deleteCurrentProfile,
              onBack = viewModel::closeSettings,
              syncState = syncSettingsState,
              publishableTemplates = templates.filter { it.isCustom },
              syncedTemplates = templates.filter { it.isSyncedOfficial && !it.isCustom },
              onSyncNow = { url -> viewModel.syncCatalogNow(url) },
              onSaveSyncSettings = viewModel::saveSyncSettings,
              onSetupAdminPin = viewModel::setupAdminPin,
              onVerifyAdminPin = viewModel::unlockAdminWithPin,
              onLockAdmin = viewModel::lockAdmin,
              onResetAdminPin = viewModel::resetAdminPinAndClearToken,
              onSaveGitHubPublishConfig = viewModel::saveGitHubPublishConfig,
              onClearGitHubToken = viewModel::clearGitHubPublishToken,
              onPublishTemplate = { tpl, url, folder ->
                viewModel.publishTemplateToGitHub(tpl, sourceUrlOverride = url, folderOverride = folder)
              },
              onPublishAll = { list, url, folder ->
                viewModel.publishAllTemplatesToGitHub(list, sourceUrlOverride = url, folderOverride = folder)
              },
              customQuestions = customQuestions,
              onSaveCustomQuestion = viewModel::saveCustomQuestion,
              onDeleteCustomQuestion = viewModel::deleteOrRevertCustomQuestion,
              onPublishQuestion = { q, url, folder ->
                viewModel.publishQuestionToGitHub(q, sourceUrlOverride = url, folderOverride = folder)
              },
              onPublishAllQuestions = { qList, url, folder ->
                viewModel.publishAllQuestionsToGitHub(qList, sourceUrlOverride = url, folderOverride = folder)
              },
              customLevels = customLevels,
              onSaveCustomLevel = viewModel::saveCustomLevel,
              onDeleteCustomLevel = viewModel::deleteOrRevertCustomLevel,
              onPublishLevel = { lvl, url, folder ->
                viewModel.publishLevelToGitHub(lvl, sourceUrlOverride = url, folderOverride = folder)
              },
              onPublishAllLevels = { list, url, folder ->
                viewModel.publishAllLevelsToGitHub(list, sourceUrlOverride = url, folderOverride = folder)
              },
              onToggleSound = viewModel::toggleSound,
            )
          }
        }
      }
    }

    if (showProfiles) {
      ProfileSwitcherDialog(
        profiles = profiles,
        activeProfileId = activeProfileId,
        onSelectProfile = viewModel::switchProfile,
        onAddNewKid = viewModel::addNewProfile,
        onDismiss = viewModel::closeProfilesDialog,
      )
    }

    val activeChallenge = challengeState
    if (activeChallenge != null) {
      ChallengeDialog(
        challenge = activeChallenge,
        onSelectOption = viewModel::submitChallengeAnswer,
        onDismiss = viewModel::dismissChallenge,
      )
    }

    AnimatedVisibility(
      visible = toastMessage != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = IndigoDeep.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
      ) {
        Text(
          text = toastMessage.orEmpty(),
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
      }
    }
  }
}
