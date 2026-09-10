package com.redsurf.tv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.redsurf.tv.vod.XtreamCategory
import com.redsurf.tv.vod.VodMovie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodDashboardView() {
    // Mock Data simulating Netflix-style dashboard powered by Xtream API
    val categories = listOf(
        XtreamCategory("1", "Trending Now", "0"),
        XtreamCategory("2", "Action Thrillers", "0"),
        XtreamCategory("3", "Comedy Movies", "0")
    )
    
    val mockMovies = listOf(
        VodMovie("101", "The Dark Knight", "", "9.0", "mp4"),
        VodMovie("102", "Inception", "", "8.8", "mp4"),
        VodMovie("103", "Interstellar", "", "8.6", "mp4"),
        VodMovie("104", "Dune: Part Two", "", "8.9", "mp4"),
        VodMovie("105", "Avengers: Endgame", "", "8.4", "mp4")
    )

    TvLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))
    ) {
        // Featured Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color(0xFF222222))
            ) {
                // Background image would be here
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(48.dp)
                ) {
                    Text(
                        "Dune: Part Two",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Paul Atreides unites with Chani and the Fremen while on a warpath of revenge against the conspirators who destroyed his family.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.LightGray,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { /* Play */ }) {
                        Text("Watch Now")
                    }
                }
            }
        }

        // Categories
        items(categories) { category ->
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
                )
                
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(mockMovies) { movie ->
                        MovieCard(movie)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(movie: VodMovie) {
    Surface(
        onClick = { /* Open Movie Details */ },
        modifier = Modifier
            .width(180.dp)
            .height(270.dp),
        shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2A),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster Image Placeholder
            Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            
            // Gradient Overlay
            Box(modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                    startY = 100f
                )
            ))
            
            Text(
                text = movie.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            // Rating Badge
            Surface(
                onClick = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Text(
                    text = "★ ${movie.rating}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
