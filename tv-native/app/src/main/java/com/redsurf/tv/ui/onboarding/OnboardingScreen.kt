package com.redsurf.tv.ui.onboarding

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun OnboardingScreen(
    localIp: String,
    port: Int,
    onXtreamSubmit: (String, String, String) -> Unit,
    onM3uSubmit: (String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf<OnboardingMethod?>(null) }

    BackHandler(enabled = selectedMethod != null) {
        selectedMethod = null
    }


    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)),
        contentAlignment = Alignment.Center
    ) {
        if (selectedMethod == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App Logo / Name
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 48.dp)) {
                    Box(modifier = Modifier.size(64.dp).background(Color(0xFFE11D48), androidx.compose.foundation.shape.CircleShape))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("RedSurf", style = MaterialTheme.typography.displayLarge, color = Color.White)
                }

                Text("Welcome. Please select a setup method:", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFA1A1AA), modifier = Modifier.padding(bottom = 32.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MethodCard("Local Setup (Phone/PC)", "Scan QR or visit IP to type credentials on your phone.") { selectedMethod = OnboardingMethod.QR }
                    MethodCard("Xtream Codes", "Type your server, username, and password using TV remote.") { selectedMethod = OnboardingMethod.XTREAM }
                    MethodCard("M3U Playlist", "Type an M3U URL using TV remote.") { selectedMethod = OnboardingMethod.M3U }
                    MethodCard("Cloud Login", "Login to sync playlists from the web.") { selectedMethod = OnboardingMethod.CLOUD }
                }
            }
        } else {
            when (selectedMethod) {
                OnboardingMethod.QR -> QrSetupContent(localIp, port)
                OnboardingMethod.XTREAM -> XtreamSetupContent(onSubmit = onXtreamSubmit)
                OnboardingMethod.M3U -> M3uSetupContent(onSubmit = onM3uSubmit)
                OnboardingMethod.CLOUD -> CloudSetupContent()
                null -> {}
            }
        }
    }
}

@Composable
fun CloudSetupContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(400.dp)) {
        Text("Cloud Login", style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
        Text("Web syncing is currently disabled in this version for local privacy. Please use the Local Setup (QR) option.", color = Color(0xFFA1A1AA), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 24.dp))
    }
}


package com.redsurf.tv.ui.onboarding

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun OnboardingScreen(
    localIp: String,
    port: Int,
    onXtreamSubmit: (String, String, String) -> Unit,
    onM3uSubmit: (String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf<OnboardingMethod?>(null) }

    BackHandler(enabled = selectedMethod != null) {
        selectedMethod = null
    }


    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)),
        contentAlignment = Alignment.Center
    ) {
        if (selectedMethod == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Welcome to RedSurf",
                    style = androidx.tv.material3.MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "How would you like to add your IPTV credentials?",
                    style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(48.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    OnboardingCard("Mobile Phone", "Scan QR code to send credentials instantly") {
                        selectedMethod = OnboardingMethod.Mobile
                    }
                    OnboardingCard("Xtream Codes", "Login with Server, Username, Password") {
                        selectedMethod = OnboardingMethod.Xtream
                    }
                    OnboardingCard("M3U Playlist", "Enter a direct M3U URL") {
                        selectedMethod = OnboardingMethod.M3U
                    }
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
                null -> {}
            }
        }
    }
}

enum class OnboardingMethod { Xtream, M3U, Mobile }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingCard(title: String, subtitle: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .height(180.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF18181B),
            focusedContainerColor = Color(0xFFE11D48)
        )
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
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) Color.White else Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun XtreamInputForm(onSubmit: (String, String, String) -> Unit, onBack: () -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(400.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Xtream Codes Details", color = Color.White, style = androidx.tv.material3.MaterialTheme.typography.headlineLarge)
        
        TvTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "Server URL (http://...)")
        TvTextField(value = username, onValueChange = { username = it }, label = "Username")
        TvTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
            androidx.tv.material3.Button(onClick = { onSubmit(serverUrl, username, password) }) {
                Text("Connect")
            }
            androidx.tv.material3.Button(
                onClick = onBack,
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.DarkGray)
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun M3uInputForm(onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var m3uUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.width(400.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("M3U Playlist URL", color = Color.White, style = androidx.tv.material3.MaterialTheme.typography.headlineLarge)
        
        TvTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, label = "http://...")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
            androidx.tv.material3.Button(onClick = { onSubmit(m3uUrl) }) {
                Text("Connect")
            }
            androidx.tv.material3.Button(
                onClick = onBack,
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.DarkGray)
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun MobilePairingView(ip: String, port: Int, onBack: () -> Unit) {
    val url = "http://$ip:$port"
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add via Mobile Phone", style = androidx.tv.material3.MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Ensure your phone is on the same WiFi as the TV.", color = Color.Gray)
        Text("Open your phone's web browser and go to:", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(Color(0xFF18181B), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Text(url, style = androidx.tv.material3.MaterialTheme.typography.displayLarge, color = Color(0xFFE11D48))
        }

        Spacer(modifier = Modifier.height(48.dp))
        androidx.tv.material3.Button(
            onClick = onBack,
            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color.DarkGray)
        ) {
            Text("Back to Options")
        }
    }
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
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label, color = if (isFocused) Color.White else Color.Gray) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFE11D48),
            unfocusedBorderColor = Color.DarkGray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.LightGray
        )
    )
}
