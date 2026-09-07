#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"

# 1. Add EPG Entities to Room DB
cat << 'KOTLIN' > "$BASE_DIR/db/EpgEntities.kt"
package com.redsurf.tv.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "epg_programs",
    indices = [Index("channelEpgId"), Index("startTime")],
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["epgId"],
            childColumns = ["channelEpgId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long
)
KOTLIN

# 2. Native XMLTV Parser
cat << 'KOTLIN' > "$BASE_DIR/parser/XmlTvParser.kt"
package com.redsurf.tv.parser

import android.util.Xml
import com.redsurf.tv.db.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmlTvParser {
    // XMLTV dates look like: 20231015080000 +0000
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun parse(inputStream: InputStream): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        val programs = mutableListOf<EpgProgramEntity>()
        
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentChannelId = ""
            var currentTitle = ""
            var currentDesc = ""
            var currentStart = 0L
            var currentEnd = 0L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                val startStr = parser.getAttributeValue(null, "start")
                                val endStr = parser.getAttributeValue(null, "stop")
                                
                                currentStart = parseDate(startStr)
                                currentEnd = parseDate(endStr)
                            }
                            "title" -> currentTitle = parser.nextText()
                            "desc" -> currentDesc = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "programme" && currentChannelId.isNotEmpty()) {
                            programs.add(
                                EpgProgramEntity(
                                    id = "\$currentChannelId-\$currentStart",
                                    channelEpgId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentEnd
                                )
                            )
                            currentTitle = ""
                            currentDesc = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        programs
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
KOTLIN

# 3. EPG Timeline UI (TiViMate Guide overlay)
cat << 'KOTLIN' > "$BASE_DIR/ui/EpgTimelineLayout.kt"
package com.redsurf.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.redsurf.tv.data.Channel
import com.redsurf.tv.db.EpgProgramEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgTimelineLayout(
    channels: List<Channel>,
    epgData: Map<String, List<EpgProgramEntity>>, // channelId -> programs
    onChannelSelect: (Channel) -> Unit
) {
    // 70% opacity background for the classic EPG view
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B).copy(alpha = 0.85f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Time Axis (Top header)
            Row(modifier = Modifier.fillMaxWidth().height(50.dp).background(Color(0xFF18181B))) {
                Spacer(modifier = Modifier.width(200.dp)) // Offset for channel list
                // Mock time slots
                val times = listOf("12:00 PM", "12:30 PM", "1:00 PM", "1:30 PM", "2:00 PM")
                times.forEach { time ->
                    Box(modifier = Modifier.width(200.dp).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(time, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Grid: Channels (Left) + Programs (Right)
            TvLazyColumn(modifier = Modifier.fillMaxSize()) {
                items(channels) { channel ->
                    Row(modifier = Modifier.fillMaxWidth().height(70.dp)) {
                        
                        // Channel Info Cell (Sticky left)
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF1E1E1E))
                                .padding(8.dp),
                            contentAlignment = androidx.compose.ui.Alignment.CenterStart
                        ) {
                            Text(channel.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Programs Timeline
                        val programs = epgData[channel.epgId] ?: emptyList()
                        
                        if (programs.isEmpty()) {
                            // No EPG fallback cell
                            EpgCell(title = "No Information", isFocused = false, onClick = { onChannelSelect(channel) })
                        } else {
                            LazyRow {
                                items(programs) { program ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    
                                    // Simplified width calculation based on duration (mock scale: 1 hour = 400dp)
                                    val durationMs = program.endTime - program.startTime
                                    val widthDp = ((durationMs / (1000f * 60f)) * (400f / 60f)).toInt().dp

                                    Surface(
                                        onClick = { onChannelSelect(channel) },
                                        modifier = Modifier
                                            .width(widthDp.coerceAtLeast(100.dp))
                                            .fillMaxHeight()
                                            .padding(2.dp)
                                            .onFocusChanged { isFocused = it.isFocused },
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFF27272A),
                                            focusedContainerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
                                            Text(
                                                program.title,
                                                color = if (isFocused) Color.White else Color.LightGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
                                            Text(
                                                "\${timeFormat.format(Date(program.startTime))} - \${timeFormat.format(Date(program.endTime))}",
                                                color = if (isFocused) Color.White.copy(alpha=0.7f) else Color.Gray,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgCell(title: String, isFocused: Boolean, onClick: () -> Unit) {
    var localFocused by remember { mutableStateOf(isFocused) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .onFocusChanged { localFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF27272A),
            focusedContainerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
            Text(title, color = if (localFocused) Color.White else Color.Gray)
        }
    }
}
KOTLIN

chmod +x tv_feature_burst_3.sh
./tv_feature_burst_3.sh
