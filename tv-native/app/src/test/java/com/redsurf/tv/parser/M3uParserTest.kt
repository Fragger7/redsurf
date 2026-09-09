package com.redsurf.tv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uParserTest {

    @Test
    fun parseM3u_validPlaylist_returnsChannels() = runBlocking {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn" tvg-logo="http://logo.com/cnn.png" group-title="News",CNN
            http://stream.com/cnn/live.ts
            #EXTINF:-1 group-title="Sports",ESPN
            http://stream.com/espn/live.ts
        """.trimIndent()

        val inputStream = ByteArrayInputStream(m3uData.toByteArray())
        val channels = M3uParser.parse(inputStream)

        assertEquals(2, channels.size)
        
        val cnn = channels[0]
        assertEquals("CNN", cnn.name)
        assertEquals("http://logo.com/cnn.png", cnn.logoUrl)
        assertEquals("News", cnn.group)
        assertEquals("cnn", cnn.epgId)
        assertEquals("http://stream.com/cnn/live.ts", cnn.streamUrl)

        val espn = channels[1]
        assertEquals("ESPN", espn.name)
        assertEquals("Sports", espn.group)
        assertEquals("http://stream.com/espn/live.ts", espn.streamUrl)
    }

    @Test
    fun parseM3u_emptyPlaylist_returnsEmptyList() = runBlocking {
        val m3uData = "#EXTM3U\n"
        val inputStream = ByteArrayInputStream(m3uData.toByteArray())
        val channels = M3uParser.parse(inputStream)
        assertTrue(channels.isEmpty())
    }
}
