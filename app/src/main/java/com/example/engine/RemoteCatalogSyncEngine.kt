package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.example.data.SyncedEducationLevelEntity
import com.example.data.SyncedQuestionEntity
import com.example.data.SyncedTemplateEntity
import com.example.model.ChallengeMode
import com.example.model.CustomEducationLevel
import com.example.model.CustomQuestion
import com.example.model.PictureTemplate
import com.example.model.argbToHex
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class HttpResponse(
  val code: Int,
  val body: String,
)

data class PublishPageResult(
  val success: Boolean,
  val publishedTemplate: PictureTemplate? = null,
  val syncedEntity: SyncedTemplateEntity? = null,
  val remotePath: String = "",
  val message: String,
)

data class PublishQuestionResult(
  val success: Boolean,
  val publishedQuestion: CustomQuestion? = null,
  val syncedEntity: SyncedQuestionEntity? = null,
  val remotePath: String = "",
  val message: String,
)

data class PublishLevelResult(
  val success: Boolean,
  val publishedLevel: CustomEducationLevel? = null,
  val syncedEntity: SyncedEducationLevelEntity? = null,
  val remotePath: String = "",
  val message: String,
)

sealed interface SyncSource {
  data class GitHubRepo(
    val owner: String,
    val repo: String,
    val branch: String? = null,
    val subPath: String = "",
  ) : SyncSource

  data class DirectCatalogJson(val url: String) : SyncSource
}

data class CatalogSyncResult(
  val upserts: List<SyncedTemplateEntity>,
  val activeRemoteIds: Set<String>?,
  val totalRemotePages: Int,
  val downloadedCount: Int,
  val statusMessage: String,
  val errorMessage: String? = null,
  val questionUpserts: List<SyncedQuestionEntity> = emptyList(),
  val activeRemoteQuestionIds: Set<String>? = null,
  val totalRemoteQuestions: Int = 0,
  val downloadedQuestionCount: Int = 0,
  val levelUpserts: List<SyncedEducationLevelEntity> = emptyList(),
  val activeRemoteLevelIds: Set<String>? = null,
  val totalRemoteLevels: Int = 0,
  val downloadedLevelCount: Int = 0,
)

object RemoteCatalogSyncEngine {

  const val DEFAULT_SYNC_SOURCE_URL = "https://github.com/loptienm/NumberByColor.git"
  const val AUTO_SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

  private val IGNORED_JSON_NAMES =
    setOf("package.json", "package-lock.json", "tsconfig.json", "catalog.json")

  private val IGNORED_DIRS =
    setOf("node_modules", "build", "gradle", ".git", ".github", ".idea")

  fun parseSyncSource(rawUrl: String): SyncSource? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null

    val normalized =
      if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "https://$trimmed"
      }

    return try {
      val uri = URI(normalized)
      val host = (uri.host ?: return null).lowercase()
      val segments =
        (uri.path ?: "")
          .split('/')
          .filter { it.isNotBlank() }

      when {
        host == "github.com" || host == "www.github.com" -> {
          if (segments.size < 2) return null
          val owner = segments[0]
          val repo = segments[1].removeSuffix(".git")
          if (owner.isBlank() || repo.isBlank()) return null
          val (branch, subPath) =
            if (segments.size >= 4 && segments[2] == "tree") {
              segments[3] to segments.drop(4).joinToString("/")
            } else {
              null to ""
            }
          SyncSource.GitHubRepo(
            owner = owner,
            repo = repo,
            branch = branch,
            subPath = subPath,
          )
        }
        host == "raw.githubusercontent.com" -> {
          if (normalized.lowercase().endsWith(".json")) {
            SyncSource.DirectCatalogJson(normalized)
          } else if (segments.size >= 3) {
            SyncSource.GitHubRepo(
              owner = segments[0],
              repo = segments[1].removeSuffix(".git"),
              branch = segments[2],
              subPath = segments.drop(3).joinToString("/"),
            )
          } else {
            SyncSource.DirectCatalogJson(normalized)
          }
        }
        else -> SyncSource.DirectCatalogJson(normalized)
      }
    } catch (_: Throwable) {
      null
    }
  }

  fun hasInternetConnection(context: Context): Boolean {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
      false
    }
  }

  fun isWifiConnected(context: Context): Boolean {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
          caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (_: Throwable) {
      false
    }
  }

  suspend fun defaultHttpFetch(urlString: String): HttpResponse =
    defaultHttpRequest(method = "GET", urlString = urlString)

  suspend fun defaultHttpRequest(
    method: String,
    urlString: String,
    headers: Map<String, String> = emptyMap(),
    jsonBody: String? = null,
  ): HttpResponse =
    withContext(Dispatchers.IO) {
      var conn: HttpURLConnection? = null
      try {
        val url = URL(urlString)
        conn =
          (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            useCaches = false
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json, application/json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "NumberByColor-Android")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (jsonBody != null) {
              doOutput = true
              setRequestProperty("Content-Type", "application/json; charset=utf-8")
              outputStream.use { out -> out.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
          }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        HttpResponse(code = code, body = body)
      } catch (t: Throwable) {
        HttpResponse(code = -1, body = t.message.orEmpty())
      } finally {
        conn?.disconnect()
      }
    }

  private data class DiscoveredRemoteFile(
    val path: String,
    val name: String,
    val sha: String,
    val downloadUrl: String,
    val apiFallbackUrl: String = "",
  )

  private fun extractHttpErrorDetail(resp: HttpResponse, requestUrl: String): String {
    val rawBody = resp.body.trim()
    val apiMessage =
      try {
        if (rawBody.startsWith("{")) {
          JSONObject(rawBody).optString("message", "").trim()
        } else {
          ""
        }
      } catch (_: Throwable) {
        ""
      }
    val detail = apiMessage.ifBlank { rawBody.take(180) }
    return if (detail.isNotBlank()) {
      "HTTP ${resp.code}: $detail (URL: $requestUrl)"
    } else {
      "HTTP ${resp.code} (URL: $requestUrl)"
    }
  }

  suspend fun syncFromSource(
    sourceUrl: String,
    existingSynced: List<SyncedTemplateEntity>,
    nowMs: Long = System.currentTimeMillis(),
    token: String = "",
    existingSyncedQuestions: List<SyncedQuestionEntity> = emptyList(),
    existingSyncedLevels: List<SyncedEducationLevelEntity> = emptyList(),
    httpFetch: suspend (String) -> HttpResponse = { url ->
      val cleanToken = token.trim()
      val headers =
        if (
          cleanToken.isNotBlank() &&
            (url.contains("api.github.com", ignoreCase = true) ||
              url.contains("raw.githubusercontent.com", ignoreCase = true))
        ) {
          mapOf("Authorization" to "Bearer $cleanToken")
        } else {
          emptyMap()
        }
      defaultHttpRequest(method = "GET", urlString = url, headers = headers)
    },
  ): CatalogSyncResult {
    val source =
      parseSyncSource(sourceUrl)
        ?: return CatalogSyncResult(
          upserts = emptyList(),
          activeRemoteIds = null,
          totalRemotePages = existingSynced.size,
          downloadedCount = 0,
          statusMessage = "Enter a valid GitHub repo or catalog URL (got: \"$sourceUrl\")",
          errorMessage = "Invalid sync URL",
        )

    return when (source) {
      is SyncSource.GitHubRepo ->
        syncGitHubRepo(
          source = source,
          existingSynced = existingSynced,
          existingSyncedQuestions = existingSyncedQuestions,
          existingSyncedLevels = existingSyncedLevels,
          nowMs = nowMs,
          hasAuthToken = token.isNotBlank(),
          httpFetch = httpFetch,
        )
      is SyncSource.DirectCatalogJson -> syncDirectCatalog(source.url, existingSynced, nowMs, httpFetch)
    }
  }

  private fun encodeGitHubPath(path: String): String {
    val clean = path.trim('/')
    if (clean.isEmpty()) return ""
    return clean
      .split('/')
      .filter { it.isNotBlank() }
      .joinToString("/") { seg ->
        java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
      }
  }

  private fun buildGitHubContentsUrl(source: SyncSource.GitHubRepo, pathOverride: String): String {
    val encodedPath = encodeGitHubPath(pathOverride)
    val pathSuffix = if (encodedPath.isEmpty()) "" else "/$encodedPath"
    val refQuery = if (!source.branch.isNullOrBlank()) "?ref=${source.branch}" else ""
    return "https://api.github.com/repos/${source.owner}/${source.repo}/contents$pathSuffix$refQuery"
  }

  private fun decodeRemoteFileBody(rawBody: String): String {
    val trimmed = rawBody.trimStart()
    if (!trimmed.startsWith("{")) return rawBody
    return try {
      val obj = JSONObject(trimmed)
      if (obj.has("grid") && obj.has("palette")) return rawBody
      if (obj.has("prompt") && obj.has("correctAnswer")) return rawBody
      if (obj.has("name") && obj.has("topics")) return rawBody
      val encoding = obj.optString("encoding", "")
      val encoded = obj.optString("content", "").replace("\n", "").replace("\r", "")
      if (encoding.equals("base64", ignoreCase = true) && encoded.isNotBlank()) {
        String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
      } else {
        rawBody
      }
    } catch (_: Throwable) {
      rawBody
    }
  }

  private suspend fun syncGitHubRepo(
    source: SyncSource.GitHubRepo,
    existingSynced: List<SyncedTemplateEntity>,
    existingSyncedQuestions: List<SyncedQuestionEntity>,
    existingSyncedLevels: List<SyncedEducationLevelEntity>,
    nowMs: Long,
    hasAuthToken: Boolean,
    httpFetch: suspend (String) -> HttpResponse,
  ): CatalogSyncResult {
    data class BranchScanResult(
      val earlyReturn: CatalogSyncResult? = null,
      val discoveredFiles: List<DiscoveredRemoteFile> = emptyList(),
      val catalogJsonDownloadUrl: String? = null,
      val checkedLabels: List<String> = emptyList(),
      val listingClean: Boolean = true,
      val firstFetchError: String? = null,
    )

    suspend fun scanBranch(branchSource: SyncSource.GitHubRepo): BranchScanResult {
      val startPath = branchSource.subPath.trim('/')
      val rootApiUrl = buildGitHubContentsUrl(branchSource, startPath)
      val rootResp = httpFetch(rootApiUrl)

      if (rootResp.code == 404 && rootResp.body.contains("empty", ignoreCase = true)) {
        return BranchScanResult(
          earlyReturn =
            CatalogSyncResult(
              upserts = emptyList(),
              activeRemoteIds = null,
              totalRemotePages = existingSynced.size,
              downloadedCount = 0,
              statusMessage =
                if (existingSynced.isNotEmpty()) {
                  "Connected to GitHub — repository is empty. Kept ${existingSynced.size} cached page(s)."
                } else {
                  "Connected to GitHub — no pages uploaded yet"
                },
            )
        )
      }
      if (rootResp.code == 403 || rootResp.code == 429) {
        val detail = extractHttpErrorDetail(rootResp, rootApiUrl)
        return BranchScanResult(
          earlyReturn =
            CatalogSyncResult(
              upserts = emptyList(),
              activeRemoteIds = null,
              totalRemotePages = existingSynced.size,
              downloadedCount = 0,
              statusMessage = "GitHub rate limit/access error — $detail",
              errorMessage = "HTTP ${rootResp.code}",
            )
        )
      }
      if (rootResp.code !in 200..299) {
        val detail = extractHttpErrorDetail(rootResp, rootApiUrl)
        val privateHint =
          if (rootResp.code == 404 && !hasAuthToken) {
            " — If this repo is Private on GitHub, make it Public so all devices can sync."
          } else {
            ""
          }
        return BranchScanResult(
          earlyReturn =
            CatalogSyncResult(
              upserts = emptyList(),
              activeRemoteIds = null,
              totalRemotePages = existingSynced.size,
              downloadedCount = 0,
              statusMessage =
                if (rootResp.code < 0) "Offline or network error: ${rootResp.body.ifBlank { "no connection" }}"
                else "Sync failed — $detail$privateHint",
              errorMessage = if (rootResp.code < 0) "Network error" else detail,
            )
        )
      }

      val trimmedRoot = rootResp.body.trimStart()
      if (!trimmedRoot.startsWith("[")) {
        return BranchScanResult(
          earlyReturn = syncDirectCatalog(rootApiUrl, existingSynced, nowMs, httpFetch)
        )
      }

      val discoveredFiles = mutableListOf<DiscoveredRemoteFile>()
      var catalogJsonDownloadUrl: String? = null
      val checkedLabels = mutableListOf<String>()
      val visitedDirs = mutableSetOf<String>()
      val dirQueue = ArrayDeque<String>()
      var listingClean = true
      var firstFetchError: String? = null

      fun collectDirectoryEntries(arr: JSONArray) {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val type = obj.optString("type", "")
          val name = obj.optString("name", "")
          val path = obj.optString("path", name)
          val sha = obj.optString("sha", "")
          val rawDownloadUrl =
            if (obj.isNull("download_url")) "" else obj.optString("download_url", "").trim()
          val rawApiUrl = if (obj.isNull("url")) "" else obj.optString("url", "").trim()
          val cleanDownloadUrl = rawDownloadUrl.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
          val cleanApiUrl =
            rawApiUrl.takeIf { it.isNotBlank() && it != "null" }
              ?: buildGitHubContentsUrl(branchSource, path)

          if (type == "file") {
            val lower = name.lowercase()
            val fetchTarget = cleanDownloadUrl.ifBlank { cleanApiUrl }
            if (lower == "catalog.json" && fetchTarget.isNotBlank()) {
              catalogJsonDownloadUrl = fetchTarget
            } else if (lower.endsWith(".json") && lower !in IGNORED_JSON_NAMES && fetchTarget.isNotBlank()) {
              discoveredFiles.add(
                DiscoveredRemoteFile(
                  path = path,
                  name = name,
                  sha = sha,
                  downloadUrl = fetchTarget,
                  apiFallbackUrl = cleanApiUrl,
                )
              )
            }
          } else if (type == "dir") {
            val lower = name.lowercase()
            if (!name.startsWith(".") && lower !in IGNORED_DIRS && path.isNotBlank()) {
              dirQueue.addLast(path)
            }
          }
        }
      }

      visitedDirs.add(startPath)
      checkedLabels.add(startPath.ifBlank { "root" })
      val rootArray =
        try {
          JSONArray(trimmedRoot)
        } catch (_: Throwable) {
          return BranchScanResult(
            earlyReturn =
              CatalogSyncResult(
                upserts = emptyList(),
                activeRemoteIds = null,
                totalRemotePages = existingSynced.size,
                downloadedCount = 0,
                statusMessage = "Couldn't read GitHub folder listing",
                errorMessage = "Invalid JSON listing",
              )
          )
        }
      collectDirectoryEntries(rootArray)

      while (dirQueue.isNotEmpty() && visitedDirs.size < 40) {
        val dirPath = dirQueue.removeFirst()
        if (!visitedDirs.add(dirPath)) continue
        checkedLabels.add(dirPath.ifBlank { "root" })
        val subUrl = buildGitHubContentsUrl(branchSource, dirPath)
        val subResp = httpFetch(subUrl)
        if (subResp.code in 200..299 && subResp.body.trimStart().startsWith("[")) {
          try {
            collectDirectoryEntries(JSONArray(subResp.body))
          } catch (_: Throwable) {
            listingClean = false
            if (firstFetchError == null) firstFetchError = "Invalid JSON in $subUrl"
          }
        } else {
          listingClean = false
          if (firstFetchError == null) {
            firstFetchError =
              if (subResp.code < 0) "Network error: ${subResp.body.ifBlank { "no connection" }}"
              else extractHttpErrorDetail(subResp, subUrl)
          }
        }
      }

      return BranchScanResult(
        discoveredFiles = discoveredFiles,
        catalogJsonDownloadUrl = catalogJsonDownloadUrl,
        checkedLabels = checkedLabels,
        listingClean = listingClean,
        firstFetchError = firstFetchError,
      )
    }

    var scan = scanBranch(source)
    if (scan.earlyReturn != null) return scan.earlyReturn!!

    // If default branch had zero pages, check other branches (e.g. main vs master vs pages)
    if (
      scan.discoveredFiles.isEmpty() &&
        scan.catalogJsonDownloadUrl == null &&
        source.branch == null &&
        scan.listingClean
    ) {
      val branchesUrl = "https://api.github.com/repos/${source.owner}/${source.repo}/branches"
      val branchesResp = httpFetch(branchesUrl)
      if (branchesResp.code in 200..299 && branchesResp.body.trimStart().startsWith("[")) {
        val branchNames =
          try {
            val arr = JSONArray(branchesResp.body)
            (0 until arr.length())
              .mapNotNull { i -> arr.optJSONObject(i)?.optString("name", "")?.trim() }
              .filter { it.isNotBlank() }
              .take(5)
          } catch (_: Throwable) {
            emptyList()
          }
        for (branchName in branchNames) {
          val candidate = scanBranch(source.copy(branch = branchName))
          if (
            candidate.earlyReturn == null &&
              (candidate.discoveredFiles.isNotEmpty() || candidate.catalogJsonDownloadUrl != null)
          ) {
            scan = candidate
            break
          }
        }
      }
    }

    // Fallback: if no page JSON files exist directly, check catalog.json
    if (scan.discoveredFiles.isEmpty() && scan.catalogJsonDownloadUrl != null) {
      return syncDirectCatalog(scan.catalogJsonDownloadUrl!!, existingSynced, nowMs, httpFetch)
    }

    if (scan.discoveredFiles.isEmpty()) {
      if (!scan.listingClean && scan.firstFetchError != null) {
        return CatalogSyncResult(
          upserts = emptyList(),
          activeRemoteIds = null,
          totalRemotePages = existingSynced.size,
          downloadedCount = 0,
          statusMessage = "Sync failed — ${scan.firstFetchError}",
          errorMessage = scan.firstFetchError,
        )
      }
      val checkedSummary = scan.checkedLabels.take(6).joinToString(", ")
      val suffix = if (scan.checkedLabels.size > 6) ", …" else ""
      val keptNote =
        if (existingSynced.isNotEmpty()) ". Kept ${existingSynced.size} cached page(s)."
        else ""
      return CatalogSyncResult(
        upserts = emptyList(),
        activeRemoteIds = null,
        totalRemotePages = existingSynced.size,
        downloadedCount = 0,
        statusMessage = "Connected to GitHub — no .colorpop.json files found (checked: $checkedSummary$suffix)$keptNote",
      )
    }

    val existingByPath = existingSynced.associateBy { it.sourceFileName }
    val existingQuestionsByPath = existingSyncedQuestions.associateBy { it.sourceFileName }
    val existingLevelsByPath = existingSyncedLevels.associateBy { it.sourceFileName }
    val resolvedById = linkedMapOf<String, SyncedTemplateEntity>()
    val toUpsert = mutableListOf<SyncedTemplateEntity>()
    val resolvedQuestionsById = linkedMapOf<String, SyncedQuestionEntity>()
    val questionsToUpsert = mutableListOf<SyncedQuestionEntity>()
    val resolvedLevelsById = linkedMapOf<String, SyncedEducationLevelEntity>()
    val levelsToUpsert = mutableListOf<SyncedEducationLevelEntity>()
    var downloaded = 0
    var downloadedQuestions = 0
    var downloadedLevels = 0
    var hadFetchFailure = !scan.listingClean
    var firstFetchError = scan.firstFetchError

    for (file in scan.discoveredFiles) {
      val cached = existingByPath[file.path]
      if (cached != null && file.sha.isNotBlank() && cached.sourceSha == file.sha) {
        val existingWinner = resolvedById[cached.id]
        if (existingWinner == null || cached.version > existingWinner.version) {
          resolvedById[cached.id] = cached
        }
        continue
      }
      val cachedQ = existingQuestionsByPath[file.path]
      if (cachedQ != null && file.sha.isNotBlank() && cachedQ.sourceSha == file.sha) {
        val existingWinner = resolvedQuestionsById[cachedQ.id]
        if (existingWinner == null || cachedQ.version > existingWinner.version) {
          resolvedQuestionsById[cachedQ.id] = cachedQ
        }
        continue
      }
      val cachedL = existingLevelsByPath[file.path]
      if (cachedL != null && file.sha.isNotBlank() && cachedL.sourceSha == file.sha) {
        val existingWinner = resolvedLevelsById[cachedL.id]
        if (existingWinner == null || cachedL.version > existingWinner.version) {
          resolvedLevelsById[cachedL.id] = cachedL
        }
        continue
      }

      var fileResp = httpFetch(file.downloadUrl)
      if (
        fileResp.code !in 200..299 &&
          file.apiFallbackUrl.isNotBlank() &&
          file.apiFallbackUrl != file.downloadUrl
      ) {
        val fallbackResp = httpFetch(file.apiFallbackUrl)
        if (fallbackResp.code in 200..299) {
          fileResp = fallbackResp
        }
      }
      if (fileResp.code !in 200..299) {
        hadFetchFailure = true
        if (firstFetchError == null) {
          firstFetchError =
            if (fileResp.code < 0) "Network error: ${fileResp.body.ifBlank { "no connection" }}"
            else extractHttpErrorDetail(fileResp, file.downloadUrl)
        }
        if (cached != null) resolvedById[cached.id] = cached
        if (cachedQ != null) resolvedQuestionsById[cachedQ.id] = cachedQ
        if (cachedL != null) resolvedLevelsById[cachedL.id] = cachedL
        continue
      }

      val rawJsonBody = decodeRemoteFileBody(fileResp.body)
      if (file.name.lowercase().endsWith(".level.json") || LevelExportEngine.isLevelJson(rawJsonBody)) {
        val fallbackLId =
          LevelExportEngine.sanitizeLevelId(
            file.name.removeSuffix(".level.json").removeSuffix(".json")
          ).ifBlank { "lvl_official_${nowMs}_${scan.discoveredFiles.indexOf(file)}" }
        val decodedL =
          LevelExportEngine.decodeLevelFromJson(
            rawJson = rawJsonBody,
            isSyncedOfficial = true,
            preserveEmbeddedId = true,
          )?.let { lvl ->
            if (lvl.id.startsWith("lvl_") || lvl.id.isBlank()) lvl.copy(id = fallbackLId) else lvl
          } ?: continue

        val lEntity =
          decodedL.toSyncedEntity(
            sourceFileName = file.path,
            sourceSha = file.sha,
            syncedAt = nowMs,
          )
        downloadedLevels++
        val prevL = resolvedLevelsById[lEntity.id]
        if (prevL == null || lEntity.version >= prevL.version) {
          resolvedLevelsById[lEntity.id] = lEntity
        }
        continue
      }

      if (file.name.lowercase().endsWith(".question.json") || QuestionExportEngine.isQuestionJson(rawJsonBody)) {
        val fallbackQId =
          QuestionExportEngine.sanitizeQuestionId(
            file.name.removeSuffix(".question.json").removeSuffix(".json")
          ).ifBlank { "q_official_${nowMs}_${scan.discoveredFiles.indexOf(file)}" }
        val decodedQ =
          QuestionExportEngine.decodeQuestionFromJson(
            rawJson = rawJsonBody,
            isSyncedOfficial = true,
            preserveEmbeddedId = true,
          )?.let { q ->
            if (q.id.startsWith("q_") || q.id.isBlank()) q.copy(id = fallbackQId) else q
          } ?: continue

        val qEntity =
          decodedQ.toSyncedEntity(
            sourceFileName = file.path,
            sourceSha = file.sha,
            syncedAt = nowMs,
          )
        downloadedQuestions++
        val prevQ = resolvedQuestionsById[qEntity.id]
        if (prevQ == null || qEntity.version >= prevQ.version) {
          resolvedQuestionsById[qEntity.id] = qEntity
        }
        continue
      }

      val fallbackId =
        PageExportEngine.sanitizePageId(
          file.name.removeSuffix(".colorpop.json").removeSuffix(".json")
        ).ifBlank { "official_${nowMs}_${scan.discoveredFiles.indexOf(file)}" }

      val decoded =
        PageExportEngine.decodePageFromJson(
          rawJson = rawJsonBody,
          isSyncedOfficial = true,
          preserveEmbeddedId = true,
        )?.let { tpl ->
          if (tpl.id.startsWith("custom_") || tpl.id.isBlank()) tpl.copy(id = fallbackId) else tpl
        }

      if (decoded == null) {
        continue
      }

      val entity =
        decoded.toSyncedEntity(
          sourceFileName = file.path,
          sourceSha = file.sha,
          syncedAt = nowMs,
        )
      downloaded++
      val prev = resolvedById[entity.id]
      if (prev == null || entity.version >= prev.version) {
        resolvedById[entity.id] = entity
      }
    }

    resolvedById.values.forEach { entity ->
      val cached = existingSynced.firstOrNull { it.id == entity.id }
      if (cached == null || cached.sourceSha != entity.sourceSha || cached.version != entity.version || cached.sourceFileName != entity.sourceFileName) {
        toUpsert.add(entity)
      }
    }

    resolvedQuestionsById.values.forEach { entity ->
      val cached = existingSyncedQuestions.firstOrNull { it.id == entity.id }
      if (cached == null || cached.sourceSha != entity.sourceSha || cached.version != entity.version || cached.sourceFileName != entity.sourceFileName) {
        questionsToUpsert.add(entity)
      }
    }

    resolvedLevelsById.values.forEach { entity ->
      val cached = existingSyncedLevels.firstOrNull { it.id == entity.id }
      if (cached == null || cached.sourceSha != entity.sourceSha || cached.version != entity.version || cached.sourceFileName != entity.sourceFileName) {
        levelsToUpsert.add(entity)
      }
    }

    val activeIds = if (!hadFetchFailure && resolvedById.isNotEmpty()) resolvedById.keys.toSet() else null
    val activeQIds =
      if (!hadFetchFailure && resolvedQuestionsById.isNotEmpty()) resolvedQuestionsById.keys.toSet() else null
    val activeLIds =
      if (!hadFetchFailure && resolvedLevelsById.isNotEmpty()) resolvedLevelsById.keys.toSet() else null
    val totalDownloaded = downloaded + downloadedQuestions + downloadedLevels
    val statusMsg =
      when {
        totalDownloaded > 0 && !hadFetchFailure -> {
          val parts = buildList {
            if (downloaded > 0) add("$downloaded page(s)")
            if (downloadedLevels > 0) add("$downloadedLevels level(s)")
            if (downloadedQuestions > 0) add("$downloadedQuestions question(s)")
          }
          "Downloaded ${parts.joinToString(", ")}"
        }
        totalDownloaded > 0 && hadFetchFailure ->
          "Downloaded $downloaded page(s), $downloadedLevels level(s), $downloadedQuestions question(s), partial error: ${firstFetchError ?: "some files failed"}"
        hadFetchFailure ->
          "Partially synced (${resolvedById.size} pages cached) — ${firstFetchError ?: "some files failed"}"
        resolvedById.isEmpty() && resolvedQuestionsById.isEmpty() && resolvedLevelsById.isEmpty() && existingSynced.isNotEmpty() ->
          "No valid .colorpop.json pages decoded — kept ${existingSynced.size} cached page(s)"
        resolvedQuestionsById.isNotEmpty() || resolvedLevelsById.isNotEmpty() -> {
          val extras = buildList {
            if (resolvedLevelsById.isNotEmpty()) add("${resolvedLevelsById.size} level(s)")
            if (resolvedQuestionsById.isNotEmpty()) add("${resolvedQuestionsById.size} question(s)")
          }
          "Up to date (${resolvedById.size} official page(s), ${extras.joinToString(", ")})"
        }
        else -> "Up to date (${resolvedById.size} official page(s))"
      }

    return CatalogSyncResult(
      upserts = toUpsert,
      activeRemoteIds = activeIds,
      totalRemotePages = resolvedById.size,
      downloadedCount = downloaded,
      statusMessage = statusMsg,
      errorMessage = if (hadFetchFailure && totalDownloaded == 0) firstFetchError else null,
      questionUpserts = questionsToUpsert,
      activeRemoteQuestionIds = activeQIds,
      totalRemoteQuestions = resolvedQuestionsById.size,
      downloadedQuestionCount = downloadedQuestions,
      levelUpserts = levelsToUpsert,
      activeRemoteLevelIds = activeLIds,
      totalRemoteLevels = resolvedLevelsById.size,
      downloadedLevelCount = downloadedLevels,
    )
  }

  private suspend fun syncDirectCatalog(
    url: String,
    existingSynced: List<SyncedTemplateEntity>,
    nowMs: Long,
    httpFetch: suspend (String) -> HttpResponse,
  ): CatalogSyncResult {
    val resp = httpFetch(url)
    if (resp.code !in 200..299) {
      val detail = extractHttpErrorDetail(resp, url)
      return CatalogSyncResult(
        upserts = emptyList(),
        activeRemoteIds = null,
        totalRemotePages = existingSynced.size,
        downloadedCount = 0,
        statusMessage =
          if (resp.code < 0) "Offline or network error: ${resp.body.ifBlank { "no connection" }}"
          else "Catalog fetch failed — $detail",
        errorMessage = if (resp.code < 0) "Network error" else detail,
      )
    }

    val trimmed = resp.body.trim()
    // Single .colorpop.json page URL?
    if (trimmed.startsWith("{")) {
      try {
        val rootObj = JSONObject(trimmed)
        if (rootObj.has("grid") && rootObj.has("palette")) {
          val tpl =
            PageExportEngine.decodePageFromJson(
              rawJson = trimmed,
              isSyncedOfficial = true,
              preserveEmbeddedId = true,
            )
              ?: return CatalogSyncResult(
                upserts = emptyList(),
                activeRemoteIds = null,
                totalRemotePages = existingSynced.size,
                downloadedCount = 0,
                statusMessage = "Invalid page JSON",
                errorMessage = "Invalid page JSON",
              )
          val sha = "${tpl.id}_v${tpl.version}_${trimmed.hashCode()}"
          val cached = existingSynced.firstOrNull { it.id == tpl.id }
          val entity = tpl.toSyncedEntity(sourceFileName = url, sourceSha = sha, syncedAt = nowMs)
          val upserts = if (cached?.sourceSha == sha) emptyList() else listOf(entity)
          return CatalogSyncResult(
            upserts = upserts,
            activeRemoteIds = setOf(entity.id),
            totalRemotePages = 1,
            downloadedCount = upserts.size,
            statusMessage =
              if (upserts.isNotEmpty()) "Synced \"${tpl.title}\"" else "Up to date (1 page)",
          )
        }
      } catch (_: Throwable) {
        return CatalogSyncResult(
          upserts = emptyList(),
          activeRemoteIds = null,
          totalRemotePages = existingSynced.size,
          downloadedCount = 0,
          statusMessage = "Couldn't parse catalog JSON",
          errorMessage = "Parse error",
        )
      }
    }

    // Parse catalog array or { "pages": [...] }
    val entriesArray =
      try {
        if (trimmed.startsWith("[")) {
          JSONArray(trimmed)
        } else {
          val obj = JSONObject(trimmed)
          obj.optJSONArray("pages") ?: obj.optJSONArray("templates") ?: JSONArray()
        }
      } catch (_: Throwable) {
        return CatalogSyncResult(
          upserts = emptyList(),
          activeRemoteIds = null,
          totalRemotePages = existingSynced.size,
          downloadedCount = 0,
          statusMessage = "Couldn't parse catalog JSON",
          errorMessage = "Invalid catalog JSON",
        )
      }

    val existingById = existingSynced.associateBy { it.id }
    val resolvedById = linkedMapOf<String, SyncedTemplateEntity>()
    val toUpsert = mutableListOf<SyncedTemplateEntity>()
    var downloaded = 0
    var hadFailure = false

    for (i in 0 until entriesArray.length()) {
      val item = entriesArray.optJSONObject(i) ?: continue
      val entryUrl = item.optString("url", "").trim()
      val entryId = item.optString("id", "").trim()
      val entryVersion = item.optInt("version", 1).coerceAtLeast(1)
      val entrySha = item.optString("sha", "v$entryVersion")

      if (entryUrl.isNotBlank()) {
        val cached = if (entryId.isNotBlank()) existingById[entryId] else null
        if (cached != null && cached.version >= entryVersion && cached.sourceSha == entrySha) {
          resolvedById[cached.id] = cached
          continue
        }
        val pageResp = httpFetch(entryUrl)
        if (pageResp.code !in 200..299) {
          hadFailure = true
          if (cached != null) resolvedById[cached.id] = cached
          continue
        }
        val decoded =
          PageExportEngine.decodePageFromJson(
            rawJson = pageResp.body,
            newId = entryId.ifBlank { null },
            isSyncedOfficial = true,
            preserveEmbeddedId = true,
          ) ?: continue
        val finalTpl =
          decoded.copy(version = maxOf(decoded.version, entryVersion))
        val entity =
          finalTpl.toSyncedEntity(
            sourceFileName = entryUrl,
            sourceSha = entrySha,
            syncedAt = nowMs,
          )
        resolvedById[entity.id] = entity
        toUpsert.add(entity)
        downloaded++
      } else if (item.has("grid") && item.has("palette")) {
        val decoded =
          PageExportEngine.decodePageFromJson(
            rawJson = item.toString(),
            isSyncedOfficial = true,
            preserveEmbeddedId = true,
          ) ?: continue
        val sha = "${decoded.id}_v${decoded.version}"
        val cached = existingById[decoded.id]
        val entity =
          decoded.toSyncedEntity(sourceFileName = url, sourceSha = sha, syncedAt = nowMs)
        resolvedById[entity.id] = entity
        if (cached == null || cached.sourceSha != sha) {
          toUpsert.add(entity)
          downloaded++
        }
      }
    }

    return CatalogSyncResult(
      upserts = toUpsert,
      activeRemoteIds = if (!hadFailure) resolvedById.keys.toSet() else null,
      totalRemotePages = resolvedById.size,
      downloadedCount = downloaded,
      statusMessage =
        if (downloaded > 0) "Downloaded $downloaded page(s)"
        else "Up to date (${resolvedById.size} official page(s))",
    )
  }

  private fun PictureTemplate.toSyncedEntity(
    sourceFileName: String,
    sourceSha: String,
    syncedAt: Long,
  ): SyncedTemplateEntity {
    val paletteStr = palette.entries.joinToString(",") { (k, v) -> "$k:${argbToHex(v)}" }
    val gridStr = grid.joinToString("/")
    val framesStr =
      if (frames.size > 1) {
        frames.joinToString("|") { frameRows -> frameRows.joinToString("/") }
      } else {
        ""
      }
    return SyncedTemplateEntity(
      id = id,
      title = title,
      gridWidth = gridWidth,
      gridHeight = gridHeight,
      paletteSerialized = paletteStr,
      gridSerialized = gridStr,
      framesSerialized = framesStr,
      frameMs = frameMs.coerceIn(80, 1200),
      version = version.coerceAtLeast(1),
      sourceFileName = sourceFileName,
      sourceSha = sourceSha,
      syncedAt = syncedAt,
    )
  }

  suspend fun publishPageToGitHub(
    sourceUrl: String,
    token: String,
    template: PictureTemplate,
    targetFolder: String = "pages",
    nowMs: Long = System.currentTimeMillis(),
    httpRequest: suspend (method: String, url: String, headers: Map<String, String>, body: String?) -> HttpResponse =
      ::defaultHttpRequest,
  ): PublishPageResult {
    val cleanToken = token.trim()
    if (cleanToken.isBlank()) {
      return PublishPageResult(
        success = false,
        message = "Add your GitHub token (PAT) in Parent Settings → Admin first",
      )
    }

    val source =
      parseSyncSource(sourceUrl) as? SyncSource.GitHubRepo
        ?: return PublishPageResult(
          success = false,
          message = "Sync URL must point to a GitHub repository",
        )

    val cleanId =
      PageExportEngine.sanitizePageId(template.id).ifBlank {
        PageExportEngine.sanitizePageId(template.title).ifBlank { "page_$nowMs" }
      }
    val folder =
      if (source.subPath.isNotBlank()) source.subPath.trim('/')
      else targetFolder.trim('/')
    val fileName = "$cleanId.colorpop.json"
    val remotePath = if (folder.isBlank()) fileName else "$folder/$fileName"
    val encodedRemotePath = encodeGitHubPath(remotePath)
    val refQuery = if (!source.branch.isNullOrBlank()) "?ref=${source.branch}" else ""
    val apiUrl = "https://api.github.com/repos/${source.owner}/${source.repo}/contents/$encodedRemotePath"
    val authHeaders = mapOf("Authorization" to "Bearer $cleanToken")

    // Check if the file already exists on GitHub so we can pass its SHA & auto-bump version
    val existingResp = httpRequest("GET", "$apiUrl$refQuery", authHeaders, null)
    var existingSha: String? = null
    var existingRemoteVersion = 0
    when (existingResp.code) {
      in 200..299 -> {
        try {
          val obj = JSONObject(existingResp.body)
          existingSha = obj.optString("sha", "").ifBlank { null }
          val encoded = obj.optString("content", "").replace("\n", "").replace("\r", "")
          if (encoded.isNotBlank()) {
            val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
            val remoteTpl =
              PageExportEngine.decodePageFromJson(
                rawJson = String(decodedBytes, Charsets.UTF_8),
                isSyncedOfficial = true,
                preserveEmbeddedId = true,
              )
            existingRemoteVersion = remoteTpl?.version ?: 0
          }
        } catch (_: Throwable) {
          // Keep existingSha if parsed before content decode failure
        }
      }
      404 -> {
        existingSha = null
      }
      401, 403 -> {
        return PublishPageResult(
          success = false,
          message = "GitHub rejected token — check Contents (Read & Write) permission",
        )
      }
      else -> {
        val msg =
          if (existingResp.code < 0) "Offline — connect to internet to publish"
          else "Couldn't check GitHub file (HTTP ${existingResp.code})"
        return PublishPageResult(success = false, message = msg)
      }
    }

    val nextVersion =
      if (existingSha != null) maxOf(template.version.coerceAtLeast(1), existingRemoteVersion + 1)
      else template.version.coerceAtLeast(1)

    val finalTpl =
      template.copy(
        id = cleanId,
        version = nextVersion,
        isCustom = false,
        isSyncedOfficial = true,
      )

    val jsonBytes = PageExportEngine.encodePageToJson(finalTpl).toByteArray(Charsets.UTF_8)
    val base64Content = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)

    val putPayload =
      JSONObject().apply {
        val actionWord = if (existingSha != null) "Update" else "Add"
        put("message", "$actionWord coloring page: ${finalTpl.title} ($cleanId v$nextVersion)")
        put("content", base64Content)
        if (existingSha != null) {
          put("sha", existingSha)
        }
        if (!source.branch.isNullOrBlank()) {
          put("branch", source.branch)
        }
      }

    val putResp = httpRequest("PUT", apiUrl, authHeaders, putPayload.toString())
    if (putResp.code !in 200..299) {
      val errMsg =
        when (putResp.code) {
          401, 403 -> "GitHub rejected token — make sure Contents is set to Read & Write"
          404 -> "Repository ${source.owner}/${source.repo} not found or token lacks access"
          409, 422 -> "GitHub conflict — please tap Publish again"
          in Int.MIN_VALUE..-1 -> "Network error while publishing to GitHub"
          else -> "Publish failed (HTTP ${putResp.code})"
        }
      return PublishPageResult(success = false, message = errMsg)
    }

    val newSha =
      try {
        JSONObject(putResp.body).optJSONObject("content")?.optString("sha", "") ?: ""
      } catch (_: Throwable) {
        ""
      }

    val syncedEntity =
      finalTpl.toSyncedEntity(
        sourceFileName = remotePath,
        sourceSha = newSha,
        syncedAt = nowMs,
      )

    return PublishPageResult(
      success = true,
      publishedTemplate = finalTpl,
      syncedEntity = syncedEntity,
      remotePath = remotePath,
      message = "Published \"${finalTpl.title}\" (v$nextVersion) to GitHub!",
    )
  }

  private fun CustomQuestion.toSyncedEntity(
    sourceFileName: String,
    sourceSha: String,
    syncedAt: Long,
  ): SyncedQuestionEntity {
    val clueJson = JSONArray().apply { clueItems.forEach { put(it) } }.toString()
    val wrongJson =
      JSONArray()
        .apply {
          wrongAnswers
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .forEach { put(it) }
        }
        .toString()
    val levelsCsv =
      if (levels.isEmpty() && customLevelIds.isEmpty()) {
        ChallengeMode.PRE_K.key
      } else {
        levels
          .filter { it != ChallengeMode.OFF }
          .joinToString(",") { it.key }
      }
    val customLevelIdsCsv =
      customLevelIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")

    return SyncedQuestionEntity(
      id = id,
      prompt = prompt.trim(),
      clueItemsJson = clueJson,
      correctAnswer = correctAnswer.trim(),
      wrongAnswersJson = wrongJson,
      levelsCsv = levelsCsv,
      customLevelIdsCsv = customLevelIdsCsv,
      isSentence = isSentence,
      version = version.coerceAtLeast(1),
      sourceFileName = sourceFileName,
      sourceSha = sourceSha,
      syncedAt = syncedAt,
    )
  }

  private fun CustomEducationLevel.toSyncedEntity(
    sourceFileName: String,
    sourceSha: String,
    syncedAt: Long,
  ): SyncedEducationLevelEntity {
    return SyncedEducationLevelEntity(
      id = id,
      name = name.trim(),
      description = description.trim(),
      topicsCsv = topics.joinToString(",") { it.key },
      version = version.coerceAtLeast(1),
      sourceFileName = sourceFileName,
      sourceSha = sourceSha,
      syncedAt = syncedAt,
    )
  }

  suspend fun publishQuestionToGitHub(
    sourceUrl: String,
    token: String,
    question: CustomQuestion,
    targetFolder: String = "pages",
    nowMs: Long = System.currentTimeMillis(),
    httpRequest: suspend (method: String, url: String, headers: Map<String, String>, body: String?) -> HttpResponse =
      ::defaultHttpRequest,
  ): PublishQuestionResult {
    val cleanToken = token.trim()
    if (cleanToken.isBlank()) {
      return PublishQuestionResult(
        success = false,
        message = "Add your GitHub token (PAT) in Parent Settings → Admin first",
      )
    }

    val source =
      parseSyncSource(sourceUrl) as? SyncSource.GitHubRepo
        ?: return PublishQuestionResult(
          success = false,
          message = "Sync URL must point to a GitHub repository",
        )

    val cleanId =
      QuestionExportEngine.sanitizeQuestionId(question.id).ifBlank {
        QuestionExportEngine.sanitizeQuestionId(question.prompt).ifBlank { "q_$nowMs" }
      }
    val baseFolder =
      if (source.subPath.isNotBlank()) source.subPath.trim('/')
      else targetFolder.trim('/')
    val questionFolder =
      when {
        baseFolder.isBlank() -> "questions"
        baseFolder.equals("questions", ignoreCase = true) ||
          baseFolder.endsWith("/questions", ignoreCase = true) -> baseFolder
        else -> "$baseFolder/questions"
      }
    val fileName = "$cleanId.question.json"
    val remotePath = "$questionFolder/$fileName"
    val encodedRemotePath = encodeGitHubPath(remotePath)
    val refQuery = if (!source.branch.isNullOrBlank()) "?ref=${source.branch}" else ""
    val apiUrl = "https://api.github.com/repos/${source.owner}/${source.repo}/contents/$encodedRemotePath"
    val authHeaders = mapOf("Authorization" to "Bearer $cleanToken")

    val existingResp = httpRequest("GET", "$apiUrl$refQuery", authHeaders, null)
    var existingSha: String? = null
    var existingRemoteVersion = 0
    when (existingResp.code) {
      in 200..299 -> {
        try {
          val obj = JSONObject(existingResp.body)
          existingSha = obj.optString("sha", "").ifBlank { null }
          val encoded = obj.optString("content", "").replace("\n", "").replace("\r", "")
          if (encoded.isNotBlank()) {
            val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
            val remoteQ =
              QuestionExportEngine.decodeQuestionFromJson(
                rawJson = String(decodedBytes, Charsets.UTF_8),
                isSyncedOfficial = true,
                preserveEmbeddedId = true,
              )
            existingRemoteVersion = remoteQ?.version ?: 0
          }
        } catch (_: Throwable) {
          // Keep existingSha
        }
      }
      404 -> existingSha = null
      401, 403 -> {
        return PublishQuestionResult(
          success = false,
          message = "GitHub rejected token — check Contents (Read & Write) permission",
        )
      }
      else -> {
        val msg =
          if (existingResp.code < 0) "Offline — connect to internet to publish"
          else "Couldn't check GitHub question file (HTTP ${existingResp.code})"
        return PublishQuestionResult(success = false, message = msg)
      }
    }

    val nextVersion =
      if (existingSha != null) maxOf(question.version.coerceAtLeast(1), existingRemoteVersion + 1)
      else question.version.coerceAtLeast(1)

    val finalQuestion =
      question.copy(
        id = cleanId,
        version = nextVersion,
        isCustom = false,
        isSyncedOfficial = true,
      )

    val jsonBytes = QuestionExportEngine.encodeQuestionToJson(finalQuestion).toByteArray(Charsets.UTF_8)
    val base64Content = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)

    val putPayload =
      JSONObject().apply {
        val actionWord = if (existingSha != null) "Update" else "Add"
        put("message", "$actionWord learning question: $cleanId v$nextVersion")
        put("content", base64Content)
        if (existingSha != null) {
          put("sha", existingSha)
        }
        if (!source.branch.isNullOrBlank()) {
          put("branch", source.branch)
        }
      }

    val putResp = httpRequest("PUT", apiUrl, authHeaders, putPayload.toString())
    if (putResp.code !in 200..299) {
      val errMsg =
        when (putResp.code) {
          401, 403 -> "GitHub rejected token — make sure Contents is set to Read & Write"
          404 -> "Repository ${source.owner}/${source.repo} not found or token lacks access"
          409, 422 -> "GitHub conflict — please tap Publish again"
          in Int.MIN_VALUE..-1 -> "Network error while publishing question to GitHub"
          else -> "Question publish failed (HTTP ${putResp.code})"
        }
      return PublishQuestionResult(success = false, message = errMsg)
    }

    val newSha =
      try {
        JSONObject(putResp.body).optJSONObject("content")?.optString("sha", "") ?: ""
      } catch (_: Throwable) {
        ""
      }

    val syncedEntity =
      finalQuestion.toSyncedEntity(
        sourceFileName = remotePath,
        sourceSha = newSha,
        syncedAt = nowMs,
      )

    return PublishQuestionResult(
      success = true,
      publishedQuestion = finalQuestion,
      syncedEntity = syncedEntity,
      remotePath = remotePath,
      message = "Published question \"$cleanId\" (v$nextVersion) to GitHub!",
    )
  }

  suspend fun publishLevelToGitHub(
    sourceUrl: String,
    token: String,
    level: CustomEducationLevel,
    targetFolder: String = "pages",
    nowMs: Long = System.currentTimeMillis(),
    httpRequest: suspend (method: String, url: String, headers: Map<String, String>, body: String?) -> HttpResponse =
      ::defaultHttpRequest,
  ): PublishLevelResult {
    val cleanToken = token.trim()
    if (cleanToken.isBlank()) {
      return PublishLevelResult(
        success = false,
        message = "Add your GitHub token (PAT) in Parent Settings → Admin first",
      )
    }

    val source =
      parseSyncSource(sourceUrl) as? SyncSource.GitHubRepo
        ?: return PublishLevelResult(
          success = false,
          message = "Sync URL must point to a GitHub repository",
        )

    val cleanId =
      LevelExportEngine.sanitizeLevelId(level.id).ifBlank {
        LevelExportEngine.sanitizeLevelId(level.name).ifBlank { "lvl_$nowMs" }
      }
    val baseFolder =
      if (source.subPath.isNotBlank()) source.subPath.trim('/')
      else targetFolder.trim('/')
    val levelFolder =
      when {
        baseFolder.isBlank() -> "levels"
        baseFolder.equals("levels", ignoreCase = true) ||
          baseFolder.endsWith("/levels", ignoreCase = true) -> baseFolder
        else -> "$baseFolder/levels"
      }
    val fileName = "$cleanId.level.json"
    val remotePath = "$levelFolder/$fileName"
    val encodedRemotePath = encodeGitHubPath(remotePath)
    val refQuery = if (!source.branch.isNullOrBlank()) "?ref=${source.branch}" else ""
    val apiUrl = "https://api.github.com/repos/${source.owner}/${source.repo}/contents/$encodedRemotePath"
    val authHeaders = mapOf("Authorization" to "Bearer $cleanToken")

    val existingResp = httpRequest("GET", "$apiUrl$refQuery", authHeaders, null)
    var existingSha: String? = null
    var existingRemoteVersion = 0
    when (existingResp.code) {
      in 200..299 -> {
        try {
          val obj = JSONObject(existingResp.body)
          existingSha = obj.optString("sha", "").ifBlank { null }
          val encoded = obj.optString("content", "").replace("\n", "").replace("\r", "")
          if (encoded.isNotBlank()) {
            val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
            val remoteL =
              LevelExportEngine.decodeLevelFromJson(
                rawJson = String(decodedBytes, Charsets.UTF_8),
                isSyncedOfficial = true,
                preserveEmbeddedId = true,
              )
            existingRemoteVersion = remoteL?.version ?: 0
          }
        } catch (_: Throwable) {
          // Keep existingSha
        }
      }
      404 -> existingSha = null
      401, 403 -> {
        return PublishLevelResult(
          success = false,
          message = "GitHub rejected token — check Contents (Read & Write) permission",
        )
      }
      else -> {
        val msg =
          if (existingResp.code < 0) "Offline — connect to internet to publish"
          else "Couldn't check GitHub level file (HTTP ${existingResp.code})"
        return PublishLevelResult(success = false, message = msg)
      }
    }

    val nextVersion =
      if (existingSha != null) maxOf(level.version.coerceAtLeast(1), existingRemoteVersion + 1)
      else level.version.coerceAtLeast(1)

    val finalLevel =
      level.copy(
        id = cleanId,
        version = nextVersion,
        isCustom = false,
        isSyncedOfficial = true,
      )

    val jsonBytes = LevelExportEngine.encodeLevelToJson(finalLevel).toByteArray(Charsets.UTF_8)
    val base64Content = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)

    val putPayload =
      JSONObject().apply {
        val actionWord = if (existingSha != null) "Update" else "Add"
        put("message", "$actionWord education level: ${finalLevel.name} ($cleanId v$nextVersion)")
        put("content", base64Content)
        if (existingSha != null) {
          put("sha", existingSha)
        }
        if (!source.branch.isNullOrBlank()) {
          put("branch", source.branch)
        }
      }

    val putResp = httpRequest("PUT", apiUrl, authHeaders, putPayload.toString())
    if (putResp.code !in 200..299) {
      val errMsg =
        when (putResp.code) {
          401, 403 -> "GitHub rejected token — make sure Contents is set to Read & Write"
          404 -> "Repository ${source.owner}/${source.repo} not found or token lacks access"
          409, 422 -> "GitHub conflict — please tap Publish again"
          in Int.MIN_VALUE..-1 -> "Network error while publishing level to GitHub"
          else -> "Level publish failed (HTTP ${putResp.code})"
        }
      return PublishLevelResult(success = false, message = errMsg)
    }

    val newSha =
      try {
        JSONObject(putResp.body).optJSONObject("content")?.optString("sha", "") ?: ""
      } catch (_: Throwable) {
        ""
      }

    val syncedEntity =
      finalLevel.toSyncedEntity(
        sourceFileName = remotePath,
        sourceSha = newSha,
        syncedAt = nowMs,
      )

    return PublishLevelResult(
      success = true,
      publishedLevel = finalLevel,
      syncedEntity = syncedEntity,
      remotePath = remotePath,
      message = "Published level \"${finalLevel.name}\" (v$nextVersion) to GitHub!",
    )
  }
}
