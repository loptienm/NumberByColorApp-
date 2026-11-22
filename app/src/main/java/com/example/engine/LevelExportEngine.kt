package com.example.engine

import com.example.model.CustomEducationLevel
import com.example.model.PracticeTopic
import org.json.JSONArray
import org.json.JSONObject

object LevelExportEngine {

  const val FORMAT_MARKER = "colorpop-level"
  private const val SCHEMA_VERSION = 1

  fun sanitizeLevelId(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(40)

  fun isLevelJson(rawJson: String): Boolean {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return false
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      fmt == FORMAT_MARKER ||
        (root.has("name") &&
          root.has("topics") &&
          !root.has("grid") &&
          !root.has("prompt"))
    } catch (_: Throwable) {
      false
    }
  }

  fun encodeLevelToJson(level: CustomEducationLevel): String {
    val root = JSONObject()
    root.put("format", FORMAT_MARKER)
    root.put("schemaVersion", SCHEMA_VERSION)
    if (level.id.isNotBlank()) {
      root.put("id", level.id)
    }
    root.put("version", level.version.coerceAtLeast(1))
    root.put("name", level.name.trim())
    root.put("description", level.description.trim())

    val topicsArr = JSONArray()
    level.topics.forEach { topic -> topicsArr.put(topic.key) }
    root.put("topics", topicsArr)

    return root.toString(2)
  }

  fun decodeLevelFromJson(
    rawJson: String,
    newId: String? = null,
    isSyncedOfficial: Boolean = false,
    preserveEmbeddedId: Boolean = false,
  ): CustomEducationLevel? {
    val trimmed = rawJson.trimStart()
    if (!trimmed.startsWith("{")) return null
    return try {
      val root = JSONObject(trimmed)
      val fmt = root.optString("format", "")
      if (fmt.isNotEmpty() && fmt != FORMAT_MARKER) return null
      if (fmt.isEmpty() && (!root.has("name") || !root.has("topics"))) return null

      val name = root.optString("name", "").trim().take(40)
      if (name.isEmpty()) return null
      val description = root.optString("description", "").trim().take(120)

      val topicsArr = root.optJSONArray("topics")
      val topics =
        if (topicsArr != null) {
          buildSet {
            for (i in 0 until topicsArr.length()) {
              val key = topicsArr.optString(i, "").trim()
              PracticeTopic.fromKey(key)?.let { add(it) }
            }
          }
        } else {
          emptySet()
        }

      val lVersion = root.optInt("version", 1).coerceAtLeast(1)
      val embeddedId =
        root.optString("id", "").ifBlank { root.optString("levelId", "") }.trim()
      val resolvedId =
        when {
          !newId.isNullOrBlank() -> newId
          (preserveEmbeddedId || isSyncedOfficial) && embeddedId.isNotBlank() -> embeddedId
          else -> "lvl_${System.currentTimeMillis()}"
        }

      CustomEducationLevel(
        id = resolvedId,
        name = name,
        description = description,
        topics = topics,
        version = lVersion,
        isCustom = !isSyncedOfficial,
        isSyncedOfficial = isSyncedOfficial,
      )
    } catch (_: Throwable) {
      null
    }
  }
}
