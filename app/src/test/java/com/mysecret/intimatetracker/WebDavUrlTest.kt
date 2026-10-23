package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.sync.normalizeDavUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavUrlTest {

    @Test
    fun `adds https scheme when missing`() {
        assertEquals("https://dav.jianguoyun.com/dav", normalizeDavUrl("dav.jianguoyun.com/dav"))
    }

    @Test
    fun `keeps explicit http`() {
        assertEquals("http://192.168.1.10:5005/dav", normalizeDavUrl("http://192.168.1.10:5005/dav"))
    }

    @Test
    fun `converts full width punctuation`() {
        // 中文输入法常见的全角冒号与斜杠
        assertEquals(
            "https://dav.jianguoyun.com/dav",
            normalizeDavUrl("https：／／dav.jianguoyun.com／dav"),
        )
    }

    @Test
    fun `trims spaces and trailing slash`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav",
            normalizeDavUrl("  https://dav.jianguoyun.com/dav/  "),
        )
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", normalizeDavUrl("   "))
    }
}
