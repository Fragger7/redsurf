package com.redsurf.tv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingScreen(
    pairingCode: String?,
    onXtreamSubmit: (String, String, String) -> Unit,
    onM3uSubmit: (String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf<OnboardingMethod?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)),
        contentAlignment = Alignment.Center
    ) {
        if (selectedMethod == null) {
            // Method Selection
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Welcome to RedSurf",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Choose how you want to add your playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingCard("Xtream Codes", "Login with Server, Username & Password") {
                        selectedMethod = OnboardingMethod.Xtream
                    }
                    OnboardingCard("M3U Playlist", "Enter a direct M3U URL") {
                        selectedMethod = OnboardingMethod.M3U
                    }
                    OnboardingCard("Mobile App", "Scan QR code to add via phone") {
                        selectedMethod = OnboardingMethod.Mobile
                    }
                }
            }
        } else {
            // Specific Input Forms
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
                    code = pairingCode,
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
            focusedContainerColor = Color(0xFFE11D48) // Rose 600
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
fun MobilePairingView(code: String?, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add via Mobile Phone", style = androidx.tv.material3.MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Go to redsurf.app on your phone and enter this code:", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Text(code ?: "...", style = androidx.tv.material3.MaterialTheme.typography.displayLarge, color = Color(0xFFE11D48))
        Spacer(modifier = Modifier.height(32.dp))
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
        label = { androidx.compose.material3.Text(label) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFE11D48),
            unfocusedBorderColor = Color.DarkGray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.LightGray,
            focusedLabelColor = Color(0xFFE11D48),
            unfocusedLabelColor = Color.Gray
        )
    )
}
