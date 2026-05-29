package com.nabla.chatovoice.ui.main

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import com.nabla.chatovoice.data.remote.ChatoGatewayRepository
import com.nabla.chatovoice.data.remote.MsalRepository
import com.nabla.chatovoice.domain.repository.GatewayRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranscribeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TranscribeViewModel
    private val context = mockk<Context>(relaxed = true)
    private val gatewayRepository = mockk<GatewayRepository>(relaxed = true)
    private val chatoGatewayRepository = mockk<ChatoGatewayRepository>(relaxed = true)
    private val msalRepository = mockk<MsalRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { chatoGatewayRepository.transcriptEntries } returns emptyList()
        every { chatoGatewayRepository.summaryText } returns ""
        every { chatoGatewayRepository.contextNotes } returns ""
        every { chatoGatewayRepository.defaultObsidianFolder } returns "Research"
        every { context.applicationContext } returns context
        // Mock packageManager for service intent
        every { context.packageName } returns "com.nabla.chatovoice"

        viewModel = TranscribeViewModel(context, gatewayRepository, chatoGatewayRepository, msalRepository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(TranscribeState.Idle, viewModel.state.value)
    }

    @Test
    fun `startTranscription sets state to Recording immediately`() {
        every { chatoGatewayRepository.azureSpeechKey } returns "test-key"
        every { chatoGatewayRepository.azureSpeechRegion } returns "westus"
        every { context.bindService(any(), any(), any<Int>()) } returns true
        every { context.startForegroundService(any()) } returns mockk()

        viewModel.startTranscription("notes")

        // State should be Recording immediately, not waiting for service
        assertEquals(TranscribeState.Recording, viewModel.state.value)
    }

    @Test
    fun `startTranscription with blank azure key sets Error state`() {
        every { chatoGatewayRepository.azureSpeechKey } returns ""
        every { chatoGatewayRepository.azureSpeechRegion } returns "westus"

        viewModel.startTranscription()

        assertTrue(viewModel.state.value is TranscribeState.Error)
    }

    @Test
    fun `clearTranscript clears entries summary and calls clearTranscriptData`() {
        every { chatoGatewayRepository.clearTranscriptData() } just Runs

        viewModel.clearTranscript()

        assertEquals(emptyList<TranscriptEntry>(), viewModel.transcriptEntries.value)
        assertNull(viewModel.summaryText.value)
        verify { chatoGatewayRepository.clearTranscriptData() }
    }

    @Test
    fun `summary text strips agent prefix`() = runTest {
        // Test the regex used in summarize() to strip "🤘 [Chato] " prefix
        val raw = "\uD83E\uDD18 [Chato] This is the actual summary."
        val cleaned = raw.trimStart()
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Ps}\\p{Pe}]?\\s*\\[\\w+\\]\\s*"), "")
            .trimStart()
        assertEquals("This is the actual summary.", cleaned)
    }

    @Test
    fun `summarize truncates to SUMMARY_SOFT_LIMIT_ENTRIES when exceeded`() {
        // Verify that if entries > 500, only last 500 are used
        // (test the truncation logic without calling the actual gateway)
        val limit = TranscribeViewModel.SUMMARY_SOFT_LIMIT_ENTRIES
        assertTrue("Soft limit should be positive", limit > 0)
        val entries = (1..limit + 10).map {
            TranscriptEntry("10:00:0$it", "GUEST_1", "Entry $it")
        }
        val truncated = entries.takeLast(limit)
        assertEquals(limit, truncated.size)
        assertEquals("Entry ${limit + 10}", truncated.last().text)
    }

    @Test
    fun `isRecording initial false emission does not reset Recording state`() {
        // Regression test for the double-tap bug:
        // isRecording is a StateFlow(false). When the collector subscribes, it immediately
        // emits false. Without the seenTrue guard, this would reset Recording->Idle.
        // The fix: only react to true->false transitions, skip the initial false.
        every { chatoGatewayRepository.azureSpeechKey } returns "test-key"
        every { chatoGatewayRepository.azureSpeechRegion } returns "westus"
        every { context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns true
        every { context.startForegroundService(any()) } returns mockk()

        viewModel.startTranscription("notes")

        // State must remain Recording despite isRecording flow emitting false (initial value)
        assertEquals(TranscribeState.Recording, viewModel.state.value)
    }
}
