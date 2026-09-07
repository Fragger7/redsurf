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
