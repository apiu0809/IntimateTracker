package com.mysecret.intimatetracker.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

data class RemoteEntry(val path: String, val etag: String?, val size: Long, val isDirectory: Boolean)

/** WebDAV 最小能力集（可用假实现做测试）。 */
interface WebDavClient {
    suspend fun propfind(path: String): List<RemoteEntry>
    suspend fun mkcol(path: String)
    suspend fun put(path: String, file: File, ifMatch: String? = null): String?
    suspend fun get(path: String, target: File): String?
    suspend fun move(from: String, to: String)
    suspend fun delete(path: String)
}

class SyncException(val code: String, message: String) : Exception(message)

/** OkHttp 实现：只用到 PROPFIND / MKCOL / PUT / GET / MOVE / DELETE。 */
class OkHttpWebDavClient(
    baseUrl: String,
    private val user: String,
    private val password: String,
) : WebDavClient {

    private val base = normalizeDavUrl(baseUrl).trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = "$base/${path.trimStart('/')}"

    private fun requestBuilder(path: String, method: String): Request.Builder =
        Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .method(method, null)

    override suspend fun propfind(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .header("Depth", "1")
            .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) throw failure(response)
            val body = response.body?.string().orEmpty()
            parseProps(body)
        }
    }

    override suspend fun mkcol(path: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .method("MKCOL", "".toRequestBody("application/xml".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) throw failure(response)
        }
    }

    override suspend fun put(path: String, file: File, ifMatch: String?): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .put(file.asRequestBody("application/zip".toMediaType()))
        if (ifMatch != null) builder.header("If-Match", ifMatch)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw failure(response)
            return@withContext response.header("ETag")
        }
    }

    override suspend fun get(path: String, target: File): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw SyncException("E_NOT_FOUND", "远端备份不存在")
            if (!response.isSuccessful) throw failure(response)
            target.parentFile?.mkdirs()
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return@withContext response.header("ETag")
        }
    }

    override suspend fun move(from: String, to: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(from))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .header("Destination", url(to))
            .header("Overwrite", "T")
            .method("MOVE", null)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw failure(response)
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", okhttp3.Credentials.basic(user, password))
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) throw failure(response)
        }
    }

    private fun failure(response: Response): SyncException = when (response.code) {
        401, 403 -> SyncException("E_AUTH", "账号或密码错误")
        507 -> SyncException("E_SPACE", "存储空间不足")
        else -> SyncException("E_NET", "网络请求失败（${response.code}）")
    }

    private fun parseProps(xml: String): List<RemoteEntry> {
        val entries = mutableListOf<RemoteEntry>()
        val responseBlocks = Regex("<[^>]*response[^>]*>(.*?)</[^>]*response>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
        responseBlocks.forEach { match ->
            val block = match.groupValues[1]
            val href = Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>").find(block)?.groupValues?.get(1).orEmpty()
            val etag = Regex("<[^>]*getetag[^>]*>(.*?)</[^>]*getetag>").find(block)?.groupValues?.get(1)
                ?.trim('"', ' ')
            val isDir = block.contains("collection")
            val name = href.trimEnd('/').substringAfterLast('/')
            val size = Regex("<[^>]*getcontentlength[^>]*>(\\d+)<").find(block)?.groupValues?.get(1)
                ?.toLongOrNull() ?: 0L
            entries += RemoteEntry(
                path = name,
                etag = etag,
                size = size,
                isDirectory = isDir,
            )
        }
        return entries
    }
}

/** 规整用户输入的 WebDAV 地址：全角转半角、补协议、去尾部斜杠。 */
fun normalizeDavUrl(raw: String): String {
    var value = raw.trim()
        .replace('：', ':')
        .replace('／', '/')
        .replace('．', '.')
        .replace('　', ' ')
        .replace(" ", "")
    if (value.isEmpty()) return value
    if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) {
        value = "https://$value"
    }
    return value.trimEnd('/')
}