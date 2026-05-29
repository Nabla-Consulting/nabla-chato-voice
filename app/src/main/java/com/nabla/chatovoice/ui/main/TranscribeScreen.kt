package com.nabla.chatovoice.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.nabla.chatovoice.util.stripMarkdown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter



// ---------------------------------------------------------------------------
// Markdown composable — renders markdown in a TextView via AndroidView
// ---------------------------------------------------------------------------

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paddingPx = with(density) { 12.dp.toPx().toInt() }
    // Use Android ScrollView wrapping TextView so scrolling works natively
    AndroidView(
        factory = { ctx ->
            val scrollView = android.widget.ScrollView(ctx).apply {
                isFillViewport = true
            }
            val textView = android.widget.TextView(ctx).apply {
                val markwon = io.noties.markwon.Markwon.create(ctx)
                markwon.setMarkdown(this, text)
                setPadding(paddingPx, paddingPx + paddingPx * 2, paddingPx, paddingPx) // extra top for toggle
            }
            scrollView.addView(textView)
            scrollView
        },
        update = { scrollView ->
            val textView = scrollView.getChildAt(0) as? android.widget.TextView ?: return@AndroidView
            val markwon = io.noties.markwon.Markwon.create(context)
            markwon.setMarkdown(textView, text)
        },
        modifier = modifier,
    )
}

@Composable
fun TranscribeScreen(viewModel: TranscribeViewModel) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    val subTabs = listOf("Transcript", "Notes", "Summary")

    // contextNotes hoisted here so all sub-tabs share it; restore from prefs so it survives app restarts
    var contextNotes by rememberSaveable { mutableStateOf(viewModel.savedContextNotes) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }
        when (selectedSubTab) {
            0 -> TranscriptSubTab(
                viewModel = viewModel,
                contextNotes = contextNotes,
                onClear = { contextNotes = "" },
            )
            1 -> NotesSubTab(
                contextNotes = contextNotes,
                onContextNotesChange = { contextNotes = it },
                viewModel = viewModel,
            )
            2 -> SummarySubTab(
                viewModel = viewModel,
                contextNotes = contextNotes
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-tab 0: Transcript
// ---------------------------------------------------------------------------

@Composable
private fun TranscriptSubTab(
    viewModel: TranscribeViewModel,
    contextNotes: String = "",
    onClear: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entries by viewModel.transcriptEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var markdownMode by rememberSaveable { mutableStateOf(false) }

    // Auto-scroll to bottom as new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status bar
        val statusText = when (val s = state) {
            is TranscribeState.Idle -> if (entries.isEmpty()) "Ready to transcribe" else "Transcription stopped"
            is TranscribeState.Recording -> "🔴 Capturing… ${entries.size} utterances"
            is TranscribeState.Stopping -> "⏳ Stopping…"
            is TranscribeState.Error -> "⚠️ ${s.message}"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (state is TranscribeState.Error)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Control buttons — all weight(1f) + height(40.dp) for visual homogeneity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is TranscribeState.Idle, is TranscribeState.Error -> {
                    Button(
                        onClick = {
                            if (state is TranscribeState.Error) viewModel.dismissError()
                            viewModel.startTranscription(contextNotes)
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Text("Start")
                    }
                }
                is TranscribeState.Recording -> {
                    Button(
                        onClick = { viewModel.stopTranscription() },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Stop")
                    }
                }
                is TranscribeState.Stopping -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Text("Stopping...")
                    }
                }
            }

            OutlinedButton(
                onClick = { copyTranscriptToClipboard(context, entries, markdownMode) },
                enabled = entries.isNotEmpty(),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Text("Copy")
            }

            OutlinedButton(
                onClick = {
                    viewModel.clearTranscript()
                    onClear()
                },
                enabled = entries.isNotEmpty() || state !is TranscribeState.Recording,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Text("Clear")
            }
        }

        // Transcript box with floating markdown toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Transcript will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(entries) { entry ->
                                TranscriptEntryRow(entry)
                            }
                        }
                    }
                }
            }
            // Floating toggle — top-right corner over the textbox
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = markdownMode, onCheckedChange = { markdownMode = it }, modifier = Modifier.scale(0.65f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-tab 1: Notes
// ---------------------------------------------------------------------------

@Composable
private fun NotesSubTab(
    contextNotes: String,
    onContextNotesChange: (String) -> Unit,
    viewModel: TranscribeViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = contextNotes,
            onValueChange = { newValue ->
                onContextNotesChange(newValue)
                viewModel.saveContextNotes(newValue)
            },
            label = { Text("Context notes") },
            placeholder = { Text("e.g. Product design meeting with John") },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-tab 2: Summary
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummarySubTab(
    viewModel: TranscribeViewModel,
    contextNotes: String,
) {
    val entries by viewModel.transcriptEntries.collectAsStateWithLifecycle()
    val summaryText by viewModel.summaryText.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    var markdownMode by rememberSaveable { mutableStateOf(false) }

    val obsidianFolders = listOf("Daily", "Plans", "Projects", "Research", "Tmp")
    var selectedFolder by rememberSaveable { mutableStateOf(viewModel.defaultFolder) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val defaultTitle = remember {
        val today = LocalDate.now()
        "${today.format(DateTimeFormatter.ISO_LOCAL_DATE)} Meeting notes"
    }
    var noteTitle by remember { mutableStateOf(defaultTitle) }

    // Auto-dismiss save status after 3 seconds
    LaunchedEffect(saveStatus) {
        if (saveStatus != null) {
            delay(3000L)
            viewModel.clearSaveStatus()
        }
    }

    val showObsidianSection = summaryText != null || entries.isNotEmpty()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Summarize + Copy buttons in one row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.summarize(contextNotes) },
                enabled = entries.isNotEmpty() && !isSummarizing,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                if (isSummarizing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Summarize")
            }
            OutlinedButton(
                onClick = {
                    val textToCopy = if (markdownMode) {
                        summaryText ?: ""
                    } else {
                        stripMarkdown(summaryText ?: "")
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("summary", textToCopy))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                enabled = summaryText != null,
                modifier = Modifier.weight(1f).height(40.dp),
            ) { Text("Copy") }
        }

        // Summary text box — fills remaining space; floating MD toggle top-right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                summaryText?.let { summary ->
                    // Always render as Markdown — toggle only affects copy
                    MarkdownText(
                        text = summary,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Summary will appear here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            // Floating MD toggle — top-right over the textbox
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = markdownMode, onCheckedChange = { markdownMode = it }, modifier = Modifier.scale(0.65f))
            }
        }

        // Save to Obsidian — shown when there's content to save
        if (showObsidianSection) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            // Row 1: folder dropdown + title field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { folderMenuExpanded = true }) {
                        Text(selectedFolder, style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(
                        expanded = folderMenuExpanded,
                        onDismissRequest = { folderMenuExpanded = false },
                    ) {
                        obsidianFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder) },
                                onClick = {
                                    selectedFolder = folder
                                    viewModel.saveDefaultFolder(folder)
                                    folderMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            // Row 2: ExposedDropdownMenuBox (what to save) + Save button
            var saveTarget by remember { mutableStateOf("Summary") }
            val saveTargetOptions = buildList {
                if (summaryText != null) add("Summary")
                if (entries.isNotEmpty()) add("Transcript")
                if (summaryText != null && entries.isNotEmpty()) add("Both")
            }
            var saveDropdownExpanded by remember { mutableStateOf(false) }

            // Snap saveTarget to a valid option when options change
            LaunchedEffect(saveTargetOptions) {
                if (saveTarget !in saveTargetOptions && saveTargetOptions.isNotEmpty()) {
                    saveTarget = saveTargetOptions.first()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = saveDropdownExpanded,
                    onExpandedChange = {
                        if (saveTargetOptions.isNotEmpty()) saveDropdownExpanded = it
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = saveTarget,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Save") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = saveDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = saveDropdownExpanded,
                        onDismissRequest = { saveDropdownExpanded = false },
                    ) {
                        saveTargetOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { saveTarget = option; saveDropdownExpanded = false },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        when (saveTarget) {
                            "Summary" -> viewModel.saveSummaryToObsidian(selectedFolder, noteTitle)
                            "Transcript" -> viewModel.saveTranscriptToObsidian(selectedFolder, noteTitle)
                            "Both" -> viewModel.saveBothToObsidian(selectedFolder, noteTitle)
                        }
                    },
                    enabled = saveTargetOptions.isNotEmpty(),
                    modifier = Modifier.width(80.dp).height(56.dp),
                ) {
                    Text("Save")
                }
            }

            // Save status feedback (auto-dismissed via LaunchedEffect above)
            if (saveStatus != null) {
                Text(
                    text = saveStatus ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (saveStatus?.startsWith("✅") == true)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

@Composable
private fun TranscriptEntryRow(entry: TranscriptEntry) {
    // Assign consistent color per speaker number (0-indexed)
    val speakerIndex = entry.speakerId.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0
    val bubbleColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val textColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val bubbleColor = bubbleColors[speakerIndex % bubbleColors.size]
    val textColor = textColors[speakerIndex % textColors.size]

    // Alternate alignment: GUEST_1 left, GUEST_2 right, others left
    val alignEnd = speakerIndex % 2 == 1

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        // Speaker label + timestamp
        Row(
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Text(
                text = "${entry.speakerId}  ${entry.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        // Bubble
        SelectionContainer {
            Surface(
                shape = if (alignEnd)
                    RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                else
                    RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = bubbleColor,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = entry.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun copyTranscriptToClipboard(
    context: Context,
    entries: List<TranscriptEntry>,
    markdownMode: Boolean = false,
) {
    val text = if (markdownMode) {
        buildString {
            appendLine("## Transcript")
            appendLine()
            entries.forEach { entry ->
                appendLine("**[${entry.timestamp}] ${entry.speakerId}:** ${entry.text}")
                appendLine()
            }
        }.trimEnd()
    } else {
        entries.joinToString("\n") { "[${it.timestamp}] ${it.speakerId}: ${it.text}" }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("transcript", text))
    Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
}
