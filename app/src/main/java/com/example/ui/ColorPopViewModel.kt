package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ALL_BUILT_IN_TEMPLATES
import com.example.data.ColorPopDatabase
import com.example.data.ColorPopRepository
import com.example.data.ProfileImportConflictMode
import com.example.data.ProfileProgressState
import com.example.engine.ChallengeGenerator
import com.example.engine.ChallengeQuestion
import com.example.engine.GifFrameDecoder
import com.example.engine.ImageToGridConverter
import com.example.engine.LevelExportEngine
import com.example.engine.PageExportEngine
import com.example.engine.ProfileExportBundle
import com.example.engine.ProfileExportEngine
import com.example.engine.PublishLevelResult
import com.example.engine.QuestionExportEngine
import com.example.engine.RemoteCatalogSyncEngine
import com.example.engine.SoundEngine
import com.example.model.BUCKET_COLOR_LIMIT
import com.example.model.BUCKET_REFILL_COST
import com.example.model.BrushType
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.KID_AVATARS
import com.example.model.KidProfile
import com.example.model.PictureTemplate
import com.example.model.PlacedSticker
import com.example.model.StickerPage
import com.example.model.StickerPagePaper
import com.example.model.clampStickerScale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
  data object Home : AppScreen

  data object Play : AppScreen

  data object CreatePage : AppScreen

  data object BlockEditor : AppScreen

  data object StickerBook : AppScreen

  data object Settings : AppScreen
}

data class ChallengeUiState(
  val targetBrush: BrushType,
  val isRefill: Boolean,
  val totalRequired: Int,
  val completedCount: Int,
  val question: ChallengeQuestion,
  val wrongOptions: Set<String> = emptySet(),
  val solvedCorrectly: Boolean = false,
)

data class PlayUiState(
  val template: PictureTemplate = ALL_BUILT_IN_TEMPLATES.first(),
  val paintedGrid: IntArray = IntArray(0),
  val paintRevision: Long = 0L,
  val activeColorNumber: Int = 1,
  val activeBrush: BrushType = BrushType.DOT,
  val unlockedBrushes: Set<BrushType> = setOf(BrushType.DOT),
  val brushProgress: Map<BrushType, Int> = emptyMap(),
  val refillProgress: Int = 0,
  val bucketColorsUsed: Set<Int> = emptySet(),
  val isCompleted: Boolean = false,
  val danceFrame: Int = 0,
  val remainingByColor: Map<Int, Int> = emptyMap(),
  val progressFraction: Float = 0f,
)

data class CreatePageUiState(
  val sourceUri: Uri? = null,
  val sourceBitmap: Bitmap? = null,
  val sourceFrames: List<Bitmap> = emptyList(),
  val sourceFrameMs: Int = 400,
  val previewTemplate: PictureTemplate? = null,
  val detectedPixelSizePx: Float? = null,
  val title: String = "My Drawing",
  val gridSize: Int = 0,
  val colorCount: Int = 0,
  val trimBackground: Boolean = true,
  val isProcessing: Boolean = false,
  val errorMessage: String? = null,
)

data class SyncSettingsUiState(
  val sourceUrl: String = RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL,
  val wifiOnlyAutoSync: Boolean = true,
  val lastSyncAtMs: Long = 0L,
  val lastSyncStatus: String = "",
  val isSyncing: Boolean = false,
  val syncedCount: Int = 0,
  val syncedQuestionsCount: Int = 0,
  val syncedLevelsCount: Int = 0,
  val hasAdminPin: Boolean = false,
  val isAdminUnlocked: Boolean = false,
  val hasPublishToken: Boolean = false,
  val maskedPublishToken: String = "",
  val publishFolder: String = "pages",
  val isPublishing: Boolean = false,
  val isPublishingAll: Boolean = false,
  val publishingTemplateId: String? = null,
  val publishingQuestionId: String? = null,
  val publishingLevelId: String? = null,
  val publishProgressText: String = "",
  val lastPublishStatus: String = "",
)

private fun templatesHaveSameArtwork(a: PictureTemplate, b: PictureTemplate): Boolean {
  return a.title.trim() == b.title.trim() &&
    a.gridWidth == b.gridWidth &&
    a.gridHeight == b.gridHeight &&
    a.palette == b.palette &&
    a.grid == b.grid &&
    a.frames == b.frames
}

fun mergeTemplates(
  customList: List<PictureTemplate>,
  syncedList: List<PictureTemplate>,
  builtInList: List<PictureTemplate> = ALL_BUILT_IN_TEMPLATES,
): List<PictureTemplate> {
  val customById = customList.associateBy { it.id }
  val syncedById = syncedList.associateBy { it.id }
  val builtInIds = builtInList.mapTo(mutableSetOf()) { it.id }
  val syncedIds = syncedById.keys

  val pureCustom = customList.filter { it.id !in builtInIds && it.id !in syncedIds }
  val newSyncedOfficial =
    syncedList
      .filter { it.id !in builtInIds }
      .map { synced ->
        val custom = customById[synced.id]
        if (custom != null && custom.version >= synced.version && !templatesHaveSameArtwork(custom, synced)) {
          custom
        } else {
          synced
        }
      }
  val resolvedBuiltIn =
    builtInList.map { builtIn ->
      val customOverride = customById[builtIn.id]
      val syncedOverride = syncedById[builtIn.id]
      if (customOverride != null) {
        if (
          syncedOverride != null &&
            (syncedOverride.version > customOverride.version ||
              (syncedOverride.version >= customOverride.version &&
                templatesHaveSameArtwork(customOverride, syncedOverride)))
        ) {
          syncedOverride
        } else {
          customOverride
        }
      } else {
        if (syncedOverride != null && syncedOverride.version >= builtIn.version) {
          syncedOverride
        } else {
          builtIn
        }
      }
    }

  val seenIds = mutableSetOf<String>()
  return (pureCustom + newSyncedOfficial + resolvedBuiltIn).filter { seenIds.add(it.id) }
}

private fun questionsHaveSameContent(a: CustomQuestion, b: CustomQuestion): Boolean {
  return a.prompt.trim() == b.prompt.trim() &&
    a.clueItems == b.clueItems &&
    a.correctAnswer.trim() == b.correctAnswer.trim() &&
    a.wrongAnswers == b.wrongAnswers &&
    a.levels == b.levels &&
    a.customLevelIds == b.customLevelIds &&
    a.isSentence == b.isSentence
}

fun mergeQuestions(
  customList: List<CustomQuestion>,
  syncedList: List<CustomQuestion>,
): List<CustomQuestion> {
  val customById = customList.associateBy { it.id }
  val syncedById = syncedList.associateBy { it.id }
  val syncedIds = syncedById.keys

  val pureCustom = customList.filter { it.id !in syncedIds }
  val resolvedSynced =
    syncedList.map { synced ->
      val custom = customById[synced.id]
      if (custom != null && custom.version >= synced.version && !questionsHaveSameContent(custom, synced)) {
        custom
      } else {
        synced
      }
    }

  val seenIds = mutableSetOf<String>()
  return (pureCustom + resolvedSynced).filter { seenIds.add(it.id) }
}

private fun levelsHaveSameContent(a: CustomEducationLevel, b: CustomEducationLevel): Boolean {
  return a.name.trim() == b.name.trim() &&
    a.description.trim() == b.description.trim() &&
    a.topics == b.topics
}

fun mergeLevels(
  customList: List<CustomEducationLevel>,
  syncedList: List<CustomEducationLevel>,
): List<CustomEducationLevel> {
  val customById = customList.associateBy { it.id }
  val syncedById = syncedList.associateBy { it.id }
  val syncedIds = syncedById.keys

  val pureCustom = customList.filter { it.id !in syncedIds }
  val resolvedSynced =
    syncedList.map { synced ->
      val custom = customById[synced.id]
      if (custom != null && custom.version >= synced.version && !levelsHaveSameContent(custom, synced)) {
        custom
      } else {
        synced
      }
    }

  val seenIds = mutableSetOf<String>()
  return (pureCustom + resolvedSynced).filter { seenIds.add(it.id) }
}

class ColorPopViewModel(application: Application) : AndroidViewModel(application) {
  private val repository =
    ColorPopRepository(ColorPopDatabase.getInstance(application), application)
  val sounds = SoundEngine()

  private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
  val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

  private val _activeProfileId = MutableStateFlow("p1")
  val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

  private val _showProfilesDialog = MutableStateFlow(false)
  val showProfilesDialog: StateFlow<Boolean> = _showProfilesDialog.asStateFlow()

  private val _settingsEntryRoute = MutableStateFlow(SettingsRoute.ROOT)
  val settingsEntryRoute: StateFlow<SettingsRoute> = _settingsEntryRoute.asStateFlow()

  private val _settingsDirectProfileBackToExit = MutableStateFlow(false)
  val settingsDirectProfileBackToExit: StateFlow<Boolean> =
    _settingsDirectProfileBackToExit.asStateFlow()

  private val _settingsRouteNonce = MutableStateFlow(0)
  val settingsRouteNonce: StateFlow<Int> = _settingsRouteNonce.asStateFlow()

  private val _profileImportPreview = MutableStateFlow<ProfileImportPreviewUiState?>(null)
  val profileImportPreview: StateFlow<ProfileImportPreviewUiState?> =
    _profileImportPreview.asStateFlow()

  private var settingsOriginScreen: AppScreen = AppScreen.Home

  private val _challengeState = MutableStateFlow<ChallengeUiState?>(null)
  val challengeState: StateFlow<ChallengeUiState?> = _challengeState.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _playState = MutableStateFlow(PlayUiState())
  val playState: StateFlow<PlayUiState> = _playState.asStateFlow()

  private val _createPageState = MutableStateFlow(CreatePageUiState())
  val createPageState: StateFlow<CreatePageUiState> = _createPageState.asStateFlow()

  val blockEditorController = BlockEditorController(onToast = ::showToast)
  val blockEditorState: StateFlow<BlockEditorUiState> = blockEditorController.state

  val profiles: StateFlow<List<KidProfile>> =
    repository.profilesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  private val _syncSettingsState =
    MutableStateFlow(
      SyncSettingsUiState(
        sourceUrl = repository.getSyncSourceUrl(),
        wifiOnlyAutoSync = repository.isWifiOnlyAutoSync(),
        lastSyncAtMs = repository.getLastSyncAtMs(),
        lastSyncStatus = repository.getLastSyncStatus(),
        hasAdminPin = repository.hasAdminPin(),
        isAdminUnlocked = false,
        hasPublishToken = repository.getGitHubPublishToken().isNotBlank(),
        maskedPublishToken = repository.getMaskedPublishToken(),
        publishFolder = repository.getPublishFolder(),
      )
    )
  val syncSettingsState: StateFlow<SyncSettingsUiState> = _syncSettingsState.asStateFlow()

  private sealed interface OptimisticProgress {
    data object Completed : OptimisticProgress

    class InProgress(val grid: Array<IntArray>) : OptimisticProgress

    data object Cleared : OptimisticProgress
  }

  private val _optimisticProgress =
    MutableStateFlow<Map<Pair<String, String>, OptimisticProgress>>(emptyMap())

  val allTemplates: StateFlow<List<PictureTemplate>> =
    combine(
      repository.customTemplatesFlow,
      repository.syncedTemplatesFlow,
    ) { custom, synced ->
      mergeTemplates(customList = custom, syncedList = synced, builtInList = ALL_BUILT_IN_TEMPLATES)
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ALL_BUILT_IN_TEMPLATES,
      )

  val allQuestions: StateFlow<List<CustomQuestion>> =
    combine(
      repository.customQuestionsFlow,
      repository.syncedQuestionsFlow,
    ) { custom, synced ->
      mergeQuestions(customList = custom, syncedList = synced)
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
      )

  val allLevels: StateFlow<List<CustomEducationLevel>> =
    combine(
      repository.customLevelsFlow,
      repository.syncedLevelsFlow,
    ) { custom, synced ->
      mergeLevels(customList = custom, syncedList = synced)
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val progressState: StateFlow<ProfileProgressState> =
    _activeProfileId
      .flatMapLatest { profileId ->
        combine(
          repository.observeProgressRows(profileId).onStart { emit(emptyList()) },
          allTemplates,
          _optimisticProgress,
        ) { rows, templates, optimisticMap ->
          val lookup = templates.associateBy { it.id }
          val base = repository.computeProgressState(rows, lookup)
          val profileOverrides = optimisticMap.filterKeys { it.first == profileId }
          if (profileOverrides.isEmpty()) {
            base
          } else {
            val completed = base.completedIds.toMutableSet()
            val inProgress = base.inProgressGrids.toMutableMap()
            profileOverrides.forEach { (key, override) ->
              val picId = key.second
              when (override) {
                is OptimisticProgress.Completed -> {
                  completed.add(picId)
                  inProgress.remove(picId)
                }
                is OptimisticProgress.InProgress -> {
                  completed.remove(picId)
                  inProgress[picId] = override.grid
                }
                is OptimisticProgress.Cleared -> {
                  completed.remove(picId)
                  inProgress.remove(picId)
                }
              }
            }
            ProfileProgressState(completedIds = completed, inProgressGrids = inProgress)
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProfileProgressState(),
      )

  val activeProfile: StateFlow<KidProfile?> =
    combine(profiles, _activeProfileId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val stickerPages: StateFlow<List<StickerPage>> =
    _activeProfileId
      .flatMapLatest { profileId -> repository.observeStickerPages(profileId) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  private val _pendingStickers = MutableStateFlow<Map<String, PlacedSticker?>>(emptyMap())

  @OptIn(ExperimentalCoroutinesApi::class)
  val placedStickers: StateFlow<List<PlacedSticker>> =
    _activeProfileId
      .flatMapLatest { profileId ->
        combine(repository.observePlacedStickers(profileId), _pendingStickers) { dbList, pending ->
          if (pending.isEmpty()) {
            dbList
          } else {
            val merged = linkedMapOf<String, PlacedSticker>()
            dbList.forEach { merged[it.id] = it }
            pending.forEach { (id, override) ->
              if (override == null) merged.remove(id) else merged[id] = override
            }
            merged.values.sortedBy { it.zOrder }
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  private val _activeStickerPageId = MutableStateFlow<String?>(null)
  val activeStickerPageId: StateFlow<String?> = _activeStickerPageId.asStateFlow()

  private val stickerIdCounter = AtomicLong(0L)

  private var saveJob: Job? = null
  private var danceJob: Job? = null
  private var toastJob: Job? = null
  private var convertJob: Job? = null
  private var syncJob: Job? = null

  init {
    viewModelScope.launch {
      val initialId = repository.ensureDefaultProfile()
      _activeProfileId.value = initialId
      repository.ensureStickerPages(initialId)
      val all = repository.profilesFlow
      all.collect { list ->
        val current = list.firstOrNull { it.id == _activeProfileId.value } ?: list.firstOrNull()
        if (current != null) {
          sounds.enabled = current.soundEnabled
        }
      }
    }
    viewModelScope.launch {
      repository.syncedTemplatesFlow.collect { list ->
        _syncSettingsState.update { it.copy(syncedCount = list.size) }
      }
    }
    viewModelScope.launch {
      repository.syncedQuestionsFlow.collect { list ->
        _syncSettingsState.update { it.copy(syncedQuestionsCount = list.size) }
      }
    }
    viewModelScope.launch {
      repository.syncedLevelsFlow.collect { list ->
        _syncSettingsState.update { it.copy(syncedLevelsCount = list.size) }
      }
    }
    maybeAutoSyncOnWifi()
  }

  fun openProfilesDialog() {
    _showProfilesDialog.value = true
  }

  fun closeProfilesDialog() {
    _showProfilesDialog.value = false
  }

  fun openStickerBook() {
    val profileId = _activeProfileId.value
    viewModelScope.launch { repository.ensureStickerPages(profileId) }
    _currentScreen.value = AppScreen.StickerBook
  }

  fun closeStickerBook() {
    _currentScreen.value = AppScreen.Home
  }

  fun openSettings() {
    val current = _currentScreen.value
    if (current != AppScreen.Settings) {
      if (current == AppScreen.Play) {
        saveCurrentProgressNow()
      }
      settingsOriginScreen = current
      _settingsEntryRoute.value = SettingsRoute.ROOT
      _settingsDirectProfileBackToExit.value = false
      _settingsRouteNonce.value += 1
      _currentScreen.value = AppScreen.Settings
    }
  }

  fun closeSettings() {
    val target = if (settingsOriginScreen == AppScreen.Settings) AppScreen.Home else settingsOriginScreen
    _currentScreen.value = target
  }

  fun switchProfile(profileId: String) {
    saveCurrentProgressNow()
    _pendingStickers.value = emptyMap()
    _activeProfileId.value = profileId
    _activeStickerPageId.value = null
    repository.setActiveProfileId(profileId)
    viewModelScope.launch { repository.ensureStickerPages(profileId) }
    val chosen = profiles.value.firstOrNull { it.id == profileId }
    if (chosen != null) sounds.enabled = chosen.soundEnabled
    _showProfilesDialog.value = false
    if (_currentScreen.value == AppScreen.Play) {
      _currentScreen.value = AppScreen.Home
    }
  }

  private suspend fun createAndOpenNewProfile(wasAlreadyInSettings: Boolean) {
    saveCurrentProgressNow()
    val nextIndex = profiles.value.size + 1
    val existingIds = profiles.value.mapTo(mutableSetOf()) { it.id }
    var newId = ProfileExportEngine.generateProfileId()
    while (newId in existingIds) {
      newId = ProfileExportEngine.generateProfileId()
    }
    val newProfile =
      KidProfile(
        id = newId,
        name = "Kid $nextIndex",
        avatar = KID_AVATARS[(nextIndex - 1) % KID_AVATARS.size],
        challengeMode = ChallengeMode.PRE_K,
        soundEnabled = true,
      )
    repository.upsertProfile(newProfile)
    repository.ensureStickerPages(newProfile.id)
    _pendingStickers.value = emptyMap()
    _activeStickerPageId.value = null
    _activeProfileId.value = newProfile.id
    repository.setActiveProfileId(newProfile.id)
    sounds.enabled = true
    _showProfilesDialog.value = false
    if (!wasAlreadyInSettings) {
      settingsOriginScreen = AppScreen.Home
    }
    _settingsEntryRoute.value = SettingsRoute.PROFILE
    _settingsDirectProfileBackToExit.value = !wasAlreadyInSettings
    _settingsRouteNonce.value += 1
    _currentScreen.value = AppScreen.Settings
  }

  fun addNewProfile() {
    val wasAlreadyInSettings = _currentScreen.value == AppScreen.Settings
    viewModelScope.launch { createAndOpenNewProfile(wasAlreadyInSettings) }
  }

  fun saveProfileDraftAndAddNew(
    name: String,
    avatar: String,
    mode: ChallengeMode,
    customLevelId: String? = null,
  ) {
    val current = activeProfile.value
    viewModelScope.launch {
      if (current != null) {
        val updated = resolveUpdatedProfile(current, name, avatar, mode, customLevelId)
        repository.upsertProfile(updated)
      }
      createAndOpenNewProfile(wasAlreadyInSettings = true)
    }
  }

  private fun resolveUpdatedProfile(
    current: KidProfile,
    name: String,
    avatar: String,
    mode: ChallengeMode,
    customLevelId: String?,
  ): KidProfile {
    val resolvedCustomId =
      if (mode == ChallengeMode.OFF) null else customLevelId?.trim()?.ifBlank { null }
    val resolvedMode =
      if (resolvedCustomId != null && mode == ChallengeMode.OFF) ChallengeMode.PRE_K else mode
    return current.copy(
      name = name.trim().ifEmpty { current.name },
      avatar = avatar,
      challengeMode = resolvedMode,
      customLevelId = resolvedCustomId,
    )
  }

  fun saveSettings(
    name: String,
    avatar: String,
    mode: ChallengeMode,
    customLevelId: String? = null,
  ) {
    val current = activeProfile.value ?: return
    val updated = resolveUpdatedProfile(current, name, avatar, mode, customLevelId)
    viewModelScope.launch {
      repository.upsertProfile(updated)
      closeSettings()
    }
  }

  private suspend fun buildProfileExportBundleWithDraft(
    draftName: String?,
    draftAvatar: String?,
    draftMode: ChallengeMode?,
    draftCustomLevelId: String?,
  ): ProfileExportBundle? {
    val current = activeProfile.value ?: return null
    saveCurrentProgressNow()
    if (draftName != null && draftAvatar != null && draftMode != null) {
      val updated = resolveUpdatedProfile(current, draftName, draftAvatar, draftMode, draftCustomLevelId)
      repository.upsertProfile(updated)
    }
    return repository.exportProfileBundle(current.id)
  }

  fun saveProfileToUri(
    destinationUri: Uri,
    draftName: String? = null,
    draftAvatar: String? = null,
    draftMode: ChallengeMode? = null,
    draftCustomLevelId: String? = null,
  ) {
    viewModelScope.launch {
      val bundle =
        buildProfileExportBundleWithDraft(draftName, draftAvatar, draftMode, draftCustomLevelId)
      if (bundle == null) {
        showToast("Couldn't export active profile")
        return@launch
      }
      val ok = ProfileExportEngine.writeProfileToUri(getApplication(), destinationUri, bundle)
      showToast(
        if (ok) "Saved ${bundle.profile.name}'s profile JSON!" else "Couldn't save profile file"
      )
    }
  }

  fun shareProfileViaIntent(
    context: Context,
    draftName: String? = null,
    draftAvatar: String? = null,
    draftMode: ChallengeMode? = null,
    draftCustomLevelId: String? = null,
  ) {
    viewModelScope.launch {
      val bundle =
        buildProfileExportBundleWithDraft(draftName, draftAvatar, draftMode, draftCustomLevelId)
      if (bundle == null) {
        showToast("Couldn't export active profile")
        return@launch
      }
      val ok =
        try {
          val uri = ProfileExportEngine.writeProfileToCache(context, bundle)
          ProfileExportEngine.launchShareProfileIntent(context, uri, bundle.profile)
        } catch (_: Throwable) {
          false
        }
      if (!ok) {
        showToast("Couldn't open share sheet for profile")
      }
    }
  }

  fun beginProfileImportFromUri(sourceUri: Uri) {
    viewModelScope.launch {
      val raw = ProfileExportEngine.readTextFromUri(getApplication(), sourceUri)
      val bundle = raw?.let { ProfileExportEngine.decodeProfileFromJson(it) }
      if (bundle == null) {
        showToast("Could not read profile — choose a valid ColorPop profile JSON file")
        return@launch
      }
      val existingProfiles = profiles.value
      val exactProfile = existingProfiles.firstOrNull { it.id == bundle.profile.id }
      val exactMatch =
        exactProfile?.let {
          ExistingProfileMatch(profile = it, summary = repository.getProfileSummary(it.id))
        }
      val sameNameMatches =
        existingProfiles
          .filter {
            it.id != bundle.profile.id &&
              it.name.trim().equals(bundle.profile.name.trim(), ignoreCase = true)
          }
          .map { ExistingProfileMatch(profile = it, summary = repository.getProfileSummary(it.id)) }
      _showProfilesDialog.value = false
      _profileImportPreview.value =
        ProfileImportPreviewUiState(
          bundle = bundle,
          exactIdMatch = exactMatch,
          sameNameMatches = sameNameMatches,
        )
    }
  }

  fun confirmProfileImport(
    mode: ProfileImportConflictMode,
    targetProfileId: String? = null,
  ) {
    val preview = _profileImportPreview.value ?: return
    _profileImportPreview.value = null
    viewModelScope.launch {
      saveCurrentProgressNow()
      val imported =
        repository.importProfileBundle(
          bundle = preview.bundle,
          mode = mode,
          targetProfileId = targetProfileId,
        )
      _pendingStickers.value = emptyMap()
      _activeStickerPageId.value = null
      _activeProfileId.value = imported.id
      repository.setActiveProfileId(imported.id)
      sounds.enabled = imported.soundEnabled
      val code = ProfileExportEngine.shortProfileCode(imported.id)
      showToast("Imported ${imported.name} (#$code)!")
    }
  }

  fun dismissProfileImport() {
    _profileImportPreview.value = null
  }

  fun saveSyncSettings(sourceUrl: String, wifiOnlyAutoSync: Boolean) {
    val cleanUrl = sourceUrl.trim()
    val prevUrl = repository.getSyncSourceUrl()
    repository.setSyncSourceUrl(cleanUrl)
    repository.setWifiOnlyAutoSync(wifiOnlyAutoSync)
    _syncSettingsState.update {
      it.copy(sourceUrl = cleanUrl, wifiOnlyAutoSync = wifiOnlyAutoSync)
    }
    if (cleanUrl.isNotBlank() && cleanUrl != prevUrl) {
      maybeAutoSyncOnWifi(forceInterval = true)
    }
  }

  private fun maybeAutoSyncOnWifi(forceInterval: Boolean = false) {
    val sourceUrl = repository.getSyncSourceUrl()
    if (sourceUrl.isBlank()) return
    val lastSync = repository.getLastSyncAtMs()
    val now = System.currentTimeMillis()
    if (!forceInterval && now - lastSync < RemoteCatalogSyncEngine.AUTO_SYNC_INTERVAL_MS) return

    val app = getApplication<Application>()
    val wifiOnly = repository.isWifiOnlyAutoSync()
    val canRun =
      if (wifiOnly) RemoteCatalogSyncEngine.isWifiConnected(app)
      else RemoteCatalogSyncEngine.hasInternetConnection(app)
    if (!canRun) return

    performCatalogSync(sourceUrl = sourceUrl, isManual = false)
  }

  fun syncCatalogNow(urlOverride: String? = null) {
    val sourceUrl = (urlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    if (urlOverride != null) {
      repository.setSyncSourceUrl(sourceUrl)
      _syncSettingsState.update { it.copy(sourceUrl = sourceUrl) }
    }
    if (sourceUrl.isBlank()) {
      showToast("Enter a GitHub repo or catalog URL first")
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "No internet connection — using saved pages"
      _syncSettingsState.update { it.copy(lastSyncStatus = msg) }
      showToast(msg)
      return
    }
    performCatalogSync(sourceUrl = sourceUrl, isManual = true)
  }

  private fun performCatalogSync(sourceUrl: String, isManual: Boolean) {
    if (syncJob?.isActive == true) return
    syncJob =
      viewModelScope.launch {
        _syncSettingsState.update { it.copy(isSyncing = true) }
        try {
          val existing = repository.getSyncedTemplatesOnce()
          val existingQuestions = repository.getSyncedQuestionsOnce()
          val existingLevels = repository.getSyncedLevelsOnce()
          val token = repository.getGitHubPublishToken()
          val result =
            RemoteCatalogSyncEngine.syncFromSource(
              sourceUrl = sourceUrl,
              existingSynced = existing,
              token = token,
              existingSyncedQuestions = existingQuestions,
              existingSyncedLevels = existingLevels,
            )
          if (result.errorMessage == null) {
            repository.applyRemoteSyncResult(result.upserts, result.activeRemoteIds)
            repository.applyRemoteQuestionSyncResult(
              result.questionUpserts,
              result.activeRemoteQuestionIds,
            )
            repository.applyRemoteLevelSyncResult(
              result.levelUpserts,
              result.activeRemoteLevelIds,
            )
            val now = System.currentTimeMillis()
            repository.setLastSyncInfo(now, result.statusMessage)
            _syncSettingsState.update {
              it.copy(
                isSyncing = false,
                lastSyncAtMs = now,
                lastSyncStatus = result.statusMessage,
              )
            }
            if (isManual) {
              showToast(result.statusMessage)
            } else if (
              result.downloadedCount > 0 ||
                result.downloadedQuestionCount > 0 ||
                result.downloadedLevelCount > 0
            ) {
              val parts = buildList {
                if (result.downloadedCount > 0) add("${result.downloadedCount} coloring page(s)")
                if (result.downloadedLevelCount > 0) add("${result.downloadedLevelCount} level(s)")
                if (result.downloadedQuestionCount > 0) add("${result.downloadedQuestionCount} question(s)")
              }
              showToast("Updated ${parts.joinToString(" & ")}")
            }
          } else {
            _syncSettingsState.update {
              it.copy(isSyncing = false, lastSyncStatus = result.statusMessage)
            }
            if (isManual) showToast(result.statusMessage)
          }
        } catch (t: Throwable) {
          val errMsg = "Couldn't check updates: ${t.message?.take(140) ?: "unexpected error"}"
          _syncSettingsState.update { it.copy(isSyncing = false, lastSyncStatus = errMsg) }
          if (isManual) showToast(errMsg)
        }
      }
  }

  fun setupAdminPin(pin: String): Boolean {
    val clean = pin.trim()
    if (clean.length < 4 || !clean.all { it.isDigit() }) {
      showToast("PIN must be 4 to 8 digits")
      return false
    }
    repository.setAdminPin(clean)
    _syncSettingsState.update { it.copy(hasAdminPin = true, isAdminUnlocked = true) }
    showToast("Parent Admin PIN saved")
    return true
  }

  fun unlockAdminWithPin(pin: String): Boolean {
    val ok = repository.verifyAdminPin(pin)
    if (ok) {
      _syncSettingsState.update { it.copy(isAdminUnlocked = true) }
    }
    return ok
  }

  fun lockAdmin() {
    _syncSettingsState.update { it.copy(isAdminUnlocked = false) }
  }

  fun resetAdminPinAndClearToken() {
    repository.resetAdminPinAndClearToken()
    _syncSettingsState.update {
      it.copy(
        hasAdminPin = false,
        isAdminUnlocked = false,
        hasPublishToken = false,
        maskedPublishToken = "",
        lastPublishStatus = "PIN reset and saved token cleared",
      )
    }
    showToast("Admin PIN reset & saved token cleared")
  }

  fun saveGitHubPublishConfig(tokenInput: String?, publishFolder: String) {
    val cleanToken = tokenInput?.trim().orEmpty()
    if (cleanToken.isNotBlank()) {
      repository.setGitHubPublishToken(cleanToken)
    }
    repository.setPublishFolder(publishFolder)
    _syncSettingsState.update {
      it.copy(
        hasPublishToken = repository.getGitHubPublishToken().isNotBlank(),
        maskedPublishToken = repository.getMaskedPublishToken(),
        publishFolder = repository.getPublishFolder(),
      )
    }
    showToast(
      if (cleanToken.isNotBlank()) "GitHub publishing token saved on this device"
      else "Publish folder saved"
    )
  }

  fun clearGitHubPublishToken() {
    repository.setGitHubPublishToken("")
    _syncSettingsState.update {
      it.copy(
        hasPublishToken = false,
        maskedPublishToken = "",
        lastPublishStatus = "GitHub token removed from this device",
      )
    }
    showToast("GitHub token removed")
  }

  fun publishTemplateToGitHub(
    template: PictureTemplate,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
    localTemplateId: String = template.id,
    onComplete: (Boolean, String) -> Unit = { _, _ -> },
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      val msg = "Unlock Parent Admin with your PIN first"
      showToast(msg)
      onComplete(false, msg)
      return
    }
    if (template.totalPaintableCells() == 0) {
      val msg = "Draw at least one block before publishing"
      showToast(msg)
      onComplete(false, msg)
      return
    }
    if (_syncSettingsState.value.isPublishing) {
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      onComplete(false, msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      onComplete(false, msg)
      return
    }

    val publishId =
      if (template.id.startsWith("custom_")) {
        PageExportEngine.sanitizePageId(template.title).ifBlank { template.id }
      } else {
        template.id
      }
    val templateToPublish = if (publishId != template.id) template.copy(id = publishId) else template

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(isPublishing = true, publishingTemplateId = localTemplateId)
      }
      val result =
        try {
          RemoteCatalogSyncEngine.publishPageToGitHub(
            sourceUrl = sourceUrl,
            token = token,
            template = templateToPublish,
            targetFolder = folder,
          )
        } catch (_: Throwable) {
          com.example.engine.PublishPageResult(
            success = false,
            message = "Couldn't publish to GitHub right now",
          )
        }

      if (result.success && result.syncedEntity != null) {
        repository.promotePublishedTemplate(
          entity = result.syncedEntity,
          localTemplateId = localTemplateId,
        )
        val pubTpl = result.publishedTemplate
        if (
          pubTpl != null &&
            _currentScreen.value == AppScreen.BlockEditor &&
            blockEditorState.value.editingExistingId == localTemplateId
        ) {
          blockEditorController.loadTemplate(
            template = pubTpl,
            originScreen = blockEditorState.value.originScreen,
            editingExistingId = pubTpl.id,
          )
        }
      }

      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          publishingTemplateId = null,
          lastPublishStatus = result.message,
        )
      }
      showToast(result.message)
      onComplete(result.success, result.message)
    }
  }

  fun publishAllTemplatesToGitHub(
    templatesToPublish: List<PictureTemplate>,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      showToast("Unlock Parent Admin with your PIN first")
      return
    }
    if (_syncSettingsState.value.isPublishing) {
      return
    }
    val validList = templatesToPublish.filter { it.totalPaintableCells() > 0 }
    if (validList.isEmpty()) {
      showToast("No edited or custom pages to publish")
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(
          isPublishing = true,
          isPublishingAll = true,
          publishingTemplateId = null,
          publishProgressText = "0/${validList.size}",
        )
      }
      var succeeded = 0
      var failed = 0
      var lastError = ""

      for ((idx, tpl) in validList.withIndex()) {
        _syncSettingsState.update {
          it.copy(
            publishingTemplateId = tpl.id,
            publishProgressText = "${idx + 1}/${validList.size}",
          )
        }
        val publishId =
          if (tpl.id.startsWith("custom_")) {
            PageExportEngine.sanitizePageId(tpl.title).ifBlank { tpl.id }
          } else {
            tpl.id
          }
        val tplToPublish = if (publishId != tpl.id) tpl.copy(id = publishId) else tpl

        val result =
          try {
            RemoteCatalogSyncEngine.publishPageToGitHub(
              sourceUrl = sourceUrl,
              token = token,
              template = tplToPublish,
              targetFolder = folder,
            )
          } catch (t: Throwable) {
            com.example.engine.PublishPageResult(
              success = false,
              message = "Couldn't publish \"${tpl.title}\": ${t.message?.take(120) ?: "unexpected error"}",
            )
          }

        if (result.success && result.syncedEntity != null) {
          repository.promotePublishedTemplate(
            entity = result.syncedEntity,
            localTemplateId = tpl.id,
          )
          val pubTpl = result.publishedTemplate
          if (
            pubTpl != null &&
              _currentScreen.value == AppScreen.BlockEditor &&
              blockEditorState.value.editingExistingId == tpl.id
          ) {
            blockEditorController.loadTemplate(
              template = pubTpl,
              originScreen = blockEditorState.value.originScreen,
              editingExistingId = pubTpl.id,
            )
          }
          succeeded++
        } else {
          failed++
          lastError = result.message
          if (
            result.message.contains("rejected token", ignoreCase = true) ||
              result.message.contains("Offline", ignoreCase = true) ||
              result.message.contains("Network error", ignoreCase = true) ||
              result.message.contains("not found or token lacks access", ignoreCase = true)
          ) {
            failed = validList.size - succeeded
            break
          }
        }
      }

      val summaryMsg =
        when {
          failed == 0 -> "Published all $succeeded page(s) to GitHub!"
          succeeded > 0 -> "Published $succeeded page(s), $failed failed: $lastError"
          else -> lastError.ifBlank { "Couldn't publish pages to GitHub" }
        }

      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          isPublishingAll = false,
          publishingTemplateId = null,
          publishProgressText = "",
          lastPublishStatus = summaryMsg,
        )
      }
      showToast(summaryMsg)
    }
  }

  /* ===================== CUSTOM QUESTIONS ===================== */

  fun saveCustomQuestion(question: CustomQuestion, editingExisting: CustomQuestion? = null) {
    val cleanPrompt = question.prompt.trim()
    val cleanCorrect = question.correctAnswer.trim()
    val cleanWrong =
      question.wrongAnswers
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(cleanCorrect, ignoreCase = false) }
        .distinct()
        .take(3)
    if (cleanPrompt.isEmpty() || cleanCorrect.isEmpty() || cleanWrong.isEmpty()) {
      showToast("Enter a question, correct answer, and at least one wrong answer")
      return
    }
    val filteredLevels = question.levels.filter { it != ChallengeMode.OFF }.toSet()
    val filteredCustomIds = question.customLevelIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val effectiveLevels =
      if (filteredLevels.isEmpty() && filteredCustomIds.isEmpty()) setOf(ChallengeMode.PRE_K)
      else filteredLevels

    val base = editingExisting
    val resolvedId =
      base?.id
        ?: QuestionExportEngine.sanitizeQuestionId(cleanPrompt)
          .let { slug -> if (slug.isNotBlank() && allQuestions.value.none { it.id == slug }) slug else "q_${System.currentTimeMillis()}" }
    val nextVersion =
      if (base != null && !base.isCustom) {
        (base.version + 1).coerceAtLeast(2)
      } else {
        (base?.version ?: 1).coerceAtLeast(1)
      }

    val toSave =
      question.copy(
        id = resolvedId,
        prompt = cleanPrompt,
        correctAnswer = cleanCorrect,
        wrongAnswers = cleanWrong,
        levels = effectiveLevels,
        customLevelIds = filteredCustomIds,
        version = nextVersion,
        isCustom = true,
        isSyncedOfficial = false,
      )
    viewModelScope.launch {
      repository.saveCustomQuestion(toSave)
      showToast(if (base != null) "Question updated" else "Custom question added!")
    }
  }

  fun deleteOrRevertCustomQuestion(questionId: String) {
    viewModelScope.launch {
      val hasSynced = repository.getSyncedQuestionsOnce().any { it.id == questionId }
      repository.deleteCustomQuestion(questionId)
      showToast(if (hasSynced) "Reverted to synced question" else "Custom question deleted")
    }
  }

  fun publishQuestionToGitHub(
    question: CustomQuestion,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
    localQuestionId: String = question.id,
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      showToast("Unlock Parent Admin with your PIN first")
      return
    }
    if (_syncSettingsState.value.isPublishing) {
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }

    val publishId =
      if (question.id.startsWith("q_")) {
        QuestionExportEngine.sanitizeQuestionId(question.prompt).ifBlank { question.id }
      } else {
        question.id
      }
    val questionToPublish = if (publishId != question.id) question.copy(id = publishId) else question

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(isPublishing = true, publishingQuestionId = localQuestionId)
      }
      val result =
        try {
          RemoteCatalogSyncEngine.publishQuestionToGitHub(
            sourceUrl = sourceUrl,
            token = token,
            question = questionToPublish,
            targetFolder = folder,
          )
        } catch (_: Throwable) {
          com.example.engine.PublishQuestionResult(
            success = false,
            message = "Couldn't publish question to GitHub right now",
          )
        }

      if (result.success && result.syncedEntity != null) {
        repository.promotePublishedQuestion(
          entity = result.syncedEntity,
          localQuestionId = localQuestionId,
        )
      }

      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          publishingQuestionId = null,
          lastPublishStatus = result.message,
        )
      }
      showToast(result.message)
    }
  }

  fun publishAllQuestionsToGitHub(
    questionsToPublish: List<CustomQuestion>,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      showToast("Unlock Parent Admin with your PIN first")
      return
    }
    if (_syncSettingsState.value.isPublishing) return
    if (questionsToPublish.isEmpty()) {
      showToast("No custom questions to publish")
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(
          isPublishing = true,
          isPublishingAll = true,
          publishingQuestionId = null,
          publishProgressText = "0/${questionsToPublish.size}",
        )
      }
      var succeeded = 0
      var failed = 0
      var lastError = ""

      for ((idx, q) in questionsToPublish.withIndex()) {
        _syncSettingsState.update {
          it.copy(
            publishingQuestionId = q.id,
            publishProgressText = "${idx + 1}/${questionsToPublish.size}",
          )
        }
        val publishId =
          if (q.id.startsWith("q_")) {
            QuestionExportEngine.sanitizeQuestionId(q.prompt).ifBlank { q.id }
          } else {
            q.id
          }
        val qToPublish = if (publishId != q.id) q.copy(id = publishId) else q
        val result =
          try {
            RemoteCatalogSyncEngine.publishQuestionToGitHub(
              sourceUrl = sourceUrl,
              token = token,
              question = qToPublish,
              targetFolder = folder,
            )
          } catch (t: Throwable) {
            com.example.engine.PublishQuestionResult(
              success = false,
              message = "Couldn't publish question: ${t.message?.take(100) ?: "error"}",
            )
          }

        if (result.success && result.syncedEntity != null) {
          repository.promotePublishedQuestion(
            entity = result.syncedEntity,
            localQuestionId = q.id,
          )
          succeeded++
        } else {
          failed++
          lastError = result.message
          if (
            result.message.contains("rejected token", ignoreCase = true) ||
              result.message.contains("Offline", ignoreCase = true) ||
              result.message.contains("Network error", ignoreCase = true)
          ) {
            failed = questionsToPublish.size - succeeded
            break
          }
        }
      }

      val summaryMsg =
        when {
          failed == 0 -> "Published all $succeeded question(s) to GitHub!"
          succeeded > 0 -> "Published $succeeded question(s), $failed failed: $lastError"
          else -> lastError.ifBlank { "Couldn't publish questions to GitHub" }
        }
      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          isPublishingAll = false,
          publishingQuestionId = null,
          publishProgressText = "",
          lastPublishStatus = summaryMsg,
        )
      }
      showToast(summaryMsg)
    }
  }

  /* ===================== CUSTOM EDUCATION LEVELS ===================== */

  fun saveCustomLevel(level: CustomEducationLevel, editingExisting: CustomEducationLevel? = null) {
    val cleanName = level.name.trim()
    val cleanDesc = level.description.trim()
    if (cleanName.isEmpty()) {
      showToast("Enter a level name")
      return
    }
    val base = editingExisting
    val resolvedId =
      base?.id
        ?: LevelExportEngine.sanitizeLevelId(cleanName)
          .let { slug -> if (slug.isNotBlank() && allLevels.value.none { it.id == slug }) slug else "lvl_${System.currentTimeMillis()}" }
    val nextVersion =
      if (base != null && !base.isCustom) {
        (base.version + 1).coerceAtLeast(2)
      } else {
        (base?.version ?: 1).coerceAtLeast(1)
      }

    val toSave =
      level.copy(
        id = resolvedId,
        name = cleanName,
        description = cleanDesc,
        version = nextVersion,
        isCustom = true,
        isSyncedOfficial = false,
      )
    viewModelScope.launch {
      repository.saveCustomLevel(toSave)
      showToast(if (base != null) "Education level updated" else "Education level created!")
    }
  }

  fun deleteOrRevertCustomLevel(levelId: String) {
    viewModelScope.launch {
      val hasSynced = repository.getSyncedLevelsOnce().any { it.id == levelId }
      repository.deleteCustomLevel(levelId)
      if (!hasSynced) {
        val current = activeProfile.value
        if (current != null && current.customLevelId == levelId) {
          repository.upsertProfile(current.copy(customLevelId = null))
        }
      }
      showToast(if (hasSynced) "Reverted to synced level" else "Education level deleted")
    }
  }

  fun publishLevelToGitHub(
    level: CustomEducationLevel,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
    localLevelId: String = level.id,
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      showToast("Unlock Parent Admin with your PIN first")
      return
    }
    if (_syncSettingsState.value.isPublishing) {
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }

    val publishId =
      if (level.id.startsWith("lvl_")) {
        LevelExportEngine.sanitizeLevelId(level.name).ifBlank { level.id }
      } else {
        level.id
      }
    val levelToPublish = if (publishId != level.id) level.copy(id = publishId) else level

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(isPublishing = true, publishingLevelId = localLevelId)
      }
      val result =
        try {
          RemoteCatalogSyncEngine.publishLevelToGitHub(
            sourceUrl = sourceUrl,
            token = token,
            level = levelToPublish,
            targetFolder = folder,
          )
        } catch (_: Throwable) {
          PublishLevelResult(
            success = false,
            message = "Couldn't publish level to GitHub right now",
          )
        }

      if (result.success && result.syncedEntity != null) {
        repository.promotePublishedLevel(
          entity = result.syncedEntity,
          localLevelId = localLevelId,
        )
      }

      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          publishingLevelId = null,
          lastPublishStatus = result.message,
        )
      }
      showToast(result.message)
    }
  }

  fun publishAllLevelsToGitHub(
    levelsToPublish: List<CustomEducationLevel>,
    sourceUrlOverride: String? = null,
    folderOverride: String? = null,
  ) {
    if (!_syncSettingsState.value.isAdminUnlocked) {
      showToast("Unlock Parent Admin with your PIN first")
      return
    }
    if (_syncSettingsState.value.isPublishing) return
    if (levelsToPublish.isEmpty()) {
      showToast("No custom education levels to publish")
      return
    }
    val sourceUrl = (sourceUrlOverride ?: _syncSettingsState.value.sourceUrl).trim()
    val folder = (folderOverride ?: _syncSettingsState.value.publishFolder).trim().trim('/')
    val token = repository.getGitHubPublishToken()
    if (token.isBlank()) {
      val msg = "Add your GitHub token (PAT) in Parent Settings → Admin first"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }
    val app = getApplication<Application>()
    if (!RemoteCatalogSyncEngine.hasInternetConnection(app)) {
      val msg = "Connect to Wi-Fi or internet to publish"
      _syncSettingsState.update { it.copy(lastPublishStatus = msg) }
      showToast(msg)
      return
    }

    viewModelScope.launch {
      _syncSettingsState.update {
        it.copy(
          isPublishing = true,
          isPublishingAll = true,
          publishingLevelId = null,
          publishProgressText = "0/${levelsToPublish.size}",
        )
      }
      var succeeded = 0
      var failed = 0
      var lastError = ""

      for ((idx, lvl) in levelsToPublish.withIndex()) {
        _syncSettingsState.update {
          it.copy(
            publishingLevelId = lvl.id,
            publishProgressText = "${idx + 1}/${levelsToPublish.size}",
          )
        }
        val publishId =
          if (lvl.id.startsWith("lvl_")) {
            LevelExportEngine.sanitizeLevelId(lvl.name).ifBlank { lvl.id }
          } else {
            lvl.id
          }
        val lvlToPublish = if (publishId != lvl.id) lvl.copy(id = publishId) else lvl
        val result =
          try {
            RemoteCatalogSyncEngine.publishLevelToGitHub(
              sourceUrl = sourceUrl,
              token = token,
              level = lvlToPublish,
              targetFolder = folder,
            )
          } catch (t: Throwable) {
            PublishLevelResult(
              success = false,
              message = "Couldn't publish level: ${t.message?.take(100) ?: "error"}",
            )
          }

        if (result.success && result.syncedEntity != null) {
          repository.promotePublishedLevel(
            entity = result.syncedEntity,
            localLevelId = lvl.id,
          )
          succeeded++
        } else {
          failed++
          lastError = result.message
          if (
            result.message.contains("rejected token", ignoreCase = true) ||
              result.message.contains("Offline", ignoreCase = true) ||
              result.message.contains("Network error", ignoreCase = true)
          ) {
            failed = levelsToPublish.size - succeeded
            break
          }
        }
      }

      val summaryMsg =
        when {
          failed == 0 -> "Published all $succeeded level(s) to GitHub!"
          succeeded > 0 -> "Published $succeeded level(s), $failed failed: $lastError"
          else -> lastError.ifBlank { "Couldn't publish levels to GitHub" }
        }
      _syncSettingsState.update {
        it.copy(
          isPublishing = false,
          isPublishingAll = false,
          publishingLevelId = null,
          publishProgressText = "",
          lastPublishStatus = summaryMsg,
        )
      }
      showToast(summaryMsg)
    }
  }

  fun deleteCurrentProfile() {
    val list = profiles.value
    val current = activeProfile.value ?: return
    if (list.size <= 1) return
    val fallback = list.first { it.id != current.id }
    viewModelScope.launch {
      repository.deleteProfile(current.id)
      _activeProfileId.value = fallback.id
      repository.setActiveProfileId(fallback.id)
      sounds.enabled = fallback.soundEnabled
      settingsOriginScreen = AppScreen.Home
      _currentScreen.value = AppScreen.Home
    }
  }

  fun toggleSound() {
    val current = activeProfile.value ?: return
    val next = !current.soundEnabled
    sounds.enabled = next
    viewModelScope.launch { repository.upsertProfile(current.copy(soundEnabled = next)) }
  }

  fun openPicture(template: PictureTemplate) {
    danceJob?.cancel()
    val saved = progressState.value.inProgressGrids[template.id]
    val grid2d = saved ?: Array(template.gridHeight) { IntArray(template.gridWidth) }
    val flat = IntArray(template.gridWidth * template.gridHeight)
    for (r in 0 until template.gridHeight) {
      for (c in 0 until template.gridWidth) {
        flat[r * template.gridWidth + c] = grid2d[r][c]
      }
    }
    val (remaining, fraction) = computeStats(template, flat)
    val initialColor =
      template.palette.keys.sorted().firstOrNull { (remaining[it] ?: 0) > 0 }
        ?: template.palette.keys.minOrNull()
        ?: 1

    _playState.value =
      PlayUiState(
        template = template,
        paintedGrid = flat,
        paintRevision = 1L,
        activeColorNumber = initialColor,
        activeBrush = BrushType.DOT,
        unlockedBrushes = setOf(BrushType.DOT),
        brushProgress = emptyMap(),
        refillProgress = 0,
        bucketColorsUsed = emptySet(),
        isCompleted = false,
        danceFrame = 0,
        remainingByColor = remaining,
        progressFraction = fraction,
      )
    _challengeState.value = null
    _currentScreen.value = AppScreen.Play
  }

  fun replayCurrentPicture() {
    val tpl = _playState.value.template
    val profileId = _activeProfileId.value
    saveJob?.cancel()
    val clearedMarker = OptimisticProgress.Cleared
    val optKey = profileId to tpl.id
    _optimisticProgress.update { it + (optKey to clearedMarker) }
    openPicture(tpl)
    viewModelScope.launch {
      withContext(NonCancellable) {
        repository.resetPictureProgress(profileId, tpl.id)
      }
      kotlinx.coroutines.withTimeoutOrNull(500L) {
        repository.observeProgressRows(profileId).collect { rows ->
          val caughtUp =
            rows.none { it.pictureId == tpl.id } ||
              _optimisticProgress.value[optKey] !== clearedMarker
          if (caughtUp) throw kotlinx.coroutines.CancellationException("caught_up")
        }
      }
      _optimisticProgress.update { map ->
        if (map[optKey] === clearedMarker) map - optKey else map
      }
    }
  }

  fun goHome() {
    danceJob?.cancel()
    _challengeState.value = null
    saveCurrentProgressNow()
    _currentScreen.value = AppScreen.Home
  }

  fun selectColor(colorNum: Int) {
    sounds.playPop()
    _playState.update { it.copy(activeColorNumber = colorNum) }
  }

  fun selectBrush(brush: BrushType) {
    val mode = activeProfile.value?.challengeMode ?: ChallengeMode.PRE_K
    val ps = _playState.value
    val bucketSpent =
      mode != ChallengeMode.OFF &&
        BrushType.BUCKET in ps.unlockedBrushes &&
        ps.bucketColorsUsed.size >= BUCKET_COLOR_LIMIT

    when {
      brush == BrushType.BUCKET && bucketSpent -> launchChallenge(BrushType.BUCKET, isRefill = true)
      mode == ChallengeMode.OFF || brush in ps.unlockedBrushes -> {
        _playState.update { it.copy(activeBrush = brush) }
      }
      else -> launchChallenge(brush, isRefill = false)
    }
  }

  private fun launchChallenge(brush: BrushType, isRefill: Boolean) {
    val profile = activeProfile.value
    val mode = profile?.challengeMode ?: ChallengeMode.PRE_K
    val customLevel =
      profile?.customLevelId?.let { id -> allLevels.value.firstOrNull { it.id == id } }
    val ps = _playState.value
    val total = if (isRefill) BUCKET_REFILL_COST else brush.cost
    val done = if (isRefill) ps.refillProgress else (ps.brushProgress[brush] ?: 0)
    _challengeState.value =
      ChallengeUiState(
        targetBrush = brush,
        isRefill = isRefill,
        totalRequired = total,
        completedCount = done,
        question =
          ChallengeGenerator.nextQuestion(
            mode = mode,
            customQuestions = allQuestions.value,
            customLevel = customLevel,
          ),
      )
  }

  fun submitChallengeAnswer(option: String) {
    val ch = _challengeState.value ?: return
    if (ch.solvedCorrectly) return

    if (option != ch.question.correct) {
      sounds.playWobble()
      _challengeState.value = ch.copy(wrongOptions = ch.wrongOptions + option)
      return
    }

    sounds.playSuccessFanfare()
    _challengeState.value = ch.copy(solvedCorrectly = true)

    viewModelScope.launch {
      delay(480L)
      val nextDone = ch.completedCount + 1
      if (ch.isRefill) {
        _playState.update { it.copy(refillProgress = nextDone) }
      } else {
        _playState.update {
          it.copy(brushProgress = it.brushProgress + (ch.targetBrush to nextDone))
        }
      }

      if (nextDone >= ch.totalRequired) {
        if (ch.isRefill) {
          _playState.update {
            it.copy(
              refillProgress = 0,
              bucketColorsUsed = emptySet(),
              unlockedBrushes = it.unlockedBrushes + BrushType.BUCKET,
              activeBrush = BrushType.BUCKET,
            )
          }
          showToast("Bucket refilled for $BUCKET_COLOR_LIMIT more colors!")
        } else {
          _playState.update {
            it.copy(
              unlockedBrushes = it.unlockedBrushes + ch.targetBrush,
              activeBrush = ch.targetBrush,
            )
          }
        }
        _challengeState.value = null
      } else {
        launchChallenge(ch.targetBrush, ch.isRefill)
      }
    }
  }

  fun dismissChallenge() {
    _challengeState.value = null
  }

  fun paintStroke(fromRow: Int?, fromCol: Int?, toRow: Int, toCol: Int) {
    val ps = _playState.value
    if (ps.isCompleted) return
    val tpl = ps.template
    val color = ps.activeColorNumber
    val mode = activeProfile.value?.challengeMode ?: ChallengeMode.PRE_K
    val grid = ps.paintedGrid.copyOf()
    var paintedAny = false
    var bucketBlocked = false
    var usedBucketOnColor = false

    fun applyAt(r: Int, c: Int) {
      if (r !in 0 until tpl.gridHeight || c !in 0 until tpl.gridWidth) return
      if (ps.activeBrush == BrushType.BUCKET) {
        val idx = r * tpl.gridWidth + c
        if (tpl.cellNumber(r, c) == color && grid[idx] != color) {
          val allowed =
            mode == ChallengeMode.OFF ||
              color in ps.bucketColorsUsed ||
              ps.bucketColorsUsed.size < BUCKET_COLOR_LIMIT
          if (!allowed) {
            bucketBlocked = true
            return
          }
          floodFill(tpl, grid, r, c, color)
          paintedAny = true
          usedBucketOnColor = true
        }
      } else {
        val radius =
          when (ps.activeBrush) {
            BrushType.DOT -> 0
            BrushType.CROSS -> 1
            BrushType.BLOB -> 2
            BrushType.BUCKET -> 0
          }
        for (rr in (r - radius)..(r + radius)) {
          for (cc in (c - radius)..(c + radius)) {
            if (kotlin.math.abs(rr - r) + kotlin.math.abs(cc - c) > radius) continue
            if (rr !in 0 until tpl.gridHeight || cc !in 0 until tpl.gridWidth) continue
            val idx = rr * tpl.gridWidth + cc
            if (tpl.cellNumber(rr, cc) == color && grid[idx] != color) {
              grid[idx] = color
              paintedAny = true
            }
          }
        }
      }
    }

    if (fromRow != null && fromCol != null && ps.activeBrush != BrushType.BUCKET) {
      val steps = maxOf(kotlin.math.abs(toRow - fromRow), kotlin.math.abs(toCol - fromCol), 1)
      for (i in 0..steps) {
        val rr = fromRow + ((toRow - fromRow) * i.toFloat() / steps).toInt()
        val cc = fromCol + ((toCol - fromCol) * i.toFloat() / steps).toInt()
        applyAt(rr, cc)
      }
    } else {
      applyAt(toRow, toCol)
    }

    if (bucketBlocked) {
      showToast("Bucket used up! Tap Bucket to answer questions for $BUCKET_COLOR_LIMIT more colors.")
      return
    }

    if (!paintedAny) return

    val wasColorRemaining = (ps.remainingByColor[color] ?: 0) > 0
    sounds.playPop()
    val updatedBucketSet =
      if (usedBucketOnColor && mode != ChallengeMode.OFF) {
        ps.bucketColorsUsed + color
      } else {
        ps.bucketColorsUsed
      }
    val (remaining, fraction) = computeStats(tpl, grid)
    val colorJustCompleted = wasColorRemaining && (remaining[color] ?: 0) == 0
    if (colorJustCompleted) {
      sounds.vibrateColorCompleted(getApplication())
    }
    val completed = remaining.isEmpty()

    _playState.update {
      it.copy(
        paintedGrid = grid,
        paintRevision = it.paintRevision + 1L,
        bucketColorsUsed = updatedBucketSet,
        remainingByColor = remaining,
        progressFraction = fraction,
        isCompleted = completed,
      )
    }

    if (completed) {
      onPictureCompleted(tpl)
    } else {
      scheduleDebouncedSave()
    }
  }

  private fun onPictureCompleted(tpl: PictureTemplate) {
    saveJob?.cancel()
    sounds.playSuccessFanfare()
    val profileId = _activeProfileId.value
    val completedMarker = OptimisticProgress.Completed
    val optKey = profileId to tpl.id
    _optimisticProgress.update { it + (optKey to completedMarker) }
    viewModelScope.launch {
      withContext(NonCancellable) {
        repository.markCompleted(profileId, tpl.id)
      }
      kotlinx.coroutines.withTimeoutOrNull(500L) {
        repository.observeProgressRows(profileId).collect { rows ->
          val caughtUp =
            rows.any { it.pictureId == tpl.id && it.isCompleted } ||
              _optimisticProgress.value[optKey] !== completedMarker
          if (caughtUp) throw kotlinx.coroutines.CancellationException("caught_up")
        }
      }
      _optimisticProgress.update { map ->
        if (map[optKey] === completedMarker) map - optKey else map
      }
    }
    danceJob?.cancel()
    danceJob =
      viewModelScope.launch {
        val frameCount = if (tpl.frames.isNotEmpty()) tpl.frames.size else 8
        val delayMs = if (tpl.frames.isNotEmpty()) tpl.frameMs.toLong() else 160L
        while (true) {
          delay(delayMs)
          _playState.update { it.copy(danceFrame = (it.danceFrame + 1) % frameCount) }
        }
      }
  }

  private fun scheduleDebouncedSave() {
    saveJob?.cancel()
    saveJob =
      viewModelScope.launch {
        delay(1000L)
        saveCurrentProgressNow()
      }
  }

  fun saveCurrentProgressNow() {
    saveJob?.cancel()
    val ps = _playState.value
    if (ps.isCompleted || ps.paintedGrid.isEmpty()) return
    if (!ps.paintedGrid.any { it > 0 }) return
    val tpl = ps.template
    val grid2d =
      Array(tpl.gridHeight) { r ->
        IntArray(tpl.gridWidth) { c -> ps.paintedGrid[r * tpl.gridWidth + c] }
      }
    val profileId = _activeProfileId.value
    val optKey = profileId to tpl.id
    val inProgressMarker = OptimisticProgress.InProgress(grid2d)
    _optimisticProgress.update { it + (optKey to inProgressMarker) }
    val serializedSnapshot = com.example.model.serializePaintedGrid(grid2d)
    viewModelScope.launch {
      withContext(NonCancellable) {
        repository.saveInProgress(profileId, tpl.id, grid2d)
      }
      kotlinx.coroutines.withTimeoutOrNull(500L) {
        repository.observeProgressRows(profileId).collect { rows ->
          val caughtUp =
            rows.any {
              it.pictureId == tpl.id && !it.isCompleted && it.paintedGrid == serializedSnapshot
            } || _optimisticProgress.value[optKey] !== inProgressMarker
          if (caughtUp) throw kotlinx.coroutines.CancellationException("caught_up")
        }
      }
      _optimisticProgress.update { map ->
        if (map[optKey] === inProgressMarker) map - optKey else map
      }
    }
  }

  private fun floodFill(
    tpl: PictureTemplate,
    grid: IntArray,
    startR: Int,
    startC: Int,
    color: Int,
  ) {
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.addLast(startR to startC)
    while (queue.isNotEmpty()) {
      val (r, c) = queue.removeFirst()
      if (r !in 0 until tpl.gridHeight || c !in 0 until tpl.gridWidth) continue
      val idx = r * tpl.gridWidth + c
      if (tpl.cellNumber(r, c) != color || grid[idx] == color) continue
      grid[idx] = color
      queue.addLast(r + 1 to c)
      queue.addLast(r - 1 to c)
      queue.addLast(r to c + 1)
      queue.addLast(r to c - 1)
    }
  }

  private fun computeStats(tpl: PictureTemplate, flat: IntArray): Pair<Map<Int, Int>, Float> {
    var needed = 0
    var filled = 0
    val remaining = mutableMapOf<Int, Int>()
    for (r in 0 until tpl.gridHeight) {
      for (c in 0 until tpl.gridWidth) {
        val target = tpl.cellNumber(r, c)
        if (target > 0) {
          needed++
          if (flat[r * tpl.gridWidth + c] == target) {
            filled++
          } else {
            remaining[target] = (remaining[target] ?: 0) + 1
          }
        }
      }
    }
    val fraction = if (needed > 0) filled.toFloat() / needed else 0f
    return remaining to fraction
  }

  /* ===================== CUSTOM PAGE CREATOR ===================== */

  fun openCreatePage() {
    _createPageState.value = CreatePageUiState()
    _currentScreen.value = AppScreen.CreatePage
  }

  fun closeCreatePage() {
    convertJob?.cancel()
    _currentScreen.value = AppScreen.Home
  }

  fun onImageSelected(uri: Uri) {
    _createPageState.update {
      it.copy(
        sourceUri = uri,
        detectedPixelSizePx = null,
        isProcessing = true,
        errorMessage = null,
      )
    }
    viewModelScope.launch {
      val app = getApplication<Application>()
      val decodedPage =
        withContext(Dispatchers.IO) {
          try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
              val bytes = stream.readBytes()
              if (bytes.size <= 2_000_000) {
                val firstNonWs = bytes.firstOrNull { b -> b != 32.toByte() && b != 9.toByte() && b != 10.toByte() && b != 13.toByte() }
                if (firstNonWs == '{'.code.toByte()) {
                  PageExportEngine.decodePageFromJson(String(bytes, Charsets.UTF_8))
                } else {
                  null
                }
              } else {
                null
              }
            }
          } catch (_: Throwable) {
            null
          }
        }

      if (decodedPage != null) {
        _createPageState.update {
          it.copy(
            sourceBitmap = null,
            sourceFrames = emptyList(),
            previewTemplate = decodedPage,
            title = decodedPage.title,
            detectedPixelSizePx = null,
            isProcessing = false,
            errorMessage = null,
          )
        }
        showToast("Loaded \"${decodedPage.title}\"")
        return@launch
      }

      val decodedGif =
        withContext(Dispatchers.IO) {
          try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
              val header = ByteArray(6)
              val read = stream.read(header)
              if (read == 6 && GifFrameDecoder.isGifHeader(header)) {
                val rest = stream.readBytes()
                val allBytes = ByteArray(6 + rest.size)
                header.copyInto(allBytes, 0)
                rest.copyInto(allBytes, 6)
                GifFrameDecoder.decodeGifFrames(allBytes)
              } else {
                null
              }
            }
          } catch (_: Throwable) {
            null
          }
        }

      if (decodedGif != null && decodedGif.frames.size > 1) {
        _createPageState.update {
          it.copy(
            sourceBitmap = decodedGif.frames.first(),
            sourceFrames = decodedGif.frames,
            sourceFrameMs = decodedGif.frameMs,
          )
        }
        regenerateCustomPreview()
        return@launch
      }

      val bmp = ImageToGridConverter.decodePreviewBitmap(app, uri)
      if (bmp == null) {
        _createPageState.update {
          it.copy(isProcessing = false, errorMessage = "Couldn't read that file. Try another image, GIF, or .colorpop.json page!")
        }
        return@launch
      }
      _createPageState.update {
        it.copy(sourceBitmap = bmp, sourceFrames = listOf(bmp), sourceFrameMs = 400)
      }
      regenerateCustomPreview()
    }
  }

  fun updateCreateTitle(title: String) {
    _createPageState.update { st ->
      st.copy(
        title = title,
        previewTemplate = st.previewTemplate?.copy(title = title.ifBlank { "My Drawing" }),
      )
    }
  }

  fun updateCreateGridSize(size: Int) {
    _createPageState.update { it.copy(gridSize = size) }
    regenerateCustomPreview()
  }

  fun updateCreateColorCount(count: Int) {
    _createPageState.update { it.copy(colorCount = count) }
    regenerateCustomPreview()
  }

  fun updateCreateTrimBackground(trim: Boolean) {
    _createPageState.update { it.copy(trimBackground = trim) }
    regenerateCustomPreview()
  }

  private fun regenerateCustomPreview() {
    val st = _createPageState.value
    val frames = st.sourceFrames.ifEmpty { listOfNotNull(st.sourceBitmap) }
    if (frames.isEmpty()) return
    convertJob?.cancel()
    convertJob =
      viewModelScope.launch {
        _createPageState.update { it.copy(isProcessing = true) }
        val result =
          ImageToGridConverter.convertFramesToResult(
            frames = frames,
            frameMs = st.sourceFrameMs,
            id = "custom_${System.currentTimeMillis()}",
            title = st.title,
            maxGridDim = st.gridSize,
            maxColors = st.colorCount,
            trimBackground = st.trimBackground,
          )
        _createPageState.update {
          it.copy(
            previewTemplate = result.template,
            detectedPixelSizePx = result.detectedPixelSizePx,
            isProcessing = false,
          )
        }
      }
  }

  fun createBlankPage(title: String, gridWidth: Int, gridHeight: Int) {
    convertJob?.cancel()
    val w = gridWidth.coerceIn(8, 64)
    val h = gridHeight.coerceIn(8, 64)
    val cleanTitle = title.trim().ifEmpty { "My Drawing" }
    val blankRow = ".".repeat(w)
    val starterPalette =
      mapOf(
        1 to 0xFF000000.toInt(), // Black (outline)
        2 to 0xFFFFFFFF.toInt(), // White
        3 to 0xFFEF4444.toInt(), // Red
        4 to 0xFFF97316.toInt(), // Orange
        5 to 0xFFFACC15.toInt(), // Yellow
        6 to 0xFF22C55E.toInt(), // Green
        7 to 0xFF38BDF8.toInt(), // Sky Blue
        8 to 0xFF2563EB.toInt(), // Royal Blue
        9 to 0xFF8B5CF6.toInt(), // Purple
        10 to 0xFFEC4899.toInt(), // Pink
      )
    val blankTemplate =
      PictureTemplate(
        id = "custom_${System.currentTimeMillis()}",
        title = cleanTitle,
        gridWidth = w,
        gridHeight = h,
        palette = starterPalette,
        grid = List(h) { blankRow },
        isCustom = true,
      )
    _createPageState.value =
      CreatePageUiState(
        title = cleanTitle,
        previewTemplate = blankTemplate,
      )
    blockEditorController.loadTemplate(
      template = blankTemplate,
      originScreen = AppScreen.CreatePage,
      editingExistingId = null,
    )
    _currentScreen.value = AppScreen.BlockEditor
  }

  fun saveAndColorCustomPage() {
    val preview = _createPageState.value.previewTemplate ?: return
    if (preview.totalPaintableCells() == 0) {
      showToast("Draw at least one block before saving!")
      return
    }
    val finalTpl = preview.copy(title = _createPageState.value.title.trim().ifEmpty { "My Drawing" })
    viewModelScope.launch {
      repository.saveCustomTemplate(finalTpl)
      openPicture(finalTpl)
    }
  }

  fun deleteCustomTemplate(templateId: String) {
    viewModelScope.launch {
      val hasBuiltInOrSynced =
        ALL_BUILT_IN_TEMPLATES.any { it.id == templateId } ||
          repository.getSyncedTemplatesOnce().any { it.id == templateId }
      if (hasBuiltInOrSynced) {
        repository.revertCustomOverride(templateId)
        showToast("Reverted to original page")
      } else {
        repository.deleteCustomTemplate(templateId)
        showToast("Custom page deleted")
      }
    }
  }

  /* ===================== BLOCK EDITOR ===================== */

  fun openBlockEditorFromCreatePage() {
    val preview = _createPageState.value.previewTemplate ?: return
    val currentTitle = _createPageState.value.title.trim().ifEmpty { preview.title }
    blockEditorController.loadTemplate(
      template = preview.copy(title = currentTitle),
      originScreen = AppScreen.CreatePage,
      editingExistingId = null,
    )
    _currentScreen.value = AppScreen.BlockEditor
  }

  fun openBlockEditorForTemplate(template: PictureTemplate) {
    blockEditorController.loadTemplate(
      template = template,
      originScreen = AppScreen.Home,
      editingExistingId = template.id,
    )
    _currentScreen.value = AppScreen.BlockEditor
  }

  fun openBlockEditorForCustomTemplate(template: PictureTemplate) {
    openBlockEditorForTemplate(template)
  }

  fun closeBlockEditor() {
    val origin = blockEditorState.value.originScreen
    _currentScreen.value = if (origin == AppScreen.CreatePage) AppScreen.CreatePage else AppScreen.Home
  }

  fun saveBlockEditorChanges() {
    val st = blockEditorState.value
    val editedTpl = blockEditorController.buildEditedTemplate() ?: return
    if (st.originScreen == AppScreen.CreatePage && st.editingExistingId == null) {
      _createPageState.update {
        it.copy(title = editedTpl.title, previewTemplate = editedTpl)
      }
      _currentScreen.value = AppScreen.CreatePage
      showToast("Applied edits to preview")
    } else {
      val base = st.baseTemplate
      val dimensionsChanged =
        base != null && (base.gridWidth != editedTpl.gridWidth || base.gridHeight != editedTpl.gridHeight)
      val bumpedVersion =
        if (base != null && !base.isCustom) {
          (base.version + 1).coerceAtLeast(2)
        } else {
          editedTpl.version.coerceAtLeast(1)
        }
      val toSave =
        editedTpl.copy(
          version = bumpedVersion,
          isCustom = true,
          isSyncedOfficial = false,
        )
      viewModelScope.launch {
        repository.saveEditedCustomTemplate(toSave, resetProgress = dimensionsChanged)
        _currentScreen.value = AppScreen.Home
        showToast("Saved changes to \"${toSave.title}\"")
      }
    }
  }

  /* ===================== STICKER BOOK ===================== */

  fun selectStickerPage(pageId: String) {
    _activeStickerPageId.value = pageId
  }

  fun addStickerPage() {
    val profileId = _activeProfileId.value
    val pages = stickerPages.value
    if (pages.size >= 12) {
      showToast("Sticker book supports up to 12 pages")
      return
    }
    val nextOrder = (pages.maxOfOrNull { it.pageOrder } ?: -1) + 1
    val paper = StickerPagePaper.entries[nextOrder % StickerPagePaper.entries.size]
    val pageId = "page_${System.currentTimeMillis()}_${nextOrder}"
    _activeStickerPageId.value = pageId
    viewModelScope.launch { repository.createStickerPage(profileId, pageId, nextOrder, paper) }
  }

  fun cycleStickerPagePaper(page: StickerPage) {
    val profileId = _activeProfileId.value
    val nextPaper = StickerPagePaper.entries[(page.paper.index + 1) % StickerPagePaper.entries.size]
    viewModelScope.launch { repository.updateStickerPagePaper(profileId, page, nextPaper) }
  }

  private fun currentMaxStickerZOrder(): Long {
    val flowMax = placedStickers.value.maxOfOrNull { it.zOrder } ?: 0L
    val pendingMax = _pendingStickers.value.values.filterNotNull().maxOfOrNull { it.zOrder } ?: 0L
    return maxOf(flowMax, pendingMax)
  }

  fun placeSticker(
    pageId: String,
    templateId: String,
    xFraction: Float,
    yFraction: Float,
    scale: Float = 1.0f,
  ): String {
    val profileId = _activeProfileId.value
    val nextZ = maxOf(System.currentTimeMillis(), currentMaxStickerZOrder() + 1L)
    val newId = "stk_${nextZ}_${stickerIdCounter.incrementAndGet()}"
    val sticker =
      PlacedSticker(
        id = newId,
        pageId = pageId,
        templateId = templateId,
        xFraction = xFraction.coerceIn(0.02f, 0.98f),
        yFraction = yFraction.coerceIn(0.02f, 0.98f),
        scale = clampStickerScale(scale),
        isMirrored = false,
        zOrder = nextZ,
      )
    sounds.playPop()
    commitStickerOptimistic(profileId, sticker)
    return newId
  }

  fun updateStickerTransform(
    sticker: PlacedSticker,
    xFraction: Float,
    yFraction: Float,
    scale: Float,
    bringToFront: Boolean = true,
  ) {
    val profileId = _activeProfileId.value
    val latest = _pendingStickers.value[sticker.id] ?: placedStickers.value.firstOrNull { it.id == sticker.id } ?: sticker
    val maxZ = currentMaxStickerZOrder()
    val nextZ =
      if (bringToFront && latest.zOrder < maxZ) maxZ + 1L else latest.zOrder
    val updated =
      latest.copy(
        xFraction = xFraction.coerceIn(0.02f, 0.98f),
        yFraction = yFraction.coerceIn(0.02f, 0.98f),
        scale = clampStickerScale(scale),
        zOrder = nextZ,
      )
    commitStickerOptimistic(profileId, updated)
  }

  fun toggleStickerMirror(sticker: PlacedSticker) {
    val profileId = _activeProfileId.value
    val latest = _pendingStickers.value[sticker.id] ?: placedStickers.value.firstOrNull { it.id == sticker.id } ?: sticker
    val maxZ = currentMaxStickerZOrder()
    val nextZ = if (latest.zOrder < maxZ) maxZ + 1L else latest.zOrder
    val updated = latest.copy(isMirrored = !latest.isMirrored, zOrder = nextZ)
    sounds.playPop()
    commitStickerOptimistic(profileId, updated)
  }

  fun bringStickerToFront(sticker: PlacedSticker) {
    val latest = _pendingStickers.value[sticker.id] ?: placedStickers.value.firstOrNull { it.id == sticker.id } ?: sticker
    val maxZ = currentMaxStickerZOrder()
    if (latest.zOrder >= maxZ) return
    val profileId = _activeProfileId.value
    val updated = latest.copy(zOrder = maxZ + 1L)
    commitStickerOptimistic(profileId, updated)
  }

  private fun commitStickerOptimistic(profileId: String, updated: PlacedSticker) {
    _pendingStickers.update { it + (updated.id to updated) }
    viewModelScope.launch {
      repository.upsertPlacedSticker(profileId, updated)
      kotlinx.coroutines.withTimeoutOrNull(500L) {
        repository.observePlacedStickers(profileId).collect { list ->
          val caughtUp =
            list.any { it == updated } ||
              _pendingStickers.value[updated.id] != updated ||
              _activeProfileId.value != profileId
          if (caughtUp) throw kotlinx.coroutines.CancellationException("caught_up")
        }
      }
      _pendingStickers.update { map ->
        if (map[updated.id] == updated) map - updated.id else map
      }
    }
  }

  fun removePlacedSticker(stickerId: String) {
    val profileId = _activeProfileId.value
    sounds.playPop()
    _pendingStickers.update { it + (stickerId to null) }
    viewModelScope.launch {
      repository.deletePlacedSticker(stickerId)
      kotlinx.coroutines.withTimeoutOrNull(500L) {
        repository.observePlacedStickers(profileId).collect { list ->
          val caughtUp =
            list.none { it.id == stickerId } ||
              !_pendingStickers.value.containsKey(stickerId) ||
              _pendingStickers.value[stickerId] != null ||
              _activeProfileId.value != profileId
          if (caughtUp) throw kotlinx.coroutines.CancellationException("caught_up")
        }
      }
      _pendingStickers.update { map ->
        if (map.containsKey(stickerId) && map[stickerId] == null) map - stickerId else map
      }
    }
  }

  fun clearStickerPage(pageId: String) {
    val profileId = _activeProfileId.value
    val pageIds = placedStickers.value.filter { it.pageId == pageId }.map { it.id }
    if (pageIds.isNotEmpty()) {
      _pendingStickers.update { map -> map + pageIds.associateWith { null } }
    }
    viewModelScope.launch {
      repository.clearStickerPage(profileId, pageId)
      if (pageIds.isNotEmpty()) {
        _pendingStickers.update { map ->
          val mutable = map.toMutableMap()
          pageIds.forEach { id -> if (mutable[id] == null) mutable.remove(id) }
          mutable
        }
      }
    }
  }

  fun openLockedStickerPicture(template: PictureTemplate) {
    openPicture(template)
    showToast("Color & finish \"${template.title}\" to unlock this sticker!")
  }

  fun showLockedStickerHint(title: String) {
    showToast("Color & finish \"$title\" to unlock this sticker!")
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
    toastJob?.cancel()
    toastJob =
      viewModelScope.launch {
        delay(2400L)
        _toastMessage.value = null
      }
  }

  override fun onCleared() {
    super.onCleared()
    saveCurrentProgressNow()
  }
}
