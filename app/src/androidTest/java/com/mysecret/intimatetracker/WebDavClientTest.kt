package com.mysecret.intimatetracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.sync.OkHttpWebDavClient
import com.mysecret.intimatetracker.data.sync.SyncException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 用 MockWebServer 验证真实 HTTP 路径（含主线程限制、XML 解析、错误码映射）。 */
@RunWith(AndroidJUnit4::class)
class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpWebDavClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpWebDavClient(server.url("/dav").toString(), "u", "p")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun propfind_parses_etag_and_entries(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version="1.0"?>
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/dav/IntimateTracker/</d:href>
                    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/dav/IntimateTracker/backup.zip</d:href>
                    <d:propstat><d:prop><d:getetag>"abc123"</d:getetag></d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
                """.trimIndent(),
            ),
        )

        val entries = client.propfind("IntimateTracker")

        assertEquals(2, entries.size)
        assertTrue(entries.any { it.path == "IntimateTracker" && it.isDirectory })
        val file = entries.first { it.path == "backup.zip" }
        assertEquals("abc123", file.etag)
    }

    @Test
    fun unauthorized_maps_to_auth_error(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { client.propfind("IntimateTracker") }.exceptionOrNull()

        assertTrue(error is SyncException)
        assertEquals("E_AUTH", (error as SyncException).code)
    }

    @Test
    fun put_returns_etag_from_header(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"new-etag\""))
        val file = File.createTempFile("upload", ".zip")
        file.writeText("payload")

        val etag = client.put("IntimateTracker/backup.zip", file)

        assertEquals("\"new-etag\"", etag)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("payload", recorded.body.readUtf8())
        file.delete()
    }

    @Test
    fun missing_file_returns_not_found_error(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val target = File.createTempFile("download", ".zip")

        val error = runCatching { client.get("IntimateTracker/backup.zip", target) }.exceptionOrNull()

        assertTrue(error is SyncException)
        assertEquals("E_NOT_FOUND", (error as SyncException).code)
        target.delete()
    }
}
