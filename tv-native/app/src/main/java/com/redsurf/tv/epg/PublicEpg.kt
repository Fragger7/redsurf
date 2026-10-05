package com.redsurf.tv.epg

import android.content.Context
import android.util.Log
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.parser.XmlTvParser
import com.redsurf.tv.sync.EpgSyncState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.util.zip.GZIPInputStream

/**
 * Public EPG supplement, Phase A (EPG_WRAPUP.md 2.1; SEQUENCING.md Sprint 4). When a provider has
 * no listings for a channel, fill them from EPGSHARE01 (free, hosted XMLTV, "for LEGAL use only").
 *
 * - **Storage:** public programmes are stored once, under the pseudo-playlist [PLAYLIST_ID], shared
 *   by every real playlist. A channel only carries `epgFallbackId` - the public channel id it
 *   matched - and the grid uses it only when the provider has nothing for that channel.
 * - **Matching:** EPGSHARE01 ids look like `CBS.Sports.Network.HD.us2`, providers' like `CBS.us`, so
 *   raw id equality almost never hits. Both sides are normalised ([normalize]: country tags,
 *   HD/FHD/4K/East/West, punctuation dropped) and only an *exact* normalised match counts - a wrong
 *   guide on a channel is worse than an empty one.
 * - **Which files:** chosen from the playlist's own category prefixes ("US|…" → US files). The
 *   56MB US locals file is deliberately skipped in Phase A.
 */
object PublicEpg {
    const val PLAYLIST_ID = "public:epgshare01"
    const val SOURCE_NAME = "EPGSHARE01"
    private const val TAG = "PublicEpg"
    private const val BASE = "https://epgshare01.online/epgshare01/"
    private const val REFRESH_MS = 20 * 60 * 60 * 1000L

    /** Country prefix (as seen in category names) → EPGSHARE01 files. */
    private val FILES = mapOf(
        "US" to listOf("epg_ripper_US2.xml.gz", "epg_ripper_US_SPORTS1.xml.gz"),
        "USA" to listOf("epg_ripper_US2.xml.gz", "epg_ripper_US_SPORTS1.xml.gz"),
        "UK" to listOf("epg_ripper_UK1.xml.gz"),
    )

    private val DROP_TOKENS = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "raw", "60fps", "east", "west",
        "feed", "us", "us2", "usa", "uk", "tv", "channel", "the", "backup", "vip",
    )

    /** Lower-case alphanumerics only, country/quality/feed tokens removed, words joined by one
     * space. "US| CBS Sports Network HD" and "CBS.Sports.Network.HD.us2" both → "cbs sports network". */
    fun normalize(raw: String): String {
        val withoutPrefix = raw.replace(Regex("^\\s*[A-Za-z]{2,3}\\s*[|:•\\-]\\s*"), " ")
        return withoutPrefix.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() && it !in DROP_TOKENS }
            .joinToString(" ")
    }

    /** Files to fetch for a playlist, from its category names' leading country token. */
    fun filesFor(groupNames: Collection<String>): Set<String> =
        groupNames.mapNotNull { name ->
            // Skip decoration before the country token - real provider names like "✯USA✯ NFL"
            // (found live 2026-09-30: those US categories fetched no US file at all).
            val token = name.dropWhile { !it.isLetterOrDigit() }.takeWhile { it.isLetter() }.uppercase()
            FILES[token]
        }.flatten().toSet()

    // Found live 2026-10-05: all three playlists' syncs downloaded and parsed the same public
    // files at the same moment. One at a time - the second and third then see a fresh
    // EpgSyncState stamp and skip. Short (~30s per file), so well inside WorkManager's 10 minutes.
    private val fileLock = Mutex()

    /** Downloads and ingests any file not refreshed in the last [REFRESH_MS]. */
    suspend fun syncFiles(context: Context, db: RedSurfDatabase, files: Set<String>) = fileLock.withLock {
        val client = IptvNetworkModule.getOkHttpClient(null, readTimeoutSeconds = 120)
        for (file in files) {
            val key = "$PLAYLIST_ID/$file"
            if (System.currentTimeMillis() - EpgSyncState.lastCompleted(context, key) < REFRESH_MS) continue
            val started = System.currentTimeMillis()
            val response = client.newCall(Request.Builder().url(BASE + file).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    Log.w(TAG, "publicEpg -> http ${it.code} for $file")
                    return@use
                }
                val body = it.body ?: return@use
                val inserted = GZIPInputStream(body.byteStream()).use { gz ->
                    XmlTvParser.parseAndInsert(gz, db, PLAYLIST_ID, skipEndedBefore = System.currentTimeMillis() - 6 * 60 * 60 * 1000L)
                }
                EpgSyncState.markCompleted(context, key)
                Log.d(TAG, "publicEpg -> $file inserted=$inserted tookMs=${System.currentTimeMillis() - started}")
            }
        }
        db.epgDao().pruneEnded(PLAYLIST_ID, System.currentTimeMillis() - 6 * 60 * 60 * 1000L)
    }

    /**
     * Sets `epgFallbackId` on this playlist's channels that have no provider listings and whose
     * EPG id or name normalises to a public channel. Returns (candidates, matched) for the log -
     * the Phase A deliverable is that measured number.
     */
    fun matchPlaylist(db: RedSurfDatabase, playlistId: String): Pair<Int, Int> {
        val epgDao = db.epgDao()
        val channelDao = db.channelDao()
        val publicIndex = HashMap<String, String>()
        epgDao.distinctChannelIdsBlocking(PLAYLIST_ID).forEach { id ->
            val stem = id.substringBeforeLast('.') // drop the ".us2" source suffix
            publicIndex.putIfAbsent(normalize(stem.replace('.', ' ')), id)
        }
        val providerIdsWithData = epgDao.currentChannelIdsBlocking(playlistId, System.currentTimeMillis()).toHashSet()
        var candidates = 0
        var matched = 0
        db.runInTransaction { channelDao.clearFallbackIds(playlistId) }
        var offset = 0
        while (true) {
            val page = channelDao.matchPage(playlistId, 2000, offset)
            if (page.isEmpty()) break
            offset += page.size
            val updates = mutableListOf<Pair<String, String>>()
            for (c in page) {
                val providerId = c.epgChannelId?.takeIf { it.isNotBlank() }
                if (providerId != null && providerId in providerIdsWithData) continue
                candidates++
                val byId = providerId?.let { publicIndex[normalize(it.substringBeforeLast('.').replace('.', ' '))] }
                val hit = byId ?: publicIndex[normalize(c.name)]
                if (hit != null) updates += c.streamId to hit
            }
            if (updates.isNotEmpty()) {
                matched += updates.size
                db.runInTransaction { updates.forEach { (streamId, id) -> channelDao.setFallbackId(playlistId, streamId, id) } }
            }
        }
        Log.d(TAG, "publicEpg match -> playlist=$playlistId candidates=$candidates matched=$matched publicChannels=${publicIndex.size}")
        return candidates to matched
    }
}
