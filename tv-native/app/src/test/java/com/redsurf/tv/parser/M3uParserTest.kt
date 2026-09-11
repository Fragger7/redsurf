package com.redsurf.tv.parser

import com.redsurf.tv.data.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uParserTest {

    /** Collects every batch into one list, for assertions that don't care about batching. */
    private suspend fun parseAll(m3uData: String, batchSize: Int = 500): List<Channel> {
        val all = mutableListOf<Channel>()
        M3uParser.parse(ByteArrayInputStream(m3uData.toByteArray()), batchSize) { batch ->
            all.addAll(batch)
        }
        return all
    }

    @Test
    fun parseM3u_validPlaylist_returnsChannels() = runBlocking {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn" tvg-logo="http://logo.com/cnn.png" group-title="News",CNN
            http://stream.com/cnn/live.ts
            #EXTINF:-1 group-title="Sports",ESPN
            http://stream.com/espn/live.ts
        """.trimIndent()

        val channels = parseAll(m3uData)

        assertEquals(2, channels.size)

        val cnn = channels[0]
        assertEquals("CNN", cnn.name)
        assertEquals("http://logo.com/cnn.png", cnn.logoUrl)
        assertEquals("News", cnn.group)
        assertEquals("cnn", cnn.epgId)
        assertEquals("http://stream.com/cnn/live.ts", cnn.streamUrl)
        assertEquals("live", cnn.streamType)

        val espn = channels[1]
        assertEquals("ESPN", espn.name)
        assertEquals("Sports", espn.group)
        assertEquals("http://stream.com/espn/live.ts", espn.streamUrl)
        assertEquals("live", espn.streamType)
    }

    @Test
    fun parseM3u_emptyPlaylist_returnsEmptyList() = runBlocking {
        assertTrue(parseAll("#EXTM3U\n").isEmpty())
    }

    @Test
    fun parseM3u_classifiesByUrlShape() = runBlocking {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1,Live No Marker
            http://host/USER/PASS/12345
            #EXTINF:-1,A Movie
            http://host/movie/USER/PASS/999.mp4
            #EXTINF:-1,A Series Episode
            http://host/series/USER/PASS/111.mp4
        """.trimIndent()

        val channels = parseAll(m3uData)

        assertEquals(3, channels.size)
        // Xtream's bare /user/pass/id live URLs have no "/live/" segment - this is the "else"
        // branch that must classify them as live, not the absence of a positive match.
        assertEquals("live", channels[0].streamType)
        assertEquals("vod", channels[1].streamType)
        assertEquals("series", channels[2].streamType)
    }

    @Test
    fun parseM3u_respectsBatchSize() = runBlocking {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(12) { i ->
            sb.append("#EXTINF:-1,Channel $i\n")
            sb.append("http://stream.com/$i\n")
        }

        val batchSizes = mutableListOf<Int>()
        M3uParser.parse(ByteArrayInputStream(sb.toString().toByteArray()), batchSize = 5) { batch ->
            batchSizes.add(batch.size)
        }

        // 12 channels at batch size 5: two full batches, one partial - proves it never
        // materializes the whole list and never drops the trailing partial batch.
        assertEquals(listOf(5, 5, 2), batchSizes)
    }
}
