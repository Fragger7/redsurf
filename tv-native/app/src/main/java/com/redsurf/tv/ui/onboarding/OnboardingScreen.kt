package com.redsurf.tv.ui.onboarding

import androidx.compose.ui.graphics.RectangleShape
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.TextSecondary
import com.redsurf.tv.R
import com.redsurf.tv.ui.theme.CrestButton
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    localIp: String,
    port: Int,
    onXtreamSubmit: (name: String, server: String, user: String, pass: String) -> Unit,
    onM3uSubmit: (name: String, url: String) -> Unit,
    // Non-null only when reached via Settings -> "Add another playlist" (user request,
    // 2026-09-12) rather than true first-run onboarding, which has nowhere to cancel back to.
    onCancel: (() -> Unit)? = null,
) {
    var selectedMethod by remember { mutableStateOf<OnboardingMethod?>(null) }

    BackHandler(enabled = selectedMethod != null) {
        selectedMethod = null
    }
    BackHandler(enabled = selectedMethod == null && onCancel != null) {
        onCancel?.invoke()
    }

    // No background of its own: MainActivity's root draws Surf's backdrop behind it.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (selectedMethod == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App Logo / Name
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 32.dp)) {
                    // The real mark + wordmark (branding, 2026-09-16), not a placeholder red dot.
                    Image(
                        painter = painterResource(R.drawable.ic_mark),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("RedSurf", style = RedSurfType.wordmark.copy(fontSize = 48.sp), color = Accent)
                }

                Text(
                    if (onCancel != null) "Add another playlist:" else "Welcome. Please select a setup method:",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    OnboardingCard("Mobile Phone", "Scan QR or visit IP to connect") {
                        selectedMethod = OnboardingMethod.Mobile
                    }
                    OnboardingCard("Xtream Codes", "Login with Server, Username, Password") {
                        selectedMethod = OnboardingMethod.Xtream
                    }
                    OnboardingCard("M3U Playlist", "Enter a direct M3U URL") {
                        selectedMethod = OnboardingMethod.M3U
                    }
                    OnboardingCard("Cloud Login", "Sync playlists from the web") {
                        selectedMethod = OnboardingMethod.Cloud
                    }
                }

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    CrestButton("Cancel - back to Live TV", onClick = onCancel)
                }
            }
        } else {
            when (selectedMethod) {
                OnboardingMethod.Xtream -> XtreamInputForm(
                    onSubmit = onXtreamSubmit,
                    onBack = { selectedMethod = null }
                )
                OnboardingMethod.M3U -> M3uInputForm(
                    onSubmit = onM3uSubmit,
                    onBack = { selectedMethod = null }
                )
                OnboardingMethod.Mobile -> MobilePairingView(
                    ip = localIp,
                    port = port,
                    onBack = { selectedMethod = null }
                )
                OnboardingMethod.Cloud -> CloudSetupContent(
                    onBack = { selectedMethod = null }
                )
                null -> {}
            }
        }
    }
}

enum class OnboardingMethod { Xtream, M3U, Mobile, Cloud }

@Composable
fun CloudSetupContent(onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(500.dp)) {
        Text("Cloud Login", style = MaterialTheme.typography.headlineLarge, color = TextPrimary, modifier = Modifier.padding(bottom = 16.dp))
        Text("Web syncing is currently disabled in this version for local privacy. Please use the Local Setup (Mobile Phone) option.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        CrestButton("Back", onClick = onBack)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingCard(title: String, subtitle: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(180.dp)
            .onFocusChanged { isFocused = it.isFocused },
        // Crest: square, solid red when focused, no ring/glow/scale - the same cursor as a row.
        colors = RedSurfFocus.rowColors(resting = SurfaceColor),
        shape = RedSurfFocus.shape(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) TextPrimary else TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun XtreamInputForm(onSubmit: (String, String, String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(400.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Xtream Codes Details", color = TextPrimary, style = MaterialTheme.typography.headlineLarge)

        // Parity with the Mobile Phone pairing form (user request, 2026-09-12): that form has
        // always had a name field, this one didn't - every on-screen Xtream playlist showed up
        // as the same generic "Xtream Playlist" with no way to tell two apart.
        TvTextField(value = name, onValueChange = { name = it }, label = "Playlist Name (optional)")
        TvTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "Server URL (http://...)")
        TvTextField(value = username, onValueChange = { username = it }, label = "Username")
        TvTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
            CrestButton("Connect", onClick = { onSubmit(name, serverUrl, username, password) })
            CrestButton("Back", onClick = onBack)
        }
    }
}

@Composable
fun M3uInputForm(onSubmit: (String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(400.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("M3U Playlist URL", color = TextPrimary, style = MaterialTheme.typography.headlineLarge)

        TvTextField(value = name, onValueChange = { name = it }, label = "Playlist Name (optional)")
        TvTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, label = "http://...")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
            CrestButton("Connect", onClick = { onSubmit(name, m3uUrl) })
            CrestButton("Back", onClick = onBack)
        }
    }
}

@Composable
fun MobilePairingView(ip: String, port: Int, onBack: () -> Unit) {
    val url = "http://$ip:$port"
    val qrBitmap = remember(url) { generatePairingQrCode(url) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add via Mobile Phone", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Ensure your phone is on the same WiFi as the TV.", color = TextSecondary)
        Spacer(modifier = Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // White quiet zone is deliberate - a QR rendered straight onto the dark theme
            // background doesn't scan reliably, regardless of app theme (user request, 2026-09-11:
            // avoid typing the pairing URL by hand on the phone).
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White, shape = RectangleShape)
                        .padding(20.dp),
                ) {
                    Image(bitmap = qrBitmap, contentDescription = "QR code for $url", modifier = Modifier.size(200.dp))
                }
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text("Scan with your phone's camera, or", color = TextSecondary)
                Text("open your phone's browser and go to:", color = TextSecondary)
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .background(SurfaceColor, shape = RectangleShape)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                ) {
                    Text(url, style = MaterialTheme.typography.headlineMedium, color = Accent)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        CrestButton("Back to Options", onClick = onBack)
    }
}

/**
 * zxing:core is pure Java - no Android Bitmap support built in - so the BitMatrix it returns is
 * converted to an Android Bitmap by hand, one pixel at a time (a QR at typical sizes is a few
 * hundred px per side, so this is cheap). Null on any encode failure (e.g. a malformed URL) so
 * the pairing screen degrades to text-only rather than crashing.
 */
private fun generatePairingQrCode(content: String, sizePx: Int = 480): ImageBitmap? = try {
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bitmap.asImageBitmap()
} catch (e: Exception) {
    null
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label, color = if (isFocused) TextPrimary else TextSecondary) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            // Real bug, disclosed since 1.1 (docs/plans/PHASE_1.md): Material3's OutlinedTextField
            // is a touch-oriented component whose internal BasicTextField swallows DPAD_DOWN/UP
            // before Compose's own focus-traversal ever sees the key press, stranding a D-pad user
            // in the field with no way to reach Connect/Back. onPreviewKeyEvent intercepts on the
            // way down to the focused node - before that internal handling - so it can hand
            // UP/DOWN off to FocusManager.moveFocus explicitly. LEFT/RIGHT are left alone; those
            // still mean "move the text cursor" while editing, correctly.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        // Crest: square corners, like every other focusable.
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = SurfaceRaised,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextSecondary,
            cursorColor = Accent
        )
    )
}
