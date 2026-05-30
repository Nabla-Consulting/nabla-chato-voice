package com.nabla.chatovoice.ui.main

import com.nabla.chatovoice.data.remote.ChatoGatewayRepository
import com.nabla.chatovoice.domain.model.ChatResponse
import com.nabla.chatovoice.domain.repository.GatewayRepository
import com.nabla.chatovoice.service.TextToSpeechManager
import com.nabla.chatovoice.service.VoiceInputManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelConversationTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel
    private val gatewayRepository = mockk<GatewayRepository>(relaxed = true)
    private val voiceInputManager = mockk<VoiceInputManager>(relaxed = true)
    private val ttsManager = mockk<TextToSpeechManager>(relaxed = true)
    private val chatoGatewayRepository = mockk<ChatoGatewayRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { chatoGatewayRepository.hasToken() } returns true
        every { chatoGatewayRepository.gatewayUrl } returns "http://test"
        every { chatoGatewayRepository.gatewayToken } returns "token"
        every { chatoGatewayRepository.azureSpeechKey } returns "key"
        every { chatoGatewayRepository.azureSpeechRegion } returns "eastus"
        every { chatoGatewayRepository.transcriptionLanguage } returns "en-US"
        every { chatoGatewayRepository.graphToken } returns ""
        viewModel = MainViewModel(
            gatewayRepository, voiceInputManager, ttsManager, chatoGatewayRepository
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial isConversationMode is false`() {
        assertFalse(viewModel.uiData.value.isConversationMode)
    }

    @Test
    fun `startConversation sets isConversationMode true and state to Recording`() {
        coEvery { voiceInputManager.listenOnce() } coAnswers {
            // Block forever so loop stays in Recording
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            null
        }

        viewModel.startConversation()
        testDispatcher.scheduler.advanceTimeBy(10)

        assertTrue(viewModel.uiData.value.isConversationMode)
        assertEquals(UiState.Recording, viewModel.uiData.value.state)
    }

    @Test
    fun `stopConversation clears isConversationMode and sets Idle state`() {
        coEvery { voiceInputManager.listenOnce() } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            null
        }

        viewModel.startConversation()
        testDispatcher.scheduler.advanceTimeBy(10)

        viewModel.stopConversation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiData.value.isConversationMode)
        assertEquals(UiState.Idle, viewModel.uiData.value.state)
    }

    @Test
    fun `startConversation without token sets Error state`() {
        every { chatoGatewayRepository.hasToken() } returns false
        viewModel = MainViewModel(
            gatewayRepository, voiceInputManager, ttsManager, chatoGatewayRepository
        )

        viewModel.startConversation()

        assertFalse(viewModel.uiData.value.isConversationMode)
        assertTrue(viewModel.uiData.value.state is UiState.Error)
    }

    @Test
    fun `conversation loop null result stays in Recording without stopping`() = runTest {
        var callCount = 0
        coEvery { voiceInputManager.listenOnce() } coAnswers {
            callCount++
            if (callCount < 3) null else {
                // Stop after a few null results to break the loop
                viewModel.stopConversation()
                null
            }
        }

        viewModel.startConversation()
        advanceUntilIdle()

        assertTrue("Should have called listenOnce multiple times", callCount >= 2)
        assertFalse(viewModel.uiData.value.isConversationMode)
    }

    @Test
    fun `conversation loop speech goes through Thinking then Processing`() = runTest {
        val states = mutableListOf<UiState>()
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiData.collect { states.add(it.state) }
        }

        coEvery { voiceInputManager.listenOnce() } coAnswers { "hello" }
        coEvery { gatewayRepository.chat(any(), any()) } coAnswers {
            Result.success(ChatResponse(content = "hi there"))
        }
        coEvery { ttsManager.speak(any()) } coAnswers {
            // Stop conversation after first TTS to end loop
            viewModel.stopConversation()
        }

        viewModel.startConversation()
        advanceUntilIdle()

        // Should have seen Thinking and Processing in the sequence
        assertTrue("Thinking state expected", states.any { it is UiState.Thinking })
        assertTrue("Processing state expected", states.any { it is UiState.Processing })

        collectorJob.cancel()
    }

    @Test
    fun `listenOnce returning null does not add message to chat`() = runTest {
        var callCount = 0
        coEvery { voiceInputManager.listenOnce() } coAnswers {
            callCount++
            if (callCount < 2) null
            else {
                viewModel.stopConversation()
                null
            }
        }

        viewModel.startConversation()
        advanceUntilIdle()

        // No user messages should have been added
        assertTrue(viewModel.uiData.value.messages.isEmpty())
    }

    @Test
    fun `startConversation is idempotent when already active`() = runTest {
        coEvery { voiceInputManager.listenOnce() } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            null
        }

        viewModel.startConversation()
        advanceTimeBy(10)

        val jobRef = viewModel.uiData.value.isConversationMode
        viewModel.startConversation() // second call — should be no-op
        advanceTimeBy(10)

        // Still in conversation mode, no crash
        assertTrue(viewModel.uiData.value.isConversationMode)
    }
}
