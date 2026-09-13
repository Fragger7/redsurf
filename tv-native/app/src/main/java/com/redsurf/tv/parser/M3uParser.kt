package com.redsurf.tv.parser

import com.redsurf.tv.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

/**
 * Streams and batches rather than materializing the whole playlist (see docs/plans/PHASE_1.md
 * #2b - the "OOM JSON trap"). The user's real provider's M3U is 327 MB / 1.23M entries; loading
 * that into one List would OOM the target device, and the Shield too.
 */
object M3uParser {

    /**
     * Parses [inputStream] and invokes [onBatch] with up to [batchSize] channels at a time,
     * never holding more than one batch in memory. Every channel is classified by URL shape:
     * "/movie/" -> vod, "/series/" -> series, otherwise live (this "else" branch is what makes
     * Xtream's bare /user/pass/id live-channel URLs - no /live/ segment - classify correctly;
     * measured on the real provider, see PHASE_1.md #2b).
     */
    suspend fun parse(
        inputStream: InputStream,
        batchSize: Int = 500,
        onBatch: suspend (List<Channel>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val batch = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentEpgId = ""
        var currentChno: Int? = null

        inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    val logoRegex = "tvg-logo=\"([^\"]+)\"".toRegex()
                    currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // .trim() - found live, 2026-09-12: an untrimmed group-title with incidental
                    // whitespace fractures what the user sees as one group into several distinct
                    // ones here (exact-string equality everywhere this is grouped/queried by,
                    // RedSurfDatabase.kt), which then also breaks zap's next/prev-in-group query.
                    val groupRegex = "group-title=\"([^\"]+)\"".toRegex()
                    currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1)?.trim() ?: "Uncategorized"

                    val idRegex = "tvg-id=\"([^\"]+)\"".toRegex()
                    currentEpgId = idRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // The provider's real channel number - found live, 2026-09-12, never parsed
                    // before (see Channel.chno's doc). Absent on many providers, hence nullable;
                    // MainViewModel falls back to a per-group counter when this is null.
                    val chnoRegex = "tvg-chno=\"([^\"]+)\"".toRegex()
                    currentChno = chnoRegex.find(trimmed)?.groupValues?.get(1)?.trim()?.toIntOrNull()

                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (!trimmed.startsWith("#")) {
                    val streamType = when {
                        trimmed.contains("/movie/") -> "vod"
                        trimmed.contains("/series/") -> "series"
                        else -> "live"
                    }
                    batch.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = currentName.ifEmpty { "Unknown Channel" },
                            streamUrl = trimmed,
                            logoUrl = currentLogo,
                            group = currentGroup,
                            epgId = currentEpgId,
                            streamType = streamType,
                            chno = currentChno,
                        )
                    )
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
                    currentEpgId = ""
                    currentChno = null

                    if (batch.size >= batchSize) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
    }
}
