package com.nabla.chatovoice.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.nabla.chatovoice.util.DebugLogger

private const val APP_VERSION = "v0.1"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiData by viewModel.uiData.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chat", "Debug")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Chato Voice", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 24.dp, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentState = uiData.state
                    Text(
                        text = when (currentState) {
                            is UiState.Idle -> "Ready"
                            is UiState.Recording -> "🎙 Recording..."
                            is UiState.Processing -> "⏳ Thinking..."
                            is UiState.Speaking -> "🔊 Speaking..."
                            is UiState.Error -> "⚠️ ${currentState.message}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = if (uiData.state is UiState.Error)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // PTT button — fixed, no scroll
                val isRecording = uiData.state is UiState.Recording
                val isProcessing = uiData.state is UiState.Processing || uiData.state is UiState.Speaking
                val pttColor = when {
                    isProcessing -> MaterialTheme.colorScheme.surfaceVariant
                    isRecording  -> MaterialTheme.colorScheme.error
                    else         -> MaterialTheme.colorScheme.primary
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(pttColor)
                        .pointerInput(isProcessing) {
                            if (!isProcessing) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.onPushToTalkDown()
                                        tryAwaitRelease()
                                        viewModel.onPushToTalkUp()
                                    }
                                )
                            }
                        }
                ) {
                    Text(
                        text = if (isRecording) "🎙" else "🎤",
                        fontSize = 32.sp
                    )
                }
                Text(
                    text = if (isRecording) "Release to send" else "Hold to talk",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ChatTab(uiData = uiData)
                1 -> DebugTab(context = context)
            }
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

@Composable
private fun ChatTab(uiData: MainUiData) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiData.messages.size) {
        if (uiData.messages.isNotEmpty()) listState.animateScrollToItem(uiData.messages.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(uiData.messages) { msg ->
            ChatBubble(msg)
        }
        // Processing indicator
        if (uiData.state is UiState.Processing) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = "...",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = if (isUser)
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
            else
                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            color = if (isUser)
                Color(0xFFE8E8E8)   // light gray
            else
                MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isUser) {
                    Text(
                        text = "Chato",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color(0xFF1A1A1A) else Color.Unspecified
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DebugTab(context: Context) {
    val logs by DebugLogger.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${logs.size} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { DebugLogger.clear() }, contentPadding = PaddingValues(0.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val text = logs.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("debug_logs", text))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    }
                ),
            color = Color(0xFF1A1A1A)
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
    }
}
