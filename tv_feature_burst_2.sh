#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/db"
mkdir -p "$BASE_DIR/ui/onboarding"

# 1. Update Gradle for Room & KSP
cat << 'APP_GRADLE' > tv-native/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "com.redsurf.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.redsurf.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Jetpack Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.compose.material3:material3:1.2.0") // For TextFields
    
    // ExoPlayer Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Room Database for lightning-fast caching
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:\$room_version")
    implementation("androidx.room:room-ktx:\$room_version")
    ksp("androidx.room:room-compiler:\$room_version")

    // Firebase (BOM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
}
APP_GRADLE

# 2. Room Database Entities & DAO
cat << 'KOTLIN' > "$BASE_DIR/db/RedSurfDatabase.kt"
package com.redsurf.tv.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val groupName: String,
    val epgId: String
)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY groupName, name")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()
}

@Database(entities = [ChannelEntity::class], version = 1, exportSchema = false)
abstract class RedSurfDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: RedSurfDatabase? = null

        fun getDatabase(context: Context): RedSurfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RedSurfDatabase::class.java,
                    "redsurf_tv_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
KOTLIN

# 3. Native TV Onboarding UI
cat << 'KOTLIN' > "$BASE_DIR/ui/onboarding/OnboardingScreen.kt"
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
KOTLIN

# 4. Integrate Native UI into MainActivity & ViewModel
cat << 'KOTLIN' > "$BASE_DIR/MainActivity.kt"
package com.redsurf.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.ui.TiViMateLayout
import com.redsurf.tv.ui.onboarding.OnboardingScreen
import com.redsurf.tv.db.RedSurfDatabase

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inject Database
        val db = RedSurfDatabase.getDatabase(this)
        viewModel.setDatabase(db)

        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()

                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            Text("Loading...", color = Color.White)
                        }
                        is AppState.Onboarding -> {
                            OnboardingScreen(
                                pairingCode = s.pairingCode,
                                onXtreamSubmit = { server, user, pass ->
                                    viewModel.loadXtreamCodes(server, user, pass)
                                },
                                onM3uSubmit = { url ->
                                    viewModel.loadPlaylist(url)
                                }
                            )
                        }
                        is AppState.Loaded -> {
                            TiViMateLayout(groups = s.groups)
                        }
                        is AppState.Error -> {
                            Text("Error: ${s.message}", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
KOTLIN

cat << 'KOTLIN' > "$BASE_DIR/MainViewModel.kt"
package com.redsurf.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

sealed class AppState {
    object Loading : AppState()
    data class Onboarding(val pairingCode: String?) : AppState()
    data class Loaded(val groups: List<ChannelGroup>) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val firestoreDb = Firebase.firestore
    private var localDb: RedSurfDatabase? = null
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state

    fun setDatabase(db: RedSurfDatabase) {
        this.localDb = db
        checkLocalCache()
    }

    private fun checkLocalCache() {
        viewModelScope.launch {
            val cachedChannels = localDb?.channelDao()?.getAllChannels()?.firstOrNull()
            if (!cachedChannels.isNullOrEmpty()) {
                val grouped = cachedChannels.map {
                    Channel(it.id, it.name, it.streamUrl, it.logoUrl, it.groupName, it.epgId)
                }.groupBy { it.group }.map { ChannelGroup(it.key, it.value) }
                
                _state.value = AppState.Loaded(grouped)
            } else {
                generatePairingCode()
            }
        }
    }

    private fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        _state.value = AppState.Onboarding(code)

        val sessionRef = firestoreDb.collection("pairingSessions").document(code)
        
        viewModelScope.launch {
            try {
                sessionRef.set(mapOf("status" to "waiting", "createdAt" to System.currentTimeMillis())).await()

                sessionRef.addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        if (snapshot.getString("status") == "paired") {
                            val url = snapshot.getString("url")
                            if (!url.isNullOrEmpty()) loadPlaylist(url)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore silent firestore fails, they can still use native UI
            }
        }
    }

    fun loadXtreamCodes(server: String, user: String, pass: String) {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        val url = "\$cleanServer/get.php?username=\$user&password=\$pass&type=m3u_plus&output=ts"
        loadPlaylist(url)
    }

    fun loadPlaylist(url: String) {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    val inputStream = URL(url).openStream()
                    M3uParser.parse(inputStream)
                }

                // Cache in Room Database for instant next boot
                val entities = channels.map {
                    ChannelEntity(it.id, it.name, it.streamUrl, it.logoUrl, it.group, it.epgId)
                }
                localDb?.channelDao()?.clearAll()
                localDb?.channelDao()?.insertChannels(entities)

                val grouped = channels.groupBy { it.group }
                    .map { ChannelGroup(it.key, it.value) }
                    .sortedBy { it.name }

                _state.value = AppState.Loaded(grouped)
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load playlist")
            }
        }
    }
}
KOTLIN

chmod +x tv_feature_burst_2.sh
./tv_feature_burst_2.sh
