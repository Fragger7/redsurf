package com.redsurf.tv.ui.shell

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import com.redsurf.tv.ui.theme.Surface as SurfaceColor

private const val PIN_LENGTH = 4

/**
 * TiviMate's parental "PIN code" prompt: four digits from an on-screen keypad or the remote's
 * number keys, submitted on the fourth. A real [Dialog] (its own window, so it owns D-pad focus -
 * the same reason MainActivity's ModalCard is one); closing it hands focus back to the row that
 * opened it.
 *
 * [confirmNew] = choosing a new PIN (enter twice, must match); otherwise [check] validates and a
 * wrong PIN clears with "Wrong PIN". [onDone] gets the accepted PIN.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PinDialog(
    title: String,
    confirmNew: Boolean,
    check: ((String) -> Boolean)?,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }

    fun submit(pin: String) {
        when {
            confirmNew && firstPin == null -> { firstPin = pin; error = null }
            confirmNew && firstPin != pin -> { firstPin = null; error = "PINs didn't match - try again" }
            !confirmNew && check?.invoke(pin) == false -> error = "Wrong PIN"
            else -> { onDone(pin); return }
        }
        entry = ""
    }

    fun type(d: Char) {
        if (entry.length >= PIN_LENGTH) return
        entry += d
        if (entry.length == PIN_LENGTH) submit(entry)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    if (code in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9) {
                        if (e.type == KeyEventType.KeyDown) type('0' + (code - NativeKeyEvent.KEYCODE_0))
                        true
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .background(SurfaceColor, RectangleShape)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (confirmNew && firstPin != null) "Confirm the PIN" else title,
                    color = TextPrimary, fontSize = 22.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(PIN_LENGTH) { i ->
                        Box(
                            Modifier
                                .size(16.dp)
                                .background(if (i < entry.length) Accent else SurfaceRaised, RectangleShape),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(error ?: " ", color = Accent, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "Cancel")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Surface(
                                onClick = {
                                    when (k) {
                                        "⌫" -> entry = entry.dropLast(1)
                                        "Cancel" -> onDismiss()
                                        else -> type(k[0])
                                    }
                                },
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(48.dp)
                                    .then(if (k == "1") Modifier.focusRequester(firstKey) else Modifier),
                                shape = RedSurfFocus.shape(),
                                colors = RedSurfFocus.rowColors(resting = SurfaceRaised),
                                border = RedSurfFocus.border(),
                                glow = RedSurfFocus.glow(),
                                scale = RedSurfFocus.scale(),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(k, fontSize = if (k.length == 1) 20.sp else 16.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text("Number keys on the remote work too", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
    LaunchedEffect(Unit) { firstKey.requestFocus() }
}
