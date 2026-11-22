package com.example.engine

import com.example.model.ChallengeMode
import com.example.model.CustomQuestion
import org.json.JSONArray
import org.json.JSONObject

object QuestionExportEngine {

  const val FORMAT_MARKER = "colorpop-question"
  private const val SCHEMA_VERSION = 1

  fun sanitizeQuestionId(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(40)

  fun isQuestionJson(rawJson: String): Boolean {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return false
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      fmt == FORMAT_MARKER ||
        (root.has("prompt") && root.has("correctAnswer") && root.has("wrongAnswers"))
    } catch (_: Throwable) {
      false
    }
  }

  fun encodeQuestionToJson(question: CustomQuestion): String {
    val root = JSONObject()
    root.put("format", FORMAT_MARKER)
    root.put("schemaVersion", SCHEMA_VERSION)
    if (question.id.isNotBlank()) {
      root.put("id", question.id)
    }
    root.put("version", question.version.coerceAtLeast(1))
    root.put("prompt", question.prompt.trim())
    root.put("correctAnswer", question.correctAnswer.trim())
    root.put("isSentence", question.isSentence)

    val clueArr = JSONArray()
    question.clueItems.forEach { token -> clueArr.put(token) }
    root.put("clueItems", clueArr)

    val wrongArr = JSONArray()
    question.wrongAnswers
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .take(3)
      .forEach { w -> wrongArr.put(w) }
    root.put("wrongAnswers", wrongArr)

    val levelArr = JSONArray()
    val effectiveLevels =
      if (question.levels.isEmpty() && question.customLevelIds.isEmpty()) {
        listOf(ChallengeMode.PRE_K)
      } else {
        question.levels.filter { it != ChallengeMode.OFF }
      }
    effectiveLevels.forEach { mode -> levelArr.put(mode.key) }
    root.put("levels", levelArr)

    if (question.customLevelIds.isNotEmpty()) {
      val customArr = JSONArray()
      question.customLevelIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
        customArr.put(it)
      }
      root.put("customLevels", customArr)
    }

    return root.toString(2)
  }

  fun decodeQuestionFromJson(
    rawJson: String,
    newId: String? = null,
    isSyncedOfficial: Boolean = false,
    preserveEmbeddedId: Boolean = false,
  ): CustomQuestion? {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return null
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      if (fmt.isNotEmpty() && fmt != FORMAT_MARKER) return null
      if (fmt.isEmpty() && (!root.has("prompt") || !root.has("correctAnswer"))) return null

      val prompt = root.optString("prompt", "").trim().take(180)
      val correctAnswer = root.optString("correctAnswer", "").trim().take(60)
      if (prompt.isEmpty() || correctAnswer.isEmpty()) return null

      val wrongArr = root.optJSONArray("wrongAnswers") ?: return null
      val wrongAnswers =
        buildList {
          for (i in 0 until wrongArr.length()) {
            val opt = wrongArr.optString(i, "").trim().take(60)
            if (opt.isNotEmpty() && opt != correctAnswer && opt !in this) {
              add(opt)
            }
            if (size >= 3) break
          }
        }
      if (wrongAnswers.isEmpty()) return null

      val clueArr = root.optJSONArray("clueItems")
      val clueItems =
        if (clueArr != null) {
          buildList {
            for (i in 0 until clueArr.length()) {
              val item = clueArr.optString(i, "").trim().take(60)
              if (item.isNotEmpty()) add(item)
            }
          }
        } else {
          emptyList()
        }

      val customLevelsArr = root.optJSONArray("customLevels")
      val customLevelIds =
        if (customLevelsArr != null) {
          buildSet {
            for (i in 0 until customLevelsArr.length()) {
              val cid = customLevelsArr.optString(i, "").trim()
              if (cid.isNotEmpty()) add(cid)
            }
          }
        } else {
          emptySet()
        }

      val levelsArr = root.optJSONArray("levels")
      val parsedLevels =
        if (levelsArr != null) {
          buildSet {
            for (i in 0 until levelsArr.length()) {
              val key = levelsArr.optString(i, "").trim()
              val mode = ChallengeMode.entries.firstOrNull { it.key == key && it != ChallengeMode.OFF }
              if (mode != null) add(mode)
            }
          }
        } else {
          emptySet()
        }
      val levels =
        if (parsedLevels.isEmpty() && customLevelIds.isEmpty()) setOf(ChallengeMode.PRE_K)
        else parsedLevels

      val isSentence = root.optBoolean("isSentence", false)
      val qVersion = root.optInt("version", 1).coerceAtLeast(1)
      val embeddedId =
        root.optString("id", "").ifBlank { root.optString("questionId", "") }.trim()
      val resolvedId =
        when {
          !newId.isNullOrBlank() -> newId
          (preserveEmbeddedId || isSyncedOfficial) && embeddedId.isNotBlank() -> embeddedId
          else -> "q_${System.currentTimeMillis()}"
        }

      CustomQuestion(
        id = resolvedId,
        prompt = prompt,
        clueItems = clueItems,
        correctAnswer = correctAnswer,
        wrongAnswers = wrongAnswers,
        levels = levels,
        customLevelIds = customLevelIds,
        isSentence = isSentence,
        version = qVersion,
        isCustom = !isSyncedOfficial,
        isSyncedOfficial = isSyncedOfficial,
      )
    } catch (_: Throwable) {
      null
    }
  }
}
