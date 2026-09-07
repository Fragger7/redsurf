package com.redsurf.tv.ui.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.redsurf.tv.data.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Mock Program data for the UI
data class EpgProgram(val title: String, val startUnix: Long, val endUnix: Long)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgGridView(channels: List<Channel>, onChannelSelected: (Channel) -> Unit) {
    val pxPerMinute = 8.dp // Defines how wide 1 minute of EPG time is
    
    // We mock current time to the start of an hour for a clean grid
    val currentTime = System.currentTimeMillis()
    
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        // Timeline Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF1E1E1E))
        ) {
            Box(modifier = Modifier.width(200.dp).fillMaxHeight()) {
                Text("Channels", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
            // Mock Timeline
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            TvLazyRow(modifier = Modifier.fillMaxHeight()) {
                items(5) { hourOffset ->
                    val t = currentTime + (hourOffset * 3600000)
                    Box(modifier = Modifier.width(pxPerMinute * 60).fillMaxHeight().padding(start = 16.dp, top = 16.dp)) {
                        Text(sdf.format(Date(t)), color = Color.White)
                    }
                }
            }
        }

        // Channels and Programs
        TvLazyColumn(modifier = Modifier.fillMaxSize()) {
            items(channels) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    // Locked Channel Column
                    Surface(
                        onClick = { onChannelSelected(channel) },
                        modifier = Modifier.width(200.dp).fillMaxHeight(),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF1A1A1A),
                            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Horizontal scrolling programs (Simulated for UI demonstration)
                    val mockPrograms = listOf(
                        EpgProgram("Morning News", currentTime, currentTime + 1800000), // 30 min
                        EpgProgram("Blockbuster Movie", currentTime + 1800000, currentTime + 9000000), // 2 hours
                        EpgProgram("Late Show", currentTime + 9000000, currentTime + 12600000) // 1 hour
                    )

                    TvLazyRow(modifier = Modifier.fillMaxHeight()) {
                        items(mockPrograms) { program ->
                            val durationMins = ((program.endUnix - program.startUnix) / 60000).toInt()
                            val width = pxPerMinute * durationMins
                            
                            Surface(
                                onClick = { onChannelSelected(channel) },
                                modifier = Modifier
                                    .width(width)
                                    .fillMaxHeight()
                                    .padding(2.dp),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF252525),
                                    focusedContainerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = program.title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
                                    Text(
                                        text = "\${timeFmt.format(Date(program.startUnix))} - \${timeFmt.format(Date(program.endUnix))}",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
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
