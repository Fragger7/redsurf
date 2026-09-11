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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.updater.UpdateManager
import com.redsurf.tv.ui.onboarding.OnboardingScreen
import com.redsurf.tv.ui.shell.AppShell
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfTheme
import com.redsurf.tv.ui.theme.Surface
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import com.redsurf.tv.db.RedSurfDatabase

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = RedSurfDatabase.getDatabase(this)
        viewModel.setDatabase(db, this)

        setContent {
            RedSurfTheme {
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

                // No blanket BackHandler here. AppShell and LiveTvScreen register their own,
                // narrowly enabled only when there's somewhere specific to go back to
                // (fullscreen -> columns, a tab -> Live TV). When neither is enabled - the root
                // Live TV screen - Back correctly falls through to Android's default: finish the
                // activity, returning to the TV home screen. The previous always-enabled handler
                // here deliberately no-op'd for AppState.Loaded ("prevent exiting the app"),
                // which is exactly what trapped a real user: found live, 2026-09-11.

                Box(
                    modifier = Modifier.fillMaxSize().background(Background),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator(color = Accent)
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                Text(s.message, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
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
                            AppShell(
                                viewModel = viewModel,
                                activePlaylistId = s.activePlaylistId,
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
 * "An update is available - install now?" TiViMate-class consent prompt. Phase 0 plumbing, not
 * the Phase 1 design system.
 *
 * Uses a real android.compose.ui.window.Dialog (a separate platform Window), not a same-
 * composition overlay Box. A same-composition Box only draws on top - it does not capture D-pad
 * focus, so the previous version of this dialog was unusable from a real remote: focus stayed
 * on whatever was focused in the screen underneath, and a DPAD press would navigate the
 * background instead of the dialog. This was caught by live-device testing, not by reading the
 * code (see docs/plans/PHASE_0.md #0.7). A Dialog is a distinct window and owns input focus for
 * as long as it's shown, which is what a modal actually requires.
 */
@Composable
private fun UpdateAvailableDialog(versionName: String, onInstall: () -> Unit, onDismiss: () -> Unit) {
    ModalCard(onDismissRequest = onDismiss) { installButtonFocus ->
        Text("Update available", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "RedSurf $versionName is ready to install.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Button(
                onClick = onInstall,
                modifier = Modifier.focusRequester(installButtonFocus),
                colors = ButtonDefaults.colors(containerColor = Accent)
            ) {
                Text("Install now")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(containerColor = SurfaceRaised)
            ) {
                Text("Later")
            }
        }
    }
}

/**
 * Shown when the OS blocks the install because RedSurf hasn't been granted permission to
 * install unknown apps yet (Android 8+, per-app toggle). Explains why before sending the user
 * to the system settings screen, rather than silently failing - the behaviour this replaces.
 */
@Composable
private fun InstallPermissionDialog(onGoToSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalCard(onDismissRequest = onDismiss) { settingsButtonFocus ->
        Text("Permission needed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "To install updates, RedSurf needs permission to install unknown apps. " +
                "You'll be taken to Settings - enable it there, then reopen RedSurf to install the update.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Button(
                onClick = onGoToSettings,
                modifier = Modifier.focusRequester(settingsButtonFocus),
                colors = ButtonDefaults.colors(containerColor = Accent)
            ) {
                Text("Open settings")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(containerColor = SurfaceRaised)
            ) {
                Text("Cancel")
            }
        }
    }
}

/**
 * Shared modal chrome for the two dialogs above: a real platform Dialog window (so it actually
 * captures D-pad input, see UpdateAvailableDialog's doc comment), a dark card matching the rest
 * of the app's palette (tv-material3's default Surface color is light, which produced unreadable
 * white-on-white text - also caught live, not by reading the code), and the primary action
 * pre-focused so OK works immediately without the user having to navigate to it first.
 */
@Composable
private fun ModalCard(onDismissRequest: () -> Unit, content: @Composable (primaryActionFocus: FocusRequester) -> Unit) {
    val primaryActionFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .background(Surface, RoundedCornerShape(16.dp))
                    .padding(32.dp)
            ) {
                Column {
                    content(primaryActionFocus)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        primaryActionFocus.requestFocus()
    }
}
