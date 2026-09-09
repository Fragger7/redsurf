package com.redsurf.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.redsurf.tv.updater.UpdateManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.redsurf.tv.ui.TiViMateLayout
import com.redsurf.tv.ui.onboarding.OnboardingScreen
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.player.tuning.AfrManager

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = RedSurfDatabase.getDatabase(this)
        viewModel.setDatabase(db, this)

        // Trigger OTA Update check
        lifecycleScope.launch {
            val updateInfo = UpdateManager.checkForUpdates()
            if (updateInfo != null && updateInfo.hasUpdate) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Downloading Update: ${updateInfo.newVersion}", Toast.LENGTH_LONG).show()
                }
                UpdateManager.downloadAndInstall(this@MainActivity, updateInfo.downloadUrl, updateInfo.newVersion)
            }
        }

        
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                
                BackHandler(enabled = true) {
                    // Prevent exiting the app on back press if we are in loaded state
                    // We can handle deep backstack here if we had one, otherwise do nothing or prompt
                    if (state is AppState.Onboarding) {
                        finish()
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator(color = Color(0xFFE11D48))
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                Text(s.message, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        is AppState.Onboarding -> {
                            OnboardingScreen(
                                localIp = s.localIp,
                                port = s.port,
                                onXtreamSubmit = { server, user, pass ->
                                    viewModel.loadXtreamCodes(server, user, pass)
                                },
                                onM3uSubmit = { url ->
                                    viewModel.loadPlaylist(url, "M3U Playlist", url, "", "m3u")
                                }
                            )
                        }
                        is AppState.Loaded -> {
                            TiViMateLayout(
                                groups = s.groups,
                                playlists = s.playlists,
                                activePlaylistId = s.activePlaylistId,
                                viewModel = viewModel,
                                activity = this@MainActivity
                            )
                        }
                        is AppState.Error -> {
                            Text("Error: ${s.message}", color = Color.Red)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // AfrManager.restoreOriginalMode()
    }
}
