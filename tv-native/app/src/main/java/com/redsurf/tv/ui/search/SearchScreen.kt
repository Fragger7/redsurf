package com.redsurf.tv.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.search.ProgramResult
import com.redsurf.tv.search.SearchLogic
import com.redsurf.tv.search.SearchResults
import com.redsurf.tv.ui.LogoOrLetter
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.CrestButton
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val RowH = 44.dp

/** One flattened row of the results list. */
private sealed interface SearchRow {
    data class Header(val text: String) : SearchRow
    data class Channel(val channel: ChannelEntity) : SearchRow
    data class Program(val program: ProgramResult) : SearchRow
}

/** Flattens results into rows. Playlist sub-headers appear only when channel matches span more
 * than one playlist ([SearchLogic.showPlaylistHeaders]); channels are then grouped per playlist
 * in order of first (best-ranked) appearance. */
private fun buildRows(results: SearchResults, playlistNames: Map<String, String>): List<SearchRow> {
    val rows = ArrayList<SearchRow>()
    if (results.channels.isNotEmpty()) {
        rows += SearchRow.Header("Channels")
        if (SearchLogic.showPlaylistHeaders(results.channels)) {
            results.channels.groupBy { it.playlistId }.forEach { (pid, list) ->
                rows += SearchRow.Header(playlistNames[pid] ?: "Playlist")
                list.forEach { rows += SearchRow.Channel(it) }
            }
        } else {
            results.channels.forEach { rows += SearchRow.Channel(it) }
        }
    }
    if (results.programs.isNotEmpty()) {
        rows += SearchRow.Header("Programs")
        results.programs.forEach { rows += SearchRow.Program(it) }
    }
    return rows
}

/**
 * SEARCH.md - the Search destination. State (query, results) is hoisted to AppShell so leaving
 * and returning keeps it. Back peels results -> query field -> the Search pill, then the app's
 * normal Back (AppShell's handler is excluded while [onInnerFocusChanged] reports true).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults,
    recents: List<String>,
    playlistNames: Map<String, String>,
    fieldFocusRequester: FocusRequester,
    /** Key of the result last opened; on entry focus lands on it instead of the field. */
    restoreKey: String?,
    onOpenChannel: (key: String, ChannelEntity) -> Unit,
    onOpenProgram: (key: String, ProgramResult) -> Unit,
    onRecordSearch: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onEscapeUp: () -> Unit,
    onInnerFocusChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    var anyFocused by remember { mutableStateOf(false) }
    val rows = remember(results, playlistNames) { buildRows(results, playlistNames) }
    val resultsFocusRequester = remember { FocusRequester() }
    val restoreFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(anyFocused) { onInnerFocusChanged(anyFocused) }
    // Leaving the destination must not leave a stale "inner focus" claim behind.
    DisposableEffect(Unit) { onDispose { onInnerFocusChanged(false) } }

    // Back peel (SEARCH.md): results -> field -> pill. Both are mutually exclusive with each other
    // and with AppShell's handler through `enabled`.
    BackHandler(enabled = anyFocused && !fieldFocused) {
        runCatching { fieldFocusRequester.requestFocus() }
    }
    BackHandler(enabled = fieldFocused) { onEscapeUp() }

    // Voice search: hidden entirely when no recogniser is installed.
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
    }
    val voiceAvailable = remember { runCatching { voiceIntent.resolveActivity(context.packageManager) != null }.getOrDefault(false) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onQueryChange)
        }
    }

    // Claim initial focus on the field, like every screen's initial-focus pattern.
    // Returning from a result's fullscreen lands on that result (state/focus discipline).
    LaunchedEffect(Unit) {
        delay(50)
        val idx = restoreKey?.let { k -> rows.indices.firstOrNull { rowKey(rows, it) == k } }
        if (idx != null) {
            runCatching { listState.scrollToItem(idx) }
            repeat(10) {
                if (runCatching { restoreFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        }
        runCatching { fieldFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .onFocusChanged { anyFocused = it.hasFocus },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { androidx.compose.material3.Text("Search channels and programs", color = if (fieldFocused) TextPrimary else TextSecondary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldFocusRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    // Same D-pad trap as onboarding's TvTextField: the inner field swallows UP/DOWN,
                    // so hand them off explicitly (UP escapes to the pill, deterministically).
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> { onEscapeUp(); true }
                            Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                            else -> false
                        }
                    },
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = SurfaceRaised,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextSecondary,
                    cursorColor = Accent,
                ),
            )
            if (voiceAvailable) {
                Spacer(Modifier.width(12.dp))
                CrestButton(
                    label = "Voice",
                    onClick = { runCatching { voiceLauncher.launch(voiceIntent) } },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            query.isBlank() -> RecentTiles(recents, onPick = onQueryChange, onRemove = onRemoveRecent, onClear = onClearRecents)
            rows.isEmpty() -> Text("No matches for \"${query.trim()}\"", style = RedSurfType.rowTitle, color = TextSecondary)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusRequester(resultsFocusRequester),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(rows.size, key = { i -> rowKey(rows, i) }) { i ->
                    val key = rowKey(rows, i)
                    val rowMod = if (key == restoreKey) Modifier.focusRequester(restoreFocusRequester) else Modifier
                    when (val r = rows[i]) {
                        is SearchRow.Header -> Text(
                            r.text,
                            style = RedSurfType.sectionTitle,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        is SearchRow.Channel -> ResultRow(
                            title = r.channel.name,
                            subtitle = null,
                            channel = r.channel,
                            modifier = rowMod,
                            onOpen = { onRecordSearch(); onOpenChannel(key, r.channel) },
                        )
                        is SearchRow.Program -> ResultRow(
                            title = r.program.title,
                            subtitle = "${r.program.channel.name}  ·  " +
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.program.startTime)),
                            channel = r.program.channel,
                            modifier = rowMod,
                            onOpen = { onRecordSearch(); onOpenProgram(key, r.program) },
                        )
                    }
                }
            }
        }
    }
}

private fun rowKey(rows: List<SearchRow>, i: Int): String = when (val r = rows[i]) {
    is SearchRow.Header -> "h:$i:${r.text}"
    is SearchRow.Channel -> "c:${r.channel.playlistId}:${r.channel.streamId}"
    is SearchRow.Program -> "p:$i"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    channel: ChannelEntity,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RedSurfFocus.shape(12.dp),
        colors = RedSurfFocus.rowColors(resting = SurfaceColor),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RowH).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(32.dp).clip(RectangleShape).background(SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                LogoOrLetter(channel.streamIcon, channel.name, RedSurfType.rowTitle, TextPrimary, Modifier.fillMaxSize().padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = RedSurfType.rowTitle,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.graphicsLayer().basicMarquee() else Modifier,
                )
                if (subtitle != null) {
                    Text(subtitle, style = RedSurfType.rowSecondary, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Recent queries: tiles, then a trailing Clear tile. Long-press OK on a tile removes it. */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun RecentTiles(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recents.isEmpty()) {
        Text("Type or speak to search. Recent searches appear here.", style = RedSurfType.rowTitle, color = TextSecondary)
        return
    }
    Text("Recent searches", style = RedSurfType.sectionTitle, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recents, key = { it }) { q ->
            var longHandled by remember { mutableStateOf(false) }
            Surface(
                onClick = { if (!longHandled) onPick(q) },
                onLongClick = { longHandled = true; onRemove(q) },
                colors = RedSurfFocus.rowColors(resting = SurfaceRaised),
                shape = RedSurfFocus.pillShape(),
                scale = RedSurfFocus.scale(),
                border = RedSurfFocus.border(),
                glow = RedSurfFocus.glow(),
            ) {
                Text(q, style = RedSurfType.rowSecondary, color = TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        item(key = "__clear__") { CrestButton(label = "Clear", onClick = onClear) }
    }
}
