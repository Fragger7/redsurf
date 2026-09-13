package com.redsurf.tv.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.UpdateCheckStatus
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * A real minimal screen, not a placeholder (user request, 2026-09-11) - testing needs a way to
 * try a different playlist without reinstalling the app. Full Settings is a later phase, but user
 * request 2026-09-12 is to build real pieces of it incrementally as they become load-bearing for
 * testing, rather than holding everything for one big redesign - playlist management (this round)
 * is the first of those, called out explicitly as blocking testing itself: without it, verifying
 * anything that only takes effect on a fresh import (e.g. the tvg-chno/num fix, 2026-09-12) meant
 * "Reset," which nukes every other loaded playlist too, not just the one under test.
 *
 * "Check for updates" (user request, 2026-09-11) is a fallback for the silent launch-time check:
 * that check can legitimately find nothing if a release publishes after the app already opened,
 * or if a previous update got stuck behind the system's install-permission screen with no way to
 * retry from inside the app. This button re-runs the exact same check on demand and shows the
 * result inline, sharing MainViewModel.updateStatus so a found update surfaces the same install
 * dialog MainActivity already shows on launch - not a second, separate update path.
 *
 * "Add another playlist" (user request, 2026-09-12) is the non-destructive sibling of "Reset":
 * it re-opens the same onboarding flow (Mobile Phone / Xtream / M3U) without deleting anything
 * already loaded - see MainViewModel.beginAddPlaylist. Both playlists then show up together
 * under Live TV, grouped by playlist name (LiveTvScreen/GroupsColumn).
 *
 * The "Loaded playlists" list (user request, 2026-09-12) is the actual per-playlist management
 * piece: each row can remove just that one playlist and its channels
 * (MainViewModel.deletePlaylist), leaving every other one intact - unlike "Reset," which is
 * still kept for the "nuke everything and start over" case. `TvLazyColumn`, not a plain `Column`,
 * for the same reason ChannelsColumn/GroupsColumn use it - a real focus-navigable, scrollable list
 * for however many playlists exist, not a fixed-size layout.
 */
@Composable
fun SettingsScreen(
    updateStatus: UpdateCheckStatus,
    playlists: List<PlaylistEntity>,
    onCheckForUpdates: () -> Unit,
    onResetPlaylist: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        TvLazyColumn(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 480.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
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
                ActionButton(label = "Add another playlist", onClick = onAddPlaylist)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Keeps everything already loaded - both show up under Live TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        "Loaded playlists",
                        style = RedSurfType.sectionTitle,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        isLast = playlists.size == 1,
                        onDelete = { onDeletePlaylist(playlist.id) },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            item {
                if (!confirmingReset) {
                    ActionButton(label = "Reset & add a different playlist", onClick = { confirmingReset = true })
                } else {
                    Text(
                        "This deletes every loaded playlist and its channels. Press OK again to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column {
                        ActionButton(
                            label = "Confirm reset",
                            onClick = {
                                confirmingReset = false
                                onResetPlaylist()
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionButton(label = "Cancel", onClick = { confirmingReset = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistEntity, isLast: Boolean, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Text(playlist.type.uppercase(), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        if (!confirming) {
            ActionButton(label = "Remove", onClick = { confirming = true })
        } else {
            Text(
                if (isLast) {
                    "Last playlist - removing it returns to setup. Press OK again to confirm."
                } else {
                    "Removes this playlist and its channels. Press OK again to confirm."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(
                label = "Confirm remove",
                onClick = {
                    confirming = false
                    onDelete()
                },
            )
            Spacer(modifier = Modifier.height(6.dp))
            ActionButton(label = "Cancel", onClick = { confirming = false })
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
