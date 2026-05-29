package com.nabla.chatovoice.ui.main

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.nabla.chatovoice.ui.theme.ChatoVoiceTheme
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val transcribeViewModel: TranscribeViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // User can still see the UI; push-to-talk will fail gracefully
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.log("APP", "onCreate -- v0.1")
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        viewModel.initTts()
        // Fix Bug 3: provide Activity reference for MSAL interactive sign-in
        transcribeViewModel.setActivity(this)
        setContent {
            ChatoVoiceTheme {
                MainScreen(viewModel = viewModel, transcribeViewModel = transcribeViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fix Bug 3: refresh activity ref on resume (Activity may have been recreated)
        transcribeViewModel.setActivity(this)
        viewModel.refreshAccessibilityStatus()
    }
}
