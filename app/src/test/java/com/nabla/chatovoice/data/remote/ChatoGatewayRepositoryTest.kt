package com.nabla.chatovoice.data.remote

import androidx.test.core.app.ApplicationProvider
import com.nabla.chatovoice.ui.main.TranscriptEntry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatoGatewayRepositoryTest {

    private lateinit var repo: ChatoGatewayRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo = ChatoGatewayRepository(context, okhttp3.OkHttpClient())
    }

    @Test
    fun `transcript roundtrip serialization`() {
        val entries = listOf(
            TranscriptEntry("10:00:01", "GUEST_1", "Hello this is a test."),
            TranscriptEntry("10:00:02", "GUEST_2", "Just a test response."),
        )
        // Write via the real API
        repo.transcriptEntries = entries

        // Read back
        val loaded = repo.transcriptEntries
        assertEquals(2, loaded.size)
        assertEquals("10:00:01", loaded[0].timestamp)
        assertEquals("GUEST_1", loaded[0].speakerId)
        assertEquals("Hello this is a test.", loaded[0].text)
        assertEquals("10:00:02", loaded[1].timestamp)
        assertEquals("GUEST_2", loaded[1].speakerId)
        assertEquals("Just a test response.", loaded[1].text)
    }

    @Test
    fun `default folder persists and restores`() {
        repo.defaultObsidianFolder = "Projects"
        assertEquals("Projects", repo.defaultObsidianFolder)
    }

    @Test
    fun `clearTranscriptData clears all three fields`() {
        repo.transcriptEntries = listOf(TranscriptEntry("10:00:01", "GUEST_1", "Test"))
        repo.summaryText = "Summary text"
        repo.contextNotes = "Context notes"

        repo.clearTranscriptData()

        assertEquals(emptyList<TranscriptEntry>(), repo.transcriptEntries)
        assertEquals("", repo.summaryText)
        assertEquals("", repo.contextNotes)
    }

    @Test
    fun `defaultObsidianFolder defaults to Research`() {
        // Fresh prefs — should default to Research
        assertEquals("Research", repo.defaultObsidianFolder)
    }
}
