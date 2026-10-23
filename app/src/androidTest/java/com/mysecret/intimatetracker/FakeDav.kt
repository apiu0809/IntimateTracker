package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.sync.RemoteEntry
import com.mysecret.intimatetracker.data.sync.WebDavClient
import java.io.File

/** 内存版 WebDAV，供同步测试使用。 */
class FakeDav : WebDavClient {

    val dirs = mutableSetOf<String>()
    val files = mutableMapOf<String, ByteArray>()
    val etags = mutableMapOf<String, String>()
    private var counter = 0

    override suspend fun propfind(path: String): List<RemoteEntry> {
        val result = mutableListOf<RemoteEntry>()
        if (path in dirs) result += RemoteEntry(path, null, 0, true)
        files.filterKeys { it.startsWith(path) }.forEach { (key, bytes) ->
            result += RemoteEntry(key.substringAfterLast('/'), etags[key], bytes.size.toLong(), false)
        }
        return result
    }

    override suspend fun mkcol(path: String) {
        dirs += path
    }

    override suspend fun put(path: String, file: File, ifMatch: String?): String? {
        files[path] = file.readBytes()
        counter += 1
        val etag = "\"etag-$counter\""
        etags[path] = etag
        return etag
    }

    override suspend fun get(path: String, target: File): String? {
        val bytes = files[path] ?: throw IllegalStateException("missing $path")
        target.writeBytes(bytes)
        return etags[path]
    }

    override suspend fun move(from: String, to: String) {
        files[to] = files.remove(from) ?: throw IllegalStateException("missing $from")
        etags[to] = etags[from].orEmpty()
    }

    override suspend fun delete(path: String) {
        files.remove(path)
        etags.remove(path)
    }
}
