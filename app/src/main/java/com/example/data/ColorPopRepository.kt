package com.example.data

import android.content.Context
import com.example.engine.LevelExportEngine
import com.example.engine.PageExportEngine
import com.example.engine.QuestionExportEngine
import com.example.engine.RemoteCatalogSyncEngine
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.KID_AVATARS
import com.example.model.KidProfile
import com.example.model.PictureTemplate
import com.example.model.PlacedSticker
import com.example.model.PracticeTopic
import com.example.model.StickerPage
import com.example.model.StickerPagePaper
import com.example.model.argbToHex
import com.example.model.clampStickerScale
import com.example.model.deserializePaintedGrid
import com.example.model.hexToArgb
import com.example.model.serializePaintedGrid
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

data class ProfileProgressState(
  val completedIds: Set<String> = emptySet(),
  val inProgressGrids: Map<String, Array<IntArray>> = emptyMap(),
)

class ColorPopRepository(private val db: ColorPopDatabase, context: Context) {
  private val prefs = context.getSharedPreferences("color_pop_prefs", Context.MODE_PRIVATE)
  private val profileDao = db.profileDao()
  private val progressDao = db.progressDao()
  private val customTemplateDao = db.customTemplateDao()
  private val syncedTemplateDao = db.syncedTemplateDao()
  private val stickerDao = db.stickerDao()
  private val customQuestionDao = db.customQuestionDao()
  private val syncedQuestionDao = db.syncedQuestionDao()
  private val customLevelDao = db.customLevelDao()
  private val syncedLevelDao = db.syncedLevelDao()

  val profilesFlow: Flow<List<KidProfile>> =
    profileDao.observeProfiles().map { list ->
      list.map { entity ->
        KidProfile(
          id = entity.id,
          name = entity.name,
          avatar = entity.avatar,
          challengeMode = ChallengeMode.fromKey(entity.challengeMode),
          soundEnabled = entity.soundEnabled,
          customLevelId = entity.customLevelId.trim().ifBlank { null },
        )
      }
    }

  val customTemplatesFlow: Flow<List<PictureTemplate>> =
    customTemplateDao.observeCustomTemplates().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  val syncedTemplatesFlow: Flow<List<PictureTemplate>> =
    syncedTemplateDao.observeSyncedTemplates().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  val customQuestionsFlow: Flow<List<CustomQuestion>> =
    customQuestionDao.observeCustomQuestions().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  val syncedQuestionsFlow: Flow<List<CustomQuestion>> =
    syncedQuestionDao.observeSyncedQuestions().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  val customLevelsFlow: Flow<List<CustomEducationLevel>> =
    customLevelDao.observeCustomLevels().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  val syncedLevelsFlow: Flow<List<CustomEducationLevel>> =
    syncedLevelDao.observeSyncedLevels().map { list ->
      list.mapNotNull { entity -> entity.toDomainOrNull() }
    }

  fun observeProgressRows(profileId: String): Flow<List<PictureProgressEntity>> =
    progressDao.observeProgressForProfile(profileId)

  fun computeProgressState(
    rows: List<PictureProgressEntity>,
    lookup: Map<String, PictureTemplate>,
  ): ProfileProgressState {
    val completed = mutableSetOf<String>()
    val inProgress = mutableMapOf<String, Array<IntArray>>()
    rows.forEach { row ->
      if (row.isCompleted) {
        completed.add(row.pictureId)
      } else {
        val tpl = lookup[row.pictureId]
        if (tpl != null) {
          val grid = deserializePaintedGrid(row.paintedGrid, tpl.gridWidth, tpl.gridHeight)
          if (grid.any { r -> r.any { it > 0 } }) {
            inProgress[row.pictureId] = grid
          }
        }
      }
    }
    return ProfileProgressState(completedIds = completed, inProgressGrids = inProgress)
  }

  fun observeProgress(
    profileId: String,
    templatesById: () -> Map<String, PictureTemplate>,
  ): Flow<ProfileProgressState> =
    progressDao.observeProgressForProfile(profileId).map { rows ->
      computeProgressState(rows, templatesById())
    }

  suspend fun ensureDefaultProfile(): String {
    val existing = profileDao.getProfilesOnce()
    if (existing.isEmpty()) {
      val def =
        ProfileEntity(
          id = "p1",
          name = "Player 1",
          avatar = KID_AVATARS[0],
          challengeMode = ChallengeMode.PRE_K.key,
          soundEnabled = true,
        )
      profileDao.upsertProfile(def)
      setActiveProfileId("p1")
      return "p1"
    }
    val saved = prefs.getString("active_profile_id", null)
    val valid = existing.firstOrNull { it.id == saved }?.id ?: existing.first().id
    setActiveProfileId(valid)
    return valid
  }

  fun getActiveProfileId(): String = prefs.getString("active_profile_id", "p1") ?: "p1"

  fun setActiveProfileId(id: String) {
    prefs.edit().putString("active_profile_id", id).apply()
  }

  suspend fun upsertProfile(profile: KidProfile) {
    profileDao.upsertProfile(
      ProfileEntity(
        id = profile.id,
        name = profile.name,
        avatar = profile.avatar,
        challengeMode = profile.challengeMode.key,
        soundEnabled = profile.soundEnabled,
        customLevelId = profile.customLevelId.orEmpty(),
      )
    )
  }

  suspend fun deleteProfile(profileId: String) {
    progressDao.deleteAllForProfile(profileId)
    stickerDao.deleteAllStickersForProfile(profileId)
    stickerDao.deleteAllPagesForProfile(profileId)
    profileDao.deleteProfile(profileId)
  }

  suspend fun saveInProgress(profileId: String, pictureId: String, grid: Array<IntArray>) {
    val serialized = serializePaintedGrid(grid)
    progressDao.upsertProgress(
      PictureProgressEntity(
        profileId = profileId,
        pictureId = pictureId,
        paintedGrid = serialized,
        isCompleted = false,
      )
    )
  }

  suspend fun markCompleted(profileId: String, pictureId: String) {
    progressDao.upsertProgress(
      PictureProgressEntity(
        profileId = profileId,
        pictureId = pictureId,
        paintedGrid = "",
        isCompleted = true,
      )
    )
  }

  suspend fun resetPictureProgress(profileId: String, pictureId: String) {
    progressDao.deleteProgress(profileId, pictureId)
  }

  suspend fun saveCustomTemplate(template: PictureTemplate) {
    val paletteStr =
      template.palette.entries.joinToString(",") { (k, v) -> "$k:${argbToHex(v)}" }
    val gridStr = template.grid.joinToString("/")
    val framesStr =
      if (template.frames.size > 1) {
        template.frames.joinToString("|") { frameRows -> frameRows.joinToString("/") }
      } else {
        ""
      }
    customTemplateDao.upsertCustomTemplate(
      CustomTemplateEntity(
        id = template.id,
        title = template.title,
        gridWidth = template.gridWidth,
        gridHeight = template.gridHeight,
        paletteSerialized = paletteStr,
        gridSerialized = gridStr,
        framesSerialized = framesStr,
        frameMs = template.frameMs,
        version = template.version.coerceAtLeast(1),
      )
    )
  }

  suspend fun saveEditedCustomTemplate(template: PictureTemplate, resetProgress: Boolean = true) {
    saveCustomTemplate(template)
    if (resetProgress) {
      progressDao.deleteAllForPicture(template.id)
    }
  }

  suspend fun deleteCustomTemplate(templateId: String) {
    progressDao.deleteAllForPicture(templateId)
    stickerDao.deleteAllForTemplate(templateId)
    customTemplateDao.deleteCustomTemplate(templateId)
  }

  suspend fun revertCustomOverride(templateId: String) {
    customTemplateDao.deleteCustomTemplate(templateId)
  }

  suspend fun getSyncedTemplatesOnce(): List<SyncedTemplateEntity> =
    syncedTemplateDao.getSyncedTemplatesOnce()

  suspend fun migratePictureId(oldId: String, newId: String) {
    if (oldId.isBlank() || newId.isBlank() || oldId == newId) return
    progressDao.copyProgressToNewPictureId(oldId, newId)
    progressDao.deleteAllForPicture(oldId)
    stickerDao.migrateTemplateId(oldId, newId)
  }

  private fun customMatchesSynced(custom: CustomTemplateEntity, synced: SyncedTemplateEntity): Boolean {
    return custom.title.trim() == synced.title.trim() &&
      custom.gridWidth == synced.gridWidth &&
      custom.gridHeight == synced.gridHeight &&
      custom.paletteSerialized == synced.paletteSerialized &&
      custom.gridSerialized == synced.gridSerialized &&
      custom.framesSerialized == synced.framesSerialized
  }

  suspend fun applyRemoteSyncResult(
    upserts: List<SyncedTemplateEntity>,
    activeRemoteIds: Set<String>?,
  ) {
    if (upserts.isNotEmpty()) {
      syncedTemplateDao.upsertSyncedTemplates(upserts)
    }
    if (!activeRemoteIds.isNullOrEmpty()) {
      syncedTemplateDao.deleteMissingSyncedTemplates(activeRemoteIds.toList())
    }
    val upsertedIds = upserts.mapTo(mutableSetOf()) { it.id }
    val allSynced = syncedTemplateDao.getSyncedTemplatesOnce()
    if (allSynced.isEmpty()) return
    val syncedById = allSynced.associateBy { it.id }
    val customList = customTemplateDao.getCustomTemplatesOnce()
    customList.forEach { custom ->
      val matchingSynced =
        syncedById[custom.id]
          ?: if (custom.id.startsWith("custom_")) {
            val sanitizedTitleId = PageExportEngine.sanitizePageId(custom.title)
            if (sanitizedTitleId.isNotBlank()) syncedById[sanitizedTitleId] else null
          } else {
            null
          }
      if (matchingSynced != null) {
        val shouldPromote =
          matchingSynced.version > custom.version ||
            customMatchesSynced(custom, matchingSynced) ||
            (matchingSynced.id in upsertedIds && matchingSynced.version >= custom.version)
        if (shouldPromote) {
          customTemplateDao.deleteCustomTemplate(custom.id)
          if (custom.id != matchingSynced.id) {
            migratePictureId(custom.id, matchingSynced.id)
          }
        }
      }
    }
  }

  /* ===================== CUSTOM QUESTIONS ===================== */

  suspend fun saveCustomQuestion(question: CustomQuestion) {
    val clueJson = JSONArray().apply { question.clueItems.forEach { put(it) } }.toString()
    val wrongJson =
      JSONArray()
        .apply {
          question.wrongAnswers
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .forEach { put(it) }
        }
        .toString()
    val levelsCsv =
      if (question.levels.isEmpty() && question.customLevelIds.isEmpty()) {
        ChallengeMode.PRE_K.key
      } else {
        question.levels
          .filter { it != ChallengeMode.OFF }
          .joinToString(",") { it.key }
      }
    val customLevelIdsCsv =
      question.customLevelIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")

    customQuestionDao.upsertCustomQuestion(
      CustomQuestionEntity(
        id = question.id,
        prompt = question.prompt.trim(),
        clueItemsJson = clueJson,
        correctAnswer = question.correctAnswer.trim(),
        wrongAnswersJson = wrongJson,
        levelsCsv = levelsCsv,
        customLevelIdsCsv = customLevelIdsCsv,
        isSentence = question.isSentence,
        version = question.version.coerceAtLeast(1),
      )
    )
  }

  suspend fun deleteCustomQuestion(questionId: String) {
    customQuestionDao.deleteCustomQuestion(questionId)
  }

  suspend fun getSyncedQuestionsOnce(): List<SyncedQuestionEntity> =
    syncedQuestionDao.getSyncedQuestionsOnce()

  private fun customQuestionMatchesSynced(
    custom: CustomQuestionEntity,
    synced: SyncedQuestionEntity,
  ): Boolean {
    return custom.prompt.trim() == synced.prompt.trim() &&
      custom.clueItemsJson == synced.clueItemsJson &&
      custom.correctAnswer.trim() == synced.correctAnswer.trim() &&
      custom.wrongAnswersJson == synced.wrongAnswersJson &&
      custom.levelsCsv == synced.levelsCsv &&
      custom.customLevelIdsCsv == synced.customLevelIdsCsv &&
      custom.isSentence == synced.isSentence
  }

  suspend fun promotePublishedQuestion(
    entity: SyncedQuestionEntity,
    localQuestionId: String? = null,
  ) {
    syncedQuestionDao.upsertSyncedQuestions(listOf(entity))
    if (!localQuestionId.isNullOrBlank()) {
      customQuestionDao.deleteCustomQuestion(localQuestionId)
    }
    customQuestionDao.deleteCustomQuestion(entity.id)
    val remaining = customQuestionDao.getCustomQuestionsOnce()
    remaining.forEach { custom ->
      val sameSanitized =
        custom.id.startsWith("q_") &&
          QuestionExportEngine.sanitizeQuestionId(custom.prompt) == entity.id
      if (
        customQuestionMatchesSynced(custom, entity) &&
          (custom.id == entity.id || sameSanitized)
      ) {
        customQuestionDao.deleteCustomQuestion(custom.id)
      }
    }
  }

  suspend fun applyRemoteQuestionSyncResult(
    upserts: List<SyncedQuestionEntity>,
    activeRemoteIds: Set<String>?,
  ) {
    if (upserts.isNotEmpty()) {
      syncedQuestionDao.upsertSyncedQuestions(upserts)
    }
    if (!activeRemoteIds.isNullOrEmpty()) {
      syncedQuestionDao.deleteMissingSyncedQuestions(activeRemoteIds.toList())
    }
    val upsertedIds = upserts.mapTo(mutableSetOf()) { it.id }
    val allSynced = syncedQuestionDao.getSyncedQuestionsOnce()
    if (allSynced.isEmpty()) return
    val syncedById = allSynced.associateBy { it.id }
    val customList = customQuestionDao.getCustomQuestionsOnce()
    customList.forEach { custom ->
      val matchingSynced =
        syncedById[custom.id]
          ?: if (custom.id.startsWith("q_")) {
            val sanitized = QuestionExportEngine.sanitizeQuestionId(custom.prompt)
            if (sanitized.isNotBlank()) syncedById[sanitized] else null
          } else {
            null
          }
      if (matchingSynced != null) {
        val shouldPromote =
          matchingSynced.version > custom.version ||
            customQuestionMatchesSynced(custom, matchingSynced) ||
            (matchingSynced.id in upsertedIds && matchingSynced.version >= custom.version)
        if (shouldPromote) {
          customQuestionDao.deleteCustomQuestion(custom.id)
        }
      }
    }
  }

  /* ===================== CUSTOM EDUCATION LEVELS ===================== */

  suspend fun saveCustomLevel(level: CustomEducationLevel) {
    val topicsCsv = level.topics.joinToString(",") { it.key }
    customLevelDao.upsertCustomLevel(
      CustomEducationLevelEntity(
        id = level.id,
        name = level.name.trim(),
        description = level.description.trim(),
        topicsCsv = topicsCsv,
        version = level.version.coerceAtLeast(1),
      )
    )
  }

  suspend fun deleteCustomLevel(levelId: String) {
    customLevelDao.deleteCustomLevel(levelId)
  }

  suspend fun getSyncedLevelsOnce(): List<SyncedEducationLevelEntity> =
    syncedLevelDao.getSyncedLevelsOnce()

  private suspend fun migrateCustomLevelIdReferences(oldId: String, newId: String) {
    if (oldId.isBlank() || newId.isBlank() || oldId == newId) return
    profileDao.migrateCustomLevelId(oldId, newId)
    customQuestionDao.getCustomQuestionsOnce().forEach { q ->
      val ids = decodeIdCsv(q.customLevelIdsCsv)
      if (oldId in ids) {
        val updatedIds = (ids - oldId) + newId
        customQuestionDao.upsertCustomQuestion(
          q.copy(customLevelIdsCsv = updatedIds.joinToString(","))
        )
      }
    }
    val updatedSyncedQ =
      syncedQuestionDao.getSyncedQuestionsOnce().mapNotNull { q ->
        val ids = decodeIdCsv(q.customLevelIdsCsv)
        if (oldId in ids) {
          val updatedIds = (ids - oldId) + newId
          q.copy(customLevelIdsCsv = updatedIds.joinToString(","))
        } else {
          null
        }
      }
    if (updatedSyncedQ.isNotEmpty()) {
      syncedQuestionDao.upsertSyncedQuestions(updatedSyncedQ)
    }
  }

  private fun customLevelMatchesSynced(
    custom: CustomEducationLevelEntity,
    synced: SyncedEducationLevelEntity,
  ): Boolean {
    return custom.name.trim() == synced.name.trim() &&
      custom.description.trim() == synced.description.trim() &&
      custom.topicsCsv == synced.topicsCsv
  }

  suspend fun promotePublishedLevel(
    entity: SyncedEducationLevelEntity,
    localLevelId: String? = null,
  ) {
    syncedLevelDao.upsertSyncedLevels(listOf(entity))
    if (!localLevelId.isNullOrBlank()) {
      customLevelDao.deleteCustomLevel(localLevelId)
      if (localLevelId != entity.id) {
        migrateCustomLevelIdReferences(localLevelId, entity.id)
      }
    }
    customLevelDao.deleteCustomLevel(entity.id)
    val remaining = customLevelDao.getCustomLevelsOnce()
    remaining.forEach { custom ->
      val sameSanitized =
        custom.id.startsWith("lvl_") &&
          LevelExportEngine.sanitizeLevelId(custom.name) == entity.id
      if (customLevelMatchesSynced(custom, entity) && (custom.id == entity.id || sameSanitized)) {
        customLevelDao.deleteCustomLevel(custom.id)
        if (custom.id != entity.id) {
          migrateCustomLevelIdReferences(custom.id, entity.id)
        }
      }
    }
  }

  suspend fun applyRemoteLevelSyncResult(
    upserts: List<SyncedEducationLevelEntity>,
    activeRemoteIds: Set<String>?,
  ) {
    if (upserts.isNotEmpty()) {
      syncedLevelDao.upsertSyncedLevels(upserts)
    }
    if (!activeRemoteIds.isNullOrEmpty()) {
      syncedLevelDao.deleteMissingSyncedLevels(activeRemoteIds.toList())
    }
    val upsertedIds = upserts.mapTo(mutableSetOf()) { it.id }
    val allSynced = syncedLevelDao.getSyncedLevelsOnce()
    if (allSynced.isEmpty()) return
    val syncedById = allSynced.associateBy { it.id }
    val customList = customLevelDao.getCustomLevelsOnce()
    customList.forEach { custom ->
      val matchingSynced =
        syncedById[custom.id]
          ?: if (custom.id.startsWith("lvl_")) {
            val sanitized = LevelExportEngine.sanitizeLevelId(custom.name)
            if (sanitized.isNotBlank()) syncedById[sanitized] else null
          } else {
            null
          }
      if (matchingSynced != null) {
        val shouldPromote =
          matchingSynced.version > custom.version ||
            customLevelMatchesSynced(custom, matchingSynced) ||
            (matchingSynced.id in upsertedIds && matchingSynced.version >= custom.version)
        if (shouldPromote) {
          customLevelDao.deleteCustomLevel(custom.id)
          if (custom.id != matchingSynced.id) {
            migrateCustomLevelIdReferences(custom.id, matchingSynced.id)
          }
        }
      }
    }
  }

  fun getSyncSourceUrl(): String {
    return if (prefs.contains(PREF_SYNC_URL)) {
      prefs.getString(PREF_SYNC_URL, "") ?: ""
    } else {
      RemoteCatalogSyncEngine.DEFAULT_SYNC_SOURCE_URL
    }
  }

  fun setSyncSourceUrl(url: String) {
    prefs.edit().putString(PREF_SYNC_URL, url.trim()).apply()
  }

  fun isWifiOnlyAutoSync(): Boolean = prefs.getBoolean(PREF_WIFI_ONLY_SYNC, true)

  fun setWifiOnlyAutoSync(enabled: Boolean) {
    prefs.edit().putBoolean(PREF_WIFI_ONLY_SYNC, enabled).apply()
  }

  fun getLastSyncAtMs(): Long = prefs.getLong(PREF_LAST_SYNC_AT_MS, 0L)

  fun getLastSyncStatus(): String = prefs.getString(PREF_LAST_SYNC_STATUS, "") ?: ""

  fun setLastSyncInfo(atMs: Long, status: String) {
    prefs.edit().putLong(PREF_LAST_SYNC_AT_MS, atMs).putString(PREF_LAST_SYNC_STATUS, status).apply()
  }

  suspend fun cachePublishedTemplate(entity: SyncedTemplateEntity) {
    syncedTemplateDao.upsertSyncedTemplates(listOf(entity))
  }

  suspend fun promotePublishedTemplate(
    entity: SyncedTemplateEntity,
    localTemplateId: String? = null,
  ) {
    syncedTemplateDao.upsertSyncedTemplates(listOf(entity))
    if (!localTemplateId.isNullOrBlank()) {
      customTemplateDao.deleteCustomTemplate(localTemplateId)
      if (localTemplateId != entity.id) {
        migratePictureId(localTemplateId, entity.id)
      }
    }
    customTemplateDao.deleteCustomTemplate(entity.id)
    val remainingCustom = customTemplateDao.getCustomTemplatesOnce()
    remainingCustom.forEach { custom ->
      val sameSanitized =
        custom.id.startsWith("custom_") && PageExportEngine.sanitizePageId(custom.title) == entity.id
      if (customMatchesSynced(custom, entity) && (custom.id == entity.id || sameSanitized)) {
        customTemplateDao.deleteCustomTemplate(custom.id)
        if (custom.id != entity.id) {
          migratePictureId(custom.id, entity.id)
        }
      }
    }
  }

  fun hasAdminPin(): Boolean = !prefs.getString(PREF_ADMIN_PIN_HASH, null).isNullOrBlank()

  fun setAdminPin(pin: String) {
    val clean = pin.trim()
    require(clean.length >= 4) { "PIN must be at least 4 digits" }
    val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
    val hash = sha256Hex("$saltHex:$clean")
    prefs.edit().putString(PREF_ADMIN_PIN_SALT, saltHex).putString(PREF_ADMIN_PIN_HASH, hash).apply()
  }

  fun verifyAdminPin(pin: String): Boolean {
    val clean = pin.trim()
    val salt = prefs.getString(PREF_ADMIN_PIN_SALT, null) ?: return false
    val expectedHex = prefs.getString(PREF_ADMIN_PIN_HASH, null) ?: return false
    if (clean.isEmpty() || expectedHex.isBlank()) return false
    val candidateHex = sha256Hex("$salt:$clean")
    return MessageDigest.isEqual(
      candidateHex.toByteArray(Charsets.UTF_8),
      expectedHex.toByteArray(Charsets.UTF_8),
    )
  }

  fun resetAdminPinAndClearToken() {
    prefs.edit()
      .remove(PREF_ADMIN_PIN_SALT)
      .remove(PREF_ADMIN_PIN_HASH)
      .remove(PREF_GITHUB_PUBLISH_TOKEN)
      .apply()
  }

  fun getGitHubPublishToken(): String = prefs.getString(PREF_GITHUB_PUBLISH_TOKEN, "") ?: ""

  fun setGitHubPublishToken(token: String) {
    val clean = token.trim()
    if (clean.isEmpty()) {
      prefs.edit().remove(PREF_GITHUB_PUBLISH_TOKEN).apply()
    } else {
      prefs.edit().putString(PREF_GITHUB_PUBLISH_TOKEN, clean).apply()
    }
  }

  fun getMaskedPublishToken(): String {
    val token = getGitHubPublishToken()
    if (token.isBlank()) return ""
    val tail = if (token.length >= 4) token.takeLast(4) else token
    return "••••$tail"
  }

  fun getPublishFolder(): String = prefs.getString(PREF_PUBLISH_FOLDER, "pages") ?: "pages"

  fun setPublishFolder(folder: String) {
    val clean = folder.trim().trim('/')
    prefs.edit().putString(PREF_PUBLISH_FOLDER, clean).apply()
  }

  private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun observeStickerPages(profileId: String): Flow<List<StickerPage>> =
    stickerDao.observePagesForProfile(profileId).map { list ->
      list.map { entity ->
        StickerPage(
          id = entity.pageId,
          pageOrder = entity.pageOrder,
          paper = StickerPagePaper.fromIndex(entity.paperIndex),
        )
      }
    }

  fun observePlacedStickers(profileId: String): Flow<List<PlacedSticker>> =
    stickerDao.observeStickersForProfile(profileId).map { list ->
      list.map { entity ->
        PlacedSticker(
          id = entity.id,
          pageId = entity.pageId,
          templateId = entity.templateId,
          xFraction = entity.xFraction.coerceIn(0f, 1f),
          yFraction = entity.yFraction.coerceIn(0f, 1f),
          scale = clampStickerScale(entity.scale),
          isMirrored = entity.isMirrored,
          zOrder = entity.zOrder,
        )
      }
    }

  suspend fun ensureStickerPages(profileId: String) {
    val existing = stickerDao.getPagesOnce(profileId)
    if (existing.isEmpty()) {
      val now = System.currentTimeMillis()
      stickerDao.upsertPage(
        StickerPageEntity(
          profileId = profileId,
          pageId = "page_1",
          pageOrder = 0,
          paperIndex = StickerPagePaper.WHITE.index,
          createdAt = now,
        )
      )
      stickerDao.upsertPage(
        StickerPageEntity(
          profileId = profileId,
          pageId = "page_2",
          pageOrder = 1,
          paperIndex = StickerPagePaper.CREAM.index,
          createdAt = now + 1,
        )
      )
      stickerDao.upsertPage(
        StickerPageEntity(
          profileId = profileId,
          pageId = "page_3",
          pageOrder = 2,
          paperIndex = StickerPagePaper.SKY.index,
          createdAt = now + 2,
        )
      )
    }
  }

  suspend fun createStickerPage(profileId: String, pageId: String, pageOrder: Int, paper: StickerPagePaper) {
    stickerDao.upsertPage(
      StickerPageEntity(
        profileId = profileId,
        pageId = pageId,
        pageOrder = pageOrder,
        paperIndex = paper.index,
        createdAt = System.currentTimeMillis(),
      )
    )
  }

  suspend fun updateStickerPagePaper(profileId: String, page: StickerPage, paper: StickerPagePaper) {
    stickerDao.upsertPage(
      StickerPageEntity(
        profileId = profileId,
        pageId = page.id,
        pageOrder = page.pageOrder,
        paperIndex = paper.index,
        createdAt = System.currentTimeMillis(),
      )
    )
  }

  suspend fun upsertPlacedSticker(profileId: String, sticker: PlacedSticker) {
    stickerDao.upsertSticker(
      PlacedStickerEntity(
        id = sticker.id,
        profileId = profileId,
        pageId = sticker.pageId,
        templateId = sticker.templateId,
        xFraction = sticker.xFraction.coerceIn(0f, 1f),
        yFraction = sticker.yFraction.coerceIn(0f, 1f),
        scale = clampStickerScale(sticker.scale),
        zOrder = sticker.zOrder,
        isMirrored = sticker.isMirrored,
      )
    )
  }

  suspend fun deletePlacedSticker(stickerId: String) {
    stickerDao.deleteSticker(stickerId)
  }

  suspend fun clearStickerPage(profileId: String, pageId: String) {
    stickerDao.clearPageStickers(profileId, pageId)
  }

  private fun CustomTemplateEntity.toDomainOrNull(): PictureTemplate? {
    return try {
      val paletteMap =
        paletteSerialized
          .split(',')
          .filter { it.contains(':') }
          .associate { entry ->
            val (k, v) = entry.split(':', limit = 2)
            k.toInt() to hexToArgb(v)
          }
      val gridRows = gridSerialized.split('/')
      if (paletteMap.isEmpty() || gridRows.size != gridHeight) return null
      val decodedFrames =
        if (framesSerialized.isBlank()) {
          emptyList()
        } else {
          framesSerialized
            .split('|')
            .map { frameStr -> frameStr.split('/') }
            .filter { rows -> rows.size == gridHeight }
        }
      PictureTemplate(
        id = id,
        title = title,
        gridWidth = gridWidth,
        gridHeight = gridHeight,
        palette = paletteMap,
        grid = gridRows,
        frames = if (decodedFrames.size > 1) decodedFrames else emptyList(),
        frameMs = frameMs.coerceIn(80, 1200),
        isCustom = true,
        version = version.coerceAtLeast(1),
        isSyncedOfficial = false,
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun SyncedTemplateEntity.toDomainOrNull(): PictureTemplate? {
    return try {
      val paletteMap =
        paletteSerialized
          .split(',')
          .filter { it.contains(':') }
          .associate { entry ->
            val (k, v) = entry.split(':', limit = 2)
            k.toInt() to hexToArgb(v)
          }
      val gridRows = gridSerialized.split('/')
      if (paletteMap.isEmpty() || gridRows.size != gridHeight) return null
      val decodedFrames =
        if (framesSerialized.isBlank()) {
          emptyList()
        } else {
          framesSerialized
            .split('|')
            .map { frameStr -> frameStr.split('/') }
            .filter { rows -> rows.size == gridHeight }
        }
      PictureTemplate(
        id = id,
        title = title,
        gridWidth = gridWidth,
        gridHeight = gridHeight,
        palette = paletteMap,
        grid = gridRows,
        frames = if (decodedFrames.size > 1) decodedFrames else emptyList(),
        frameMs = frameMs.coerceIn(80, 1200),
        isCustom = false,
        version = version.coerceAtLeast(1),
        isSyncedOfficial = true,
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun decodeJsonStringList(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val v = arr.optString(i, "").trim()
          if (v.isNotEmpty()) add(v)
        }
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  private fun decodeIdCsv(csv: String): Set<String> =
    csv
      .split(',')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()

  private fun decodeLevelsCsv(csv: String, hasCustomLevels: Boolean): Set<ChallengeMode> {
    val parsed =
      csv
        .split(',')
        .mapNotNull { key ->
          val clean = key.trim()
          ChallengeMode.entries.firstOrNull { it.key == clean && it != ChallengeMode.OFF }
        }
        .toSet()
    return if (parsed.isEmpty() && !hasCustomLevels) setOf(ChallengeMode.PRE_K) else parsed
  }

  private fun decodeTopicsCsv(csv: String): Set<PracticeTopic> =
    csv
      .split(',')
      .mapNotNull { PracticeTopic.fromKey(it.trim()) }
      .toSet()

  private fun CustomEducationLevelEntity.toDomainOrNull(): CustomEducationLevel? {
    if (name.isBlank()) return null
    return CustomEducationLevel(
      id = id,
      name = name,
      description = description,
      topics = decodeTopicsCsv(topicsCsv),
      version = version.coerceAtLeast(1),
      isCustom = true,
      isSyncedOfficial = false,
    )
  }

  private fun SyncedEducationLevelEntity.toDomainOrNull(): CustomEducationLevel? {
    if (name.isBlank()) return null
    return CustomEducationLevel(
      id = id,
      name = name,
      description = description,
      topics = decodeTopicsCsv(topicsCsv),
      version = version.coerceAtLeast(1),
      isCustom = false,
      isSyncedOfficial = true,
    )
  }

  private fun CustomQuestionEntity.toDomainOrNull(): CustomQuestion? {
    val wrong = decodeJsonStringList(wrongAnswersJson)
    if (prompt.isBlank() || correctAnswer.isBlank() || wrong.isEmpty()) return null
    val customIds = decodeIdCsv(customLevelIdsCsv)
    return CustomQuestion(
      id = id,
      prompt = prompt,
      clueItems = decodeJsonStringList(clueItemsJson),
      correctAnswer = correctAnswer,
      wrongAnswers = wrong,
      levels = decodeLevelsCsv(levelsCsv, customIds.isNotEmpty()),
      customLevelIds = customIds,
      isSentence = isSentence,
      version = version.coerceAtLeast(1),
      isCustom = true,
      isSyncedOfficial = false,
    )
  }

  private fun SyncedQuestionEntity.toDomainOrNull(): CustomQuestion? {
    val wrong = decodeJsonStringList(wrongAnswersJson)
    if (prompt.isBlank() || correctAnswer.isBlank() || wrong.isEmpty()) return null
    val customIds = decodeIdCsv(customLevelIdsCsv)
    return CustomQuestion(
      id = id,
      prompt = prompt,
      clueItems = decodeJsonStringList(clueItemsJson),
      correctAnswer = correctAnswer,
      wrongAnswers = wrong,
      levels = decodeLevelsCsv(levelsCsv, customIds.isNotEmpty()),
      customLevelIds = customIds,
      isSentence = isSentence,
      version = version.coerceAtLeast(1),
      isCustom = false,
      isSyncedOfficial = true,
    )
  }

  companion object {
    private const val PREF_SYNC_URL = "sync_source_url"
    private const val PREF_WIFI_ONLY_SYNC = "wifi_only_auto_sync"
    private const val PREF_LAST_SYNC_AT_MS = "last_sync_at_ms"
    private const val PREF_LAST_SYNC_STATUS = "last_sync_status"
    private const val PREF_ADMIN_PIN_SALT = "admin_pin_salt"
    private const val PREF_ADMIN_PIN_HASH = "admin_pin_hash"
    private const val PREF_GITHUB_PUBLISH_TOKEN = "github_publish_token"
    private const val PREF_PUBLISH_FOLDER = "github_publish_folder"
  }
}
