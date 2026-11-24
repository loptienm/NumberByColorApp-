package com.example.engine

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.KID_AVATARS
import com.example.model.KidProfile
import com.example.model.PictureTemplate
import com.example.model.clampStickerScale
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ExportedProgressEntry(
  val pictureId: String,
  val paintedGrid: String,
  val isCompleted: Boolean,
  val updatedAt: Long,
)

data class ExportedStickerPage(
  val pageId: String,
  val pageOrder: Int,
  val paperIndex: Int,
  val createdAt: Long,
)

data class ExportedPlacedSticker(
  val id: String,
  val pageId: String,
  val templateId: String,
  val xFraction: Float,
  val yFraction: Float,
  val scale: Float,
  val zOrder: Long,
  val isMirrored: Boolean,
)

data class ProfileExportBundle(
  val profile: KidProfile,
  val exportedAt: Long,
  val progress: List<ExportedProgressEntry>,
  val stickerPages: List<ExportedStickerPage>,
  val stickerPlacements: List<ExportedPlacedSticker>,
  val customLevels: List<CustomEducationLevel> = emptyList(),
  val customQuestions: List<CustomQuestion> = emptyList(),
  val customTemplates: List<PictureTemplate> = emptyList(),
) {
  val completedCount: Int
    get() = progress.count { it.isCompleted }

  val inProgressCount: Int
    get() = progress.count { !it.isCompleted }

  val stickerCount: Int
    get() = stickerPlacements.size
}

object ProfileExportEngine {

  const val FORMAT_MARKER = "colorpop-profile"
  private const val SCHEMA_VERSION = 1

  fun shortProfileCode(profileId: String): String {
    val clean = profileId.trim().removePrefix("p_").replace(Regex("[^A-Za-z0-9]"), "")
    return clean.takeLast(6).ifBlank { "PROF" }.uppercase()
  }

  fun sanitizeFileBaseName(name: String): String =
    name
      .trim()
      .replace(Regex("[^A-Za-z0-9._-]+"), "_")
      .trim('_', '.')
      .take(28)
      .ifEmpty { "kid_profile" }

  fun suggestedFileName(profile: KidProfile): String =
    "${sanitizeFileBaseName(profile.name)}_${shortProfileCode(profile.id).lowercase()}.profile.json"

  fun generateProfileId(): String =
    "p_${UUID.randomUUID().toString().replace("-", "").take(10)}"

  fun isProfileJson(rawJson: String): Boolean {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return false
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      fmt == FORMAT_MARKER || (root.has("profile") && root.has("progress"))
    } catch (_: Throwable) {
      false
    }
  }

  fun encodeProfileToJson(bundle: ProfileExportBundle): String {
    val root = JSONObject()
    root.put("format", FORMAT_MARKER)
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("exportedAt", bundle.exportedAt)

    val profileObj =
      JSONObject().apply {
        put("id", bundle.profile.id)
        put("shortCode", shortProfileCode(bundle.profile.id))
        put("name", bundle.profile.name.trim().ifBlank { "Player 1" })
        put("avatar", bundle.profile.avatar.ifBlank { KID_AVATARS.first() })
        put("challengeMode", bundle.profile.challengeMode.key)
        put("soundEnabled", bundle.profile.soundEnabled)
        if (!bundle.profile.customLevelId.isNullOrBlank()) {
          put("customLevelId", bundle.profile.customLevelId.trim())
        }
      }
    root.put("profile", profileObj)

    val summaryObj =
      JSONObject().apply {
        put("completedCount", bundle.completedCount)
        put("inProgressCount", bundle.inProgressCount)
        put("stickerPageCount", bundle.stickerPages.size)
        put("stickerPlacementCount", bundle.stickerPlacements.size)
      }
    root.put("summary", summaryObj)

    val progressArr = JSONArray()
    bundle.progress.forEach { entry ->
      progressArr.put(
        JSONObject().apply {
          put("pictureId", entry.pictureId)
          put("paintedGrid", entry.paintedGrid)
          put("isCompleted", entry.isCompleted)
          put("updatedAt", entry.updatedAt)
        }
      )
    }
    root.put("progress", progressArr)

    val pagesArr = JSONArray()
    bundle.stickerPages.forEach { page ->
      pagesArr.put(
        JSONObject().apply {
          put("pageId", page.pageId)
          put("pageOrder", page.pageOrder)
          put("paperIndex", page.paperIndex)
          put("createdAt", page.createdAt)
        }
      )
    }
    root.put("stickerPages", pagesArr)

    val placementsArr = JSONArray()
    bundle.stickerPlacements.forEach { sticker ->
      placementsArr.put(
        JSONObject().apply {
          put("id", sticker.id)
          put("pageId", sticker.pageId)
          put("templateId", sticker.templateId)
          put("xFraction", sticker.xFraction.toDouble())
          put("yFraction", sticker.yFraction.toDouble())
          put("scale", sticker.scale.toDouble())
          put("zOrder", sticker.zOrder)
          put("isMirrored", sticker.isMirrored)
        }
      )
    }
    root.put("stickerPlacements", placementsArr)

    if (bundle.customLevels.isNotEmpty()) {
      val levelsArr = JSONArray()
      bundle.customLevels.forEach { lvl ->
        levelsArr.put(JSONObject(LevelExportEngine.encodeLevelToJson(lvl)))
      }
      root.put("customLevels", levelsArr)
    }

    if (bundle.customQuestions.isNotEmpty()) {
      val questionsArr = JSONArray()
      bundle.customQuestions.forEach { q ->
        questionsArr.put(JSONObject(QuestionExportEngine.encodeQuestionToJson(q)))
      }
      root.put("customQuestions", questionsArr)
    }

    if (bundle.customTemplates.isNotEmpty()) {
      val templatesArr = JSONArray()
      bundle.customTemplates.forEach { tpl ->
        templatesArr.put(JSONObject(PageExportEngine.encodePageToJson(tpl)))
      }
      root.put("customTemplates", templatesArr)
    }

    return root.toString(2)
  }

  fun decodeProfileFromJson(rawJson: String): ProfileExportBundle? {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return null
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      if (fmt.isNotEmpty() && fmt != FORMAT_MARKER) return null

      val profileObj = root.optJSONObject("profile") ?: return null
      val rawName = profileObj.optString("name", "").trim().take(16)
      if (rawName.isEmpty()) return null
      val profileId = profileObj.optString("id", "").trim().ifBlank { generateProfileId() }
      val avatar = profileObj.optString("avatar", "").trim().ifBlank { KID_AVATARS.first() }
      val challengeMode = ChallengeMode.fromKey(profileObj.optString("challengeMode", ChallengeMode.PRE_K.key))
      val soundEnabled = profileObj.optBoolean("soundEnabled", true)
      val customLevelId = profileObj.optString("customLevelId", "").trim().ifBlank { null }

      val profile =
        KidProfile(
          id = profileId,
          name = rawName,
          avatar = avatar,
          challengeMode = challengeMode,
          soundEnabled = soundEnabled,
          customLevelId = customLevelId,
        )

      val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

      val progress =
        buildList {
          val arr = root.optJSONArray("progress") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val picId = item.optString("pictureId", "").trim()
            if (picId.isEmpty()) continue
            add(
              ExportedProgressEntry(
                pictureId = picId,
                paintedGrid = item.optString("paintedGrid", ""),
                isCompleted = item.optBoolean("isCompleted", false),
                updatedAt = item.optLong("updatedAt", exportedAt),
              )
            )
          }
        }

      val stickerPages =
        buildList {
          val arr = root.optJSONArray("stickerPages") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val pageId = item.optString("pageId", "").trim()
            if (pageId.isEmpty()) continue
            add(
              ExportedStickerPage(
                pageId = pageId,
                pageOrder = item.optInt("pageOrder", i),
                paperIndex = item.optInt("paperIndex", 0),
                createdAt = item.optLong("createdAt", exportedAt + i),
              )
            )
          }
        }

      val stickerPlacements =
        buildList {
          val arr = root.optJSONArray("stickerPlacements") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val pageId = item.optString("pageId", "").trim()
            val tplId = item.optString("templateId", "").trim()
            if (pageId.isEmpty() || tplId.isEmpty()) continue
            val id = item.optString("id", "").trim().ifBlank { UUID.randomUUID().toString() }
            add(
              ExportedPlacedSticker(
                id = id,
                pageId = pageId,
                templateId = tplId,
                xFraction = item.optDouble("xFraction", 0.5).toFloat().coerceIn(0f, 1f),
                yFraction = item.optDouble("yFraction", 0.5).toFloat().coerceIn(0f, 1f),
                scale = clampStickerScale(item.optDouble("scale", 1.0).toFloat()),
                zOrder = item.optLong("zOrder", (i + 1).toLong()),
                isMirrored = item.optBoolean("isMirrored", false),
              )
            )
          }
        }

      val customLevels =
        buildList {
          val arr = root.optJSONArray("customLevels") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            LevelExportEngine.decodeLevelFromJson(
              rawJson = obj.toString(),
              preserveEmbeddedId = true,
            )?.let(::add)
          }
        }

      val customQuestions =
        buildList {
          val arr = root.optJSONArray("customQuestions") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            QuestionExportEngine.decodeQuestionFromJson(
              rawJson = obj.toString(),
              preserveEmbeddedId = true,
            )?.let(::add)
          }
        }

      val customTemplates =
        buildList {
          val arr = root.optJSONArray("customTemplates") ?: JSONArray()
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            PageExportEngine.decodePageFromJson(
              rawJson = obj.toString(),
              preserveEmbeddedId = true,
            )?.let(::add)
          }
        }

      ProfileExportBundle(
        profile = profile,
        exportedAt = exportedAt,
        progress = progress,
        stickerPages = stickerPages,
        stickerPlacements = stickerPlacements,
        customLevels = customLevels,
        customQuestions = customQuestions,
        customTemplates = customTemplates,
      )
    } catch (_: Throwable) {
      null
    }
  }

  suspend fun writeProfileToUri(
    context: Context,
    destinationUri: Uri,
    bundle: ProfileExportBundle,
  ): Boolean =
    withContext(Dispatchers.IO) {
      try {
        val bytes = encodeProfileToJson(bundle).toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
          out.write(bytes)
          out.flush()
          true
        } ?: false
      } catch (_: Throwable) {
        false
      }
    }

  suspend fun writeProfileToCache(
    context: Context,
    bundle: ProfileExportBundle,
  ): Uri =
    withContext(Dispatchers.IO) {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val cutoffMs = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
      exportDir.listFiles()?.forEach { f ->
        if (f.lastModified() < cutoffMs) f.delete()
      }
      val outFile = File(exportDir, suggestedFileName(bundle.profile))
      outFile.writeBytes(encodeProfileToJson(bundle).toByteArray(Charsets.UTF_8))
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

  fun launchShareProfileIntent(
    context: Context,
    uri: Uri,
    profile: KidProfile,
  ): Boolean {
    return try {
      val cleanName = profile.name.trim().ifBlank { "Kid Profile" }
      val shortCode = shortProfileCode(profile.id)
      val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "application/json"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, "Number by Color Profile: $cleanName (#$shortCode)")
          putExtra(
            Intent.EXTRA_TEXT,
            "Number by Color profile backup for $cleanName (#$shortCode). Open Settings -> Profile Settings -> Import Profile in the app to load this file.",
          )
          clipData = ClipData.newRawUri(cleanName, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      val chooser = Intent.createChooser(sendIntent, "Share $cleanName's Profile")
      if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    } catch (_: Throwable) {
      false
    }
  }

  suspend fun readTextFromUri(
    context: Context,
    uri: Uri,
  ): String? =
    withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
          it.readText()
        }
      } catch (_: Throwable) {
        null
      }
    }
}
