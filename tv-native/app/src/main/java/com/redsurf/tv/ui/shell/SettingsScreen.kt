package com.redsurf.tv.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.UpdateCheckStatus
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * A real minimal screen, not a placeholder (user request, 2026-09-11) - testing needs a way to
 * try a different playlist without reinstalling the app. Not multi-playlist management, just a
 * reset. Full Settings is a later phase.
 *
 * "Check for updates" (user request, 2026-09-11) is a fallback for the silent launch-time check:
 * that check can legitimately find nothing if a release publishes after the app already opened,
 * or if a previous update got stuck behind the system's install-permission screen with no way to
 * retry from inside the app. This button re-runs the exact same check on demand and shows the
 * result inline, sharing MainViewModel.updateStatus so a found update surfaces the same install
 * dialog MainActivity already shows on launch - not a second, separate update path.
 */
@Composable
fun SettingsScreen(
    updateStatus: UpdateCheckStatus,
    onCheckForUpdates: () -> Unit,
    onResetPlaylist: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(24.dp))

            ActionButton(label = "Check for updates", onClick = onCheckForUpdates)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (updateStatus) {
                    is UpdateCheckStatus.Idle -> "Checks for a new version on GitHub."
                    is UpdateCheckStatus.Checking -> "Checking..."
                    is UpdateCheckStatus.UpToDate -> "You're on the latest version."
                    is UpdateCheckStatus.Available -> "Update found: ${updateStatus.info.newVersion} - see the install prompt."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (updateStatus is UpdateCheckStatus.Available) Accent else TextSecondary,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!confirming) {
                ActionButton(label = "Reset & add a different playlist", onClick = { confirming = true })
            } else {
                Text(
                    "This deletes the current playlist and its channels. Press OK again to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    ActionButton(
                        label = "Confirm reset",
                        onClick = {
                            confirming = false
                            onResetPlaylist()
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButton(label = "Cancel", onClick = { confirming = false })
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)),
        colors = RedSurfFocus.colors(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}
