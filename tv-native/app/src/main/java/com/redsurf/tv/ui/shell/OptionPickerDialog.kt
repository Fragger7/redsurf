package com.redsurf.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary

/**
 * TiviMate-style preset list (polish sprint, 2026-10-27): OK on a Settings row opens the choices,
 * focus starts on the current one, OK picks and closes, Back closes unchanged. Replaces cycling a
 * row through its values one press at a time.
 */
@Composable
fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .background(SurfaceColor, RectangleShape)
                    .padding(28.dp),
            ) {
                Text(title, color = TextPrimary, fontSize = 22.sp)
                Spacer(Modifier.height(16.dp))
                options.forEach { option ->
                    val isSelected = option == selected
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onPick(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier),
                        shape = RedSurfFocus.shape(),
                        colors = RedSurfFocus.rowColors(resting = SurfaceRaised),
                        border = RedSurfFocus.border(),
                        glow = RedSurfFocus.glow(),
                        scale = RedSurfFocus.scale(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label(option), style = RedSurfType.rowTitle, color = TextPrimary)
                            // A radio-style mark; the focused row is solid red, so the mark is white there.
                            if (isSelected) Text("●", color = TextPrimary.copy(alpha = if (focused) 1f else 0.8f))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
}

/** A Settings row whose value is chosen from [options] in an [OptionPickerDialog]. Focus returns
 * to this row when the dialog closes (`FOCUS_MODEL.md` - every overlay reclaims focus on close). */
@Composable
fun <T> PickerRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    row: @Composable (value: String, onClick: () -> Unit, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var reclaim by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    row(optionLabel(selected), { open = true }, modifier.focusRequester(rowFocus))
    if (open) {
        OptionPickerDialog(
            title = label,
            options = options,
            selected = selected,
            label = optionLabel,
            onPick = { onPick(it); open = false; reclaim = true },
            onDismiss = { open = false; reclaim = true },
        )
    }
    LaunchedEffect(reclaim) {
        if (reclaim) {
            runCatching { rowFocus.requestFocus() }
            reclaim = false
        }
    }
}
