package com.nabla.chatovoice.ui.main

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabla.chatovoice.util.DebugLogger

private const val APP_VERSION = "v0.1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiData by viewModel.uiData.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chato Voice", fontWeight = FontWeight.Bold)
                        Text(APP_VERSION, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Status row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = if (uiData.isAccessibilityConnected) Color(0xFF4CAF50) else Color(0xFFFF9800)
                Surface(color = statusColor, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                Text(
                    text = if (uiData.isAccessibilityConnected) "Accessibility ON" else "Accessibility OFF",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // State indicator
            val currentState = uiData.state
            Text(
                text = when (currentState) {
                    is UiState.Idle -> "Ready"
                    is UiState.Recording -> "🎙 Recording..."
                    is UiState.Processing -> "⏳ Thinking..."
                    is UiState.Speaking -> "🔊 Speaking..."
                    is UiState.Error -> "⚠️ ${currentState.message}"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            // Transcription
            if (uiData.lastTranscription.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("You:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(uiData.lastTranscription, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Response
            if (uiData.lastResponse.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Chato:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(uiData.lastResponse, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Push-to-talk button
            val isRecording = uiData.state is UiState.Recording
            val isProcessing = uiData.state is UiState.Processing || uiData.state is UiState.Speaking
            Button(
                onClick = {},
                modifier = Modifier
                    .size(120.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                viewModel.onPushToTalkDown()
                                tryAwaitRelease()
                                viewModel.onPushToTalkUp()
                            }
                        )
                    },
                shape = CircleShape,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isRecording) "🎙" else "🎤",
                    fontSize = 36.sp
                )
            }

            Text(
                text = if (isRecording) "Release to send" else "Hold to talk",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Debug log panel
            val logs by DebugLogger.logs.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { DebugLogger.clear() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = Color(0xFF1A1A1A),
                shape = MaterialTheme.shapes.small
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF88),
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(
            initialUrl = uiData.gatewayUrl,
            initialToken = uiData.gatewayToken,
            onDismiss = { showSettings = false },
            onSave = { url, token ->
                viewModel.saveSettings(url, token)
                showSettings = false
            }
        )
    }
}
