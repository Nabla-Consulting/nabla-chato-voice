# Chato Voice

An Android voice assistant and meeting transcriber that connects to an [OpenClaw](https://github.com/openclaw/openclaw) gateway. Talk to Chato hands-free via push-to-talk, or run live multi-speaker transcription sessions with Azure Cognitive Services.

---

## Features

### Chat (Push-to-Talk)
- **Push-to-talk** — hold the mic button, speak, release; response spoken aloud via Android TTS
- **Screen context** — optional accessibility service reads the current screen and includes it in every request
- **Markdown rendering** — Chato responses rendered with Markwon in the chat view
- **Chat history** — scrollable conversation log; long-press any message to copy
- **Debug tab** — live log of gateway requests/responses; long-press to copy all logs

### Transcription
- **Live multi-speaker transcription** — Azure Cognitive Services ConversationTranscriber with speaker diarization
- **Auto language detection** — detects Spanish (es-US) and English (en-US) automatically per utterance
- **Semantic segmentation** — utterances split by meaning rather than silence, reducing mid-sentence cuts
- **Per-speaker debounce** — 300 ms debounce per speaker; different speakers emit independently (no dropped utterances)
- **Context notes** — prepend meeting context (e.g. participant names) to improve accuracy via Azure PhraseList grammar
- **Markdown transcript toggle** — view transcript as plain text or formatted Markdown
- **Copy transcript** — copy as plain text or Markdown
- **AI summary** — send transcript to Chato gateway for structured meeting summary
- **Save to Obsidian** — save summary, transcript, or both to a configurable OneDrive-backed Obsidian folder
- **Persistent state** — transcript, context notes, summary, and folder preference survive app restarts

### General
- **Configurable gateway** — set OpenClaw URL + token via Settings dialog
- **Configurable Azure** — set Azure Speech API key + region via Settings
- **Material You** — dynamic color on Android 12+
- **Wake lock** — prevents screen/CPU sleep during active transcription sessions (4 h safety cap)

---

## Requirements

- Android 8.0+ (API 26+)
- A running [OpenClaw](https://github.com/openclaw/openclaw) gateway (local or remote via Tailscale)
- OpenClaw gateway token
- Azure Cognitive Services Speech resource (for transcription)

---

## Build

### 1. Clone

```bash
git clone https://github.com/Nabla-Consulting/nabla-chato-voice.git
cd nabla-chato-voice
```

### 2. Create `local.properties`

```properties
sdk.dir=C:\\path\\to\\Android\\Sdk
```

### 3. (Optional) Debug secrets

Create `app/secrets.properties` (git-ignored) to pre-fill credentials in debug builds:

```properties
DEBUG_GATEWAY_TOKEN=your_token_here
DEBUG_AZURE_SPEECH_KEY=your_azure_key_here
DEBUG_AZURE_SPEECH_REGION=westus
```

Without this file the app starts with empty credentials — configure them via Settings on first launch.

### 4. Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/com.nabla.chatovoice-debug.apk
```

---

## Setup

1. Install the APK on your device
2. Open the app → tap the **Settings** icon (top right)
3. Enter your **Gateway URL** (e.g. `http://192.168.1.x:18789`)
4. Enter your **Gateway Token**
5. Enter your **Azure Speech Key** and **Region** (for transcription)
6. Save

### Optional: Screen context (Chat tab)

Enable the **Chato Voice** accessibility service in Android Settings → Accessibility to allow the app to read the current screen and include it as context in Chato requests.

---

## Usage

### Chat tab
- **Hold** the mic button → speak → **release** to send
- Response appears in the chat log and is spoken aloud via Android TTS
- Long-press any bubble to copy the message

### Transcribe tab
- **Transcript** sub-tab: Start/Stop transcription; each utterance appears as a speaker bubble
- **Notes** sub-tab: Enter context notes (participant names, topic) to improve accuracy
- **Summary** sub-tab: Tap **Summarize** to send transcript to Chato; save result to Obsidian

### Debug tab
- Live request/response log; long-press to copy all entries

---

## Project Structure

```
app/src/main/java/com/nabla/chatovoice/
├── ChatoVoiceApp.kt                       # Application class (Hilt)
├── data/remote/
│   ├── ChatoGatewayRepository.kt          # HTTP client for OpenClaw /v1/chat/completions
│   └── MsalRepository.kt                  # MSAL for OneDrive/Graph token (Obsidian save)
├── di/
│   └── AppModule.kt                       # Hilt dependency graph
├── domain/
│   ├── model/ChatMessage.kt
│   └── repository/GatewayRepository.kt
├── service/
│   ├── ChatoAccessibilityService.kt       # Screen context reader (optional)
│   ├── TextToSpeechManager.kt             # Android TTS wrapper
│   ├── TranscriptionService.kt            # Foreground service — Azure ConversationTranscriber
│   ├── TranscriptionServiceConnection.kt  # Service bind helper
│   └── VoiceInputManager.kt              # SpeechRecognizer wrapper (PTT chat)
├── ui/main/
│   ├── MainActivity.kt
│   ├── MainScreen.kt                      # Chat + Debug tabs + PTT bar
│   ├── MainViewModel.kt
│   ├── SettingsDialog.kt                  # Gateway URL/token + Azure key/region
│   ├── TranscribeScreen.kt                # Transcript/Notes/Summary sub-tabs
│   ├── TranscribeViewModel.kt
│   └── UiState.kt                         # Idle / Recording / Processing / Speaking / Error
└── util/
    ├── DebugLogger.kt
    └── MarkdownUtils.kt
```

---

## Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| HTTP | OkHttp |
| Async | Coroutines + StateFlow |
| Transcription | Azure Cognitive Services Speech SDK 1.44.0 (ConversationTranscriber) |
| Speech output | Android `TextToSpeech` |
| Speech input (PTT) | Android `SpeechRecognizer` |
| Markdown | Markwon (core, ext-tables) |
| Auth (Obsidian) | MSAL 5.x |
| Architecture | MVVM + Repository |

---

## Security Notes

- The gateway token and Azure Speech key are stored in `SharedPreferences` on-device
- In debug builds, credentials can be pre-filled from `app/secrets.properties` (git-ignored)
- Release builds have no hardcoded credentials — configure via Settings
- The accessibility service reads window content descriptions only; it does not transmit raw screen data independently

---

## Version

`1.0`

---

## License

Private / personal use.
