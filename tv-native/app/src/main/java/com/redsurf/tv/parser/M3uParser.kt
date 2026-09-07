package com.redsurf.tv.parser

import com.redsurf.tv.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

object M3uParser {
    suspend fun parse(inputStream: InputStream): List<Channel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentEpgId = ""

        inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract tvg-logo
                    val logoRegex = "tvg-logo=\"([^\"]+)\"".toRegex()
                    currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // Extract group-title
                    val groupRegex = "group-title=\"([^\"]+)\"".toRegex()
                    currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1) ?: "Uncategorized"

                    // Extract tvg-id (EPG)
                    val idRegex = "tvg-id=\"([^\"]+)\"".toRegex()
                    currentEpgId = idRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // Extract name (after the last comma)
                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (!trimmed.startsWith("#")) {
                    // It's a URL
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = currentName.ifEmpty { "Unknown Channel" },
                            streamUrl = trimmed,
                            logoUrl = currentLogo,
                            group = currentGroup,
                            epgId = currentEpgId
                        )
                    )
                    // Reset
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
                    currentEpgId = ""
                }
            }
        }
        channels
    }
}
