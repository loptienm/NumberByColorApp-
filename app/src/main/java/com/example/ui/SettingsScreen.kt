package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.KidProfile
import com.example.model.PictureTemplate
import com.example.ui.theme.BlossomTint
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SkyTint
import com.example.ui.theme.SlateMuted

enum class SettingsRoute(val titleRes: Int) {
  ROOT(R.string.settings_title),
  PROFILE(R.string.settings_nav_profile),
  ADMIN_MENU(R.string.settings_nav_admin),
  SERVER_SETUP(R.string.settings_nav_server),
  EDUCATION_LEVELS(R.string.settings_nav_levels),
  CUSTOM_QUESTIONS(R.string.settings_nav_questions),
  CUSTOM_PAGES(R.string.settings_nav_pages);

  fun parentRoute(): SettingsRoute? =
    when (this) {
      ROOT -> null
      PROFILE, ADMIN_MENU -> ROOT
      SERVER_SETUP, EDUCATION_LEVELS, CUSTOM_QUESTIONS, CUSTOM_PAGES -> ADMIN_MENU
    }
}

@Composable
fun SettingsScreen(
  profile: KidProfile,
  canRemoveKid: Boolean,
  onSave: (name: String, avatar: String, mode: ChallengeMode, customLevelId: String?) -> Unit,
  onAddAnotherKid: () -> Unit,
  onRemoveKid: () -> Unit,
  onBack: () -> Unit,
  initialRoute: SettingsRoute = SettingsRoute.ROOT,
  syncState: SyncSettingsUiState = SyncSettingsUiState(),
  publishableTemplates: List<PictureTemplate> = emptyList(),
  syncedTemplates: List<PictureTemplate> = emptyList(),
  onSyncNow: (String) -> Unit = {},
  onSaveSyncSettings: (syncUrl: String, wifiOnlyAutoSync: Boolean) -> Unit = { _, _ -> },
  onSetupAdminPin: (String) -> Boolean = { false },
  onVerifyAdminPin: (String) -> Boolean = { false },
  onLockAdmin: () -> Unit = {},
  onResetAdminPin: () -> Unit = {},
  onSaveGitHubPublishConfig: (tokenInput: String?, publishFolder: String) -> Unit = { _, _ -> },
  onClearGitHubToken: () -> Unit = {},
  onPublishTemplate: (template: PictureTemplate, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  onPublishAll: (templates: List<PictureTemplate>, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  customQuestions: List<CustomQuestion> = emptyList(),
  onSaveCustomQuestion: (question: CustomQuestion, editingExisting: CustomQuestion?) -> Unit = { _, _ -> },
  onDeleteCustomQuestion: (String) -> Unit = {},
  onPublishQuestion: (question: CustomQuestion, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  onPublishAllQuestions: (questions: List<CustomQuestion>, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  customLevels: List<CustomEducationLevel> = emptyList(),
  onSaveCustomLevel: (level: CustomEducationLevel, editingExisting: CustomEducationLevel?) -> Unit = { _, _ -> },
  onDeleteCustomLevel: (String) -> Unit = {},
  onPublishLevel: (level: CustomEducationLevel, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  onPublishAllLevels: (levels: List<CustomEducationLevel>, syncUrl: String, folder: String) -> Unit = { _, _, _ -> },
  onToggleSound: () -> Unit = {},
) {
  var currentRoute by rememberSaveable { mutableStateOf(initialRoute) }

  fun handleBack() {
    val parent = currentRoute.parentRoute()
    if (parent != null) {
      currentRoute = parent
    } else {
      onBack()
    }
  }

  BackHandler { handleBack() }

  var name by remember(profile.id) { mutableStateOf(profile.name) }
  var selectedAvatar by remember(profile.id) { mutableStateOf(profile.avatar) }
  var selectedMode by remember(profile.id) { mutableStateOf(profile.challengeMode) }
  var selectedCustomLevelId by remember(profile.id, profile.customLevelId) {
    mutableStateOf(profile.customLevelId)
  }
  var confirmDelete by remember { mutableStateOf(false) }

  var syncUrl by remember(syncState.sourceUrl) { mutableStateOf(syncState.sourceUrl) }
  var wifiOnlyAutoSync by remember(syncState.wifiOnlyAutoSync) { mutableStateOf(syncState.wifiOnlyAutoSync) }
  var patInput by remember { mutableStateOf("") }
  var publishFolder by remember(syncState.publishFolder) { mutableStateOf(syncState.publishFolder) }
  var showChangePinDialog by remember { mutableStateOf(false) }
  var showQuestionEditor by remember { mutableStateOf(false) }
  var editingQuestion by remember { mutableStateOf<CustomQuestion?>(null) }
  var showLevelEditor by remember { mutableStateOf(false) }
  var editingLevel by remember { mutableStateOf<CustomEducationLevel?>(null) }

  val activeLevelName =
    remember(selectedCustomLevelId, customLevels, selectedMode) {
      customLevels.firstOrNull { it.id == selectedCustomLevelId }?.name ?: selectedMode.label
    }

  fun persistPendingConfig() {
    onSaveSyncSettings(syncUrl, wifiOnlyAutoSync)
    if (syncState.isAdminUnlocked) {
      onSaveGitHubPublishConfig(patInput.ifBlank { null }, publishFolder)
      if (patInput.isNotBlank()) patInput = ""
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.linearGradient(colors = listOf(SkyTint, BlossomTint)))
        .testTag("settings_screen"),
  ) {
    Surface(color = Color.White.copy(alpha = 0.90f), shadowElevation = 3.dp) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        IconButton(
          onClick = ::handleBack,
          modifier = Modifier.size(48.dp).testTag("settings_back_button"),
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
            text = stringResource(currentRoute.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = IndigoDeep,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$selectedAvatar ${name.ifBlank { profile.name }} • $activeLevelName",
            style = MaterialTheme.typography.bodyMedium,
            color = IndigoPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Button(
          onClick = {
            persistPendingConfig()
            onSave(name, selectedAvatar, selectedMode, selectedCustomLevelId)
          },
          modifier = Modifier.height(44.dp).testTag("settings_save_button"),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
        ) {
          Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.save_short),
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }

    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val isWide = maxWidth >= 680.dp
      val horizontalPad = if (isWide) 24.dp else 16.dp
      val verticalPad = if (isWide) 18.dp else 14.dp

      Box(
        modifier =
          Modifier.fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad, vertical = verticalPad),
        contentAlignment = Alignment.TopCenter,
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 720.dp)
              .testTag(if (isWide) "settings_tablet_container" else "settings_phone_container"),
        ) {
          when (currentRoute) {
            SettingsRoute.ROOT -> {
              SettingsRootMenu(
                avatar = selectedAvatar,
                kidName = name.ifBlank { profile.name },
                activeLevelLabel = activeLevelName,
                soundEnabled = profile.soundEnabled,
                syncState = syncState,
                customLevelsCount = customLevels.size,
                customQuestionsCount = customQuestions.size,
                customPagesCount = publishableTemplates.size + syncedTemplates.size,
                onOpenProfile = { currentRoute = SettingsRoute.PROFILE },
                onOpenAdmin = { currentRoute = SettingsRoute.ADMIN_MENU },
              )
            }
            SettingsRoute.PROFILE -> {
              ProfileSettingsPage(
                name = name,
                onNameChange = { name = it },
                selectedAvatar = selectedAvatar,
                onSelectAvatar = { selectedAvatar = it },
                selectedMode = selectedMode,
                selectedCustomLevelId = selectedCustomLevelId,
                customLevels = customLevels,
                onSelectLevel = { mode, customId ->
                  selectedMode = mode
                  selectedCustomLevelId = customId
                },
                soundEnabled = profile.soundEnabled,
                onToggleSound = onToggleSound,
                canRemoveKid = canRemoveKid,
                onAddKidClick = {
                  persistPendingConfig()
                  onSave(name, selectedAvatar, selectedMode, selectedCustomLevelId)
                  onAddAnotherKid()
                },
                onRequestRemoveKid = { confirmDelete = true },
              )
            }
            SettingsRoute.ADMIN_MENU -> {
              if (!syncState.isAdminUnlocked) {
                ParentPinGatePanel(
                  hasExistingPin = syncState.hasAdminPin,
                  onSetupPin = onSetupAdminPin,
                  onVerifyPin = onVerifyAdminPin,
                  onResetPinAndToken = onResetAdminPin,
                )
              } else {
                AdminHubMenu(
                  syncState = syncState,
                  publishFolder = publishFolder,
                  customLevelsCount = customLevels.size,
                  customQuestionsCount = customQuestions.size,
                  pendingPagesCount = publishableTemplates.size,
                  syncedPagesCount = syncedTemplates.size,
                  onChangePin = { showChangePinDialog = true },
                  onLockAdmin = onLockAdmin,
                  onOpenServer = { currentRoute = SettingsRoute.SERVER_SETUP },
                  onOpenLevels = { currentRoute = SettingsRoute.EDUCATION_LEVELS },
                  onOpenQuestions = { currentRoute = SettingsRoute.CUSTOM_QUESTIONS },
                  onOpenPages = { currentRoute = SettingsRoute.CUSTOM_PAGES },
                )
              }
            }
            SettingsRoute.SERVER_SETUP -> {
              if (!syncState.isAdminUnlocked) {
                ParentPinGatePanel(
                  hasExistingPin = syncState.hasAdminPin,
                  onSetupPin = onSetupAdminPin,
                  onVerifyPin = onVerifyAdminPin,
                  onResetPinAndToken = onResetAdminPin,
                )
              } else {
                ServerSettingsPage(
                  syncState = syncState,
                  syncUrl = syncUrl,
                  onSyncUrlChange = { syncUrl = it },
                  wifiOnlyAutoSync = wifiOnlyAutoSync,
                  onWifiOnlyChange = { wifiOnlyAutoSync = it },
                  patInput = patInput,
                  onPatInputChange = { patInput = it },
                  publishFolder = publishFolder,
                  onPublishFolderChange = { publishFolder = it },
                  onSaveConfig = { persistPendingConfig() },
                  onClearToken = {
                    patInput = ""
                    onClearGitHubToken()
                  },
                  onSyncNow = {
                    onSaveSyncSettings(syncUrl, wifiOnlyAutoSync)
                    onSyncNow(syncUrl)
                  },
                  onChangePin = { showChangePinDialog = true },
                  onLockAdmin = onLockAdmin,
                )
              }
            }
            SettingsRoute.EDUCATION_LEVELS -> {
              if (!syncState.isAdminUnlocked) {
                ParentPinGatePanel(
                  hasExistingPin = syncState.hasAdminPin,
                  onSetupPin = onSetupAdminPin,
                  onVerifyPin = onVerifyAdminPin,
                  onResetPinAndToken = onResetAdminPin,
                )
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                  AdminUnlockedBar(onChangePin = {}, onLockAdmin = onLockAdmin, showChangePin = false)
                  SettingsSectionCard {
                    CustomLevelsAdminSection(
                      levels = customLevels,
                      customQuestions = customQuestions,
                      syncState = syncState,
                      onCreateNew = {
                        editingLevel = null
                        showLevelEditor = true
                      },
                      onEditLevel = { lvl ->
                        editingLevel = lvl
                        showLevelEditor = true
                      },
                      onDeleteOrRevertLevel = onDeleteCustomLevel,
                      onPublishLevel = { lvl ->
                        persistPendingConfig()
                        onPublishLevel(lvl, syncUrl, publishFolder)
                      },
                      onPublishAllLevels = { list ->
                        persistPendingConfig()
                        onPublishAllLevels(list, syncUrl, publishFolder)
                      },
                    )
                  }
                  SyncStatusCard(syncState = syncState)
                }
              }
            }
            SettingsRoute.CUSTOM_QUESTIONS -> {
              if (!syncState.isAdminUnlocked) {
                ParentPinGatePanel(
                  hasExistingPin = syncState.hasAdminPin,
                  onSetupPin = onSetupAdminPin,
                  onVerifyPin = onVerifyAdminPin,
                  onResetPinAndToken = onResetAdminPin,
                )
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                  AdminUnlockedBar(onChangePin = {}, onLockAdmin = onLockAdmin, showChangePin = false)
                  SettingsSectionCard {
                    CustomQuestionsAdminSection(
                      questions = customQuestions,
                      syncState = syncState,
                      onCreateNew = {
                        editingQuestion = null
                        showQuestionEditor = true
                      },
                      onEditQuestion = { q ->
                        editingQuestion = q
                        showQuestionEditor = true
                      },
                      onDeleteOrRevertQuestion = onDeleteCustomQuestion,
                      onPublishQuestion = { q ->
                        persistPendingConfig()
                        onPublishQuestion(q, syncUrl, publishFolder)
                      },
                      onPublishAllQuestions = { qList ->
                        persistPendingConfig()
                        onPublishAllQuestions(qList, syncUrl, publishFolder)
                      },
                      customLevels = customLevels,
                    )
                  }
                  SyncStatusCard(syncState = syncState)
                }
              }
            }
            SettingsRoute.CUSTOM_PAGES -> {
              if (!syncState.isAdminUnlocked) {
                ParentPinGatePanel(
                  hasExistingPin = syncState.hasAdminPin,
                  onSetupPin = onSetupAdminPin,
                  onVerifyPin = onVerifyAdminPin,
                  onResetPinAndToken = onResetAdminPin,
                )
              } else {
                PagesSettingsPage(
                  syncState = syncState,
                  publishableTemplates = publishableTemplates,
                  syncedTemplates = syncedTemplates,
                  onSyncNow = {
                    onSaveSyncSettings(syncUrl, wifiOnlyAutoSync)
                    onSyncNow(syncUrl)
                  },
                  onPublishTemplate = { tpl ->
                    persistPendingConfig()
                    onPublishTemplate(tpl, syncUrl, publishFolder)
                  },
                  onPublishAll = { list ->
                    persistPendingConfig()
                    onPublishAll(list, syncUrl, publishFolder)
                  },
                  onLockAdmin = onLockAdmin,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showLevelEditor) {
    CustomLevelEditorDialog(
      initialLevel = editingLevel,
      onSave = onSaveCustomLevel,
      onDismiss = {
        showLevelEditor = false
        editingLevel = null
      },
    )
  }

  if (showQuestionEditor) {
    CustomQuestionEditorDialog(
      initialQuestion = editingQuestion,
      defaultMode = selectedMode,
      onSave = onSaveCustomQuestion,
      onDismiss = {
        showQuestionEditor = false
        editingQuestion = null
      },
      customLevels = customLevels,
      defaultCustomLevelId = selectedCustomLevelId,
    )
  }

  if (showChangePinDialog) {
    ParentPinPromptDialog(
      hasExistingPin = false,
      titleOverride = "Change Parent PIN",
      onSetupPin = { newPin ->
        val ok = onSetupAdminPin(newPin)
        if (ok) showChangePinDialog = false
        ok
      },
      onVerifyPin = { false },
      onDismiss = { showChangePinDialog = false },
    )
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Remove ${profile.name}?") },
      text = { Text("This will remove ${profile.name}'s saved pictures and stickers.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            onRemoveKid()
          }
        ) {
          Text("Remove", color = CoralError, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun SettingsRootMenu(
  avatar: String,
  kidName: String,
  activeLevelLabel: String,
  soundEnabled: Boolean,
  syncState: SyncSettingsUiState,
  customLevelsCount: Int,
  customQuestionsCount: Int,
  customPagesCount: Int,
  onOpenProfile: () -> Unit,
  onOpenAdmin: () -> Unit,
) {
  val soundLabel = if (soundEnabled) "Sound on" else "Muted"
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SettingsSectionCard {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_profile),
        subtitle = "$avatar $kidName • $activeLevelLabel • $soundLabel",
        icon = Icons.Filled.Person,
        onClick = onOpenProfile,
        testTag = "settings_row_profile",
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_admin),
        subtitle = "Server setup, education levels, custom questions & pages",
        icon = Icons.Filled.AdminPanelSettings,
        onClick = onOpenAdmin,
        testTag = "settings_row_admin",
        badgeText = if (syncState.isAdminUnlocked) "Unlocked" else "PIN",
        trailingIcon = if (syncState.isAdminUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
        trailingTint = if (syncState.isAdminUnlocked) EmeraldSuccess else IndigoPrimary,
      )
    }

    SettingsSectionCard {
      Text(
        text = "Library Summary",
        style = MaterialTheme.typography.titleMedium,
        color = IndigoDeep,
      )
      Text(
        text = "$customLevelsCount custom level(s) • $customQuestionsCount custom question(s) • $customPagesCount custom/synced page(s)",
        style = MaterialTheme.typography.bodyMedium,
        color = SlateMuted,
      )
    }
  }
}

@Composable
private fun AdminHubMenu(
  syncState: SyncSettingsUiState,
  publishFolder: String,
  customLevelsCount: Int,
  customQuestionsCount: Int,
  pendingPagesCount: Int,
  syncedPagesCount: Int,
  onChangePin: () -> Unit,
  onLockAdmin: () -> Unit,
  onOpenServer: () -> Unit,
  onOpenLevels: () -> Unit,
  onOpenQuestions: () -> Unit,
  onOpenPages: () -> Unit,
) {
  val normalizedFolder = publishFolder.trim('/').ifBlank { "root" }
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    AdminUnlockedBar(onChangePin = onChangePin, onLockAdmin = onLockAdmin, showChangePin = true)

    SettingsSectionCard {
      Text(
        text = "Admin",
        style = MaterialTheme.typography.titleLarge,
        color = IndigoDeep,
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_server),
        subtitle = "GitHub repo URL, PAT token, folder ($normalizedFolder) & Wi-Fi sync",
        icon = Icons.Filled.CloudSync,
        onClick = onOpenServer,
        testTag = "settings_row_server",
        badgeText = if (syncState.hasPublishToken) "PAT set" else null,
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_levels),
        subtitle = "Create practice targets & assign built-in topics or questions",
        icon = Icons.Filled.School,
        onClick = onOpenLevels,
        testTag = "settings_row_levels",
        badgeText = if (customLevelsCount > 0) "$customLevelsCount" else null,
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_questions),
        subtitle = "Write math, spelling, or language questions & publish",
        icon = Icons.Filled.Quiz,
        onClick = onOpenQuestions,
        testTag = "settings_row_questions",
        badgeText = if (customQuestionsCount > 0) "$customQuestionsCount" else null,
      )
      SettingsMenuRow(
        title = stringResource(R.string.settings_nav_pages),
        subtitle = "Sync & publish coloring pages ($pendingPagesCount pending, $syncedPagesCount synced)",
        icon = Icons.Filled.Palette,
        onClick = onOpenPages,
        testTag = "settings_row_pages",
        badgeText =
          when {
            pendingPagesCount > 0 -> "$pendingPagesCount new"
            syncedPagesCount > 0 -> "$syncedPagesCount"
            else -> null
          },
      )
    }

    SyncStatusCard(syncState = syncState)
  }
}
