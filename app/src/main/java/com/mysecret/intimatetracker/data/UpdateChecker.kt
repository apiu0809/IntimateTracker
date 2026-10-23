package com.mysecret.intimatetracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 远端最新版本信息。 */
data class UpdateInfo(
    val version: String,
    val pageUrl: String,
    val notes: String,
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * 从 GitHub Releases 读取最新版本，与当前版本比较。
 *
 * 无需登录，走 api.github.com 匿名额度（每小时 60 次），只在用户点「检查更新」时请求。
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "IntimateTracker-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching UpdateResult.Failed("HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val version = json.optString("tag_name").trim().removePrefix("v")
                val pageUrl = json.optString("html_url").trim().ifBlank { RELEASES_URL }
                val notes = json.optString("body").trim()
                when {
                    version.isBlank() -> UpdateResult.Failed("响应缺少版本号")
                    isNewerVersion(version, currentVersion) ->
                        UpdateResult.Available(UpdateInfo(version, pageUrl, notes))
                    else -> UpdateResult.UpToDate
                }
            }
        }.getOrElse { error ->
            UpdateResult.Failed(error.message ?: "网络异常")
        }
    }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/apiu0809/IntimateTracker/releases/latest"
        const val RELEASES_URL = "https://github.com/apiu0809/IntimateTracker/releases"
    }
}

/** 语义化版本比较：`1.5.10` > `1.5.9`，`v` 前缀与 `-rc1` 之类的后缀不影响主版本号比较。 */
internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val left = versionNumbers(candidate)
    val right = versionNumbers(current)
    for (index in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(index) { 0 }
        val r = right.getOrElse(index) { 0 }
        if (l != r) return l > r
    }
    return false
}

private fun versionNumbers(version: String): List<Int> =
    version.trim()
        .removePrefix("v")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.trim().toIntOrNull() ?: 0 }
