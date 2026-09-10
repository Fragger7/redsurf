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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.updater.UpdateManager
import com.redsurf.tv.ui.TiViMateLayout
import com.redsurf.tv.ui.onboarding.OnboardingScreen
import com.redsurf.tv.db.RedSurfDatabase

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = RedSurfDatabase.getDatabase(this)
        viewModel.setDatabase(db, this)

        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()

                // OTA update: check once per launch, but never install without the user's
                // consent, and never attempt the install if the OS will just block it.
                var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
                var showInstallPermissionPrompt by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val info = UpdateManager.checkForUpdates()
                    if (info != null && info.hasUpdate) {
                        updateInfo = info
                    }
                }

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

                    updateInfo?.let { info ->
                        UpdateAvailableDialog(
                            versionName = info.newVersion,
                            onInstall = {
                                if (UpdateManager.canInstallUnknownApps(this@MainActivity)) {
                                    UpdateManager.downloadAndInstall(this@MainActivity, info.downloadUrl, info.newVersion)
                                    updateInfo = null
                                } else {
                                    showInstallPermissionPrompt = true
                                }
                            },
                            onDismiss = { updateInfo = null }
                        )
                    }

                    if (showInstallPermissionPrompt) {
                        InstallPermissionDialog(
                            onGoToSettings = {
                                UpdateManager.requestInstallUnknownAppsPermission(this@MainActivity)
                                showInstallPermissionPrompt = false
                            },
                            onDismiss = { showInstallPermissionPrompt = false }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

/**
 * "An update is available - install now?" TiViMate-class consent prompt. Phase 0 plumbing,
 * not the Phase 1 design system - uses the same overlay-Box + tv-material3 Button pattern
 * already established in OnboardingScreen.kt's CloudSetupContent.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateAvailableDialog(versionName: String, onInstall: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text("Update available", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "RedSurf $versionName is ready to install.",
                    color = Color(0xFFA1A1AA),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.colors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("Install now")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(containerColor = Color.DarkGray)
                    ) {
                        Text("Later")
                    }
                }
            }
        }
    }
}

/**
 * Shown when the OS blocks the install because RedSurf hasn't been granted permission to
 * install unknown apps yet (Android 8+, per-app toggle). Explains why before sending the user
 * to the system settings screen, rather than silently failing - the behaviour this replaces.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InstallPermissionDialog(onGoToSettings: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text("Permission needed", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "To install updates, RedSurf needs permission to install unknown apps. " +
                        "You'll be taken to Settings - enable it there, then reopen RedSurf to install the update.",
                    color = Color(0xFFA1A1AA),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    Button(
                        onClick = onGoToSettings,
                        colors = ButtonDefaults.colors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("Open settings")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(containerColor = Color.DarkGray)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
