# Chato Voice

An Android voice assistant that lets you talk to [OpenClaw](https://github.com/openclaw/openclaw) hands-free. Push to talk, get a spoken response. Uses Android's built-in speech recognition and TTS engine — no third-party voice APIs.

---

## Features

- **Push-to-talk** — hold the mic button, speak, release
- **Screen context** — optional accessibility service reads the current screen and includes it in every request so the assistant has situational awareness
- **Text-to-speech responses** — responses are spoken back using Android TTS
- **Chat history** — scrollable conversation log in the main tab
- **Debug tab** — live log of gateway requests/responses for troubleshooting
- **Configurable gateway** — point it at any OpenClaw gateway URL + token via Settings
- **Material You** — dynamic color on Android 12+

---

## Requirements

- Android 8.0+ (API 26+)
- A running [OpenClaw](https://github.com/openclaw/openclaw) gateway (local or remote)
- OpenClaw gateway token

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

### 3. Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Setup

1. Install the APK on your device
2. Open the app — tap the **Settings** icon (top right)
3. Enter your **Gateway URL** (e.g. `http://192.168.1.x:18789`)
4. Enter your **Gateway Token**
5. Save — the app is ready to use

### Optional: Screen context

Enable the **Chato Voice** accessibility service in Android Settings → Accessibility to allow the app to read the current screen and include it as context in every request.

---

## Usage

- **Hold** the mic button at the bottom → speak
- **Release** → your speech is transcribed and sent to the gateway
- The assistant's response appears in the chat log and is spoken aloud
- **Long-press** any message to copy it to the clipboard

---

## Project Structure

```
app/src/main/java/com/nabla/chatovoice/
├── ChatoVoiceApp.kt                  # Application class (Hilt)
├── data/remote/
│   └── ChatoGatewayRepository.kt    # HTTP client for OpenClaw /v1/chat/completions
├── di/
│   └── AppModule.kt                 # Hilt dependency graph
├── domain/
│   ├── model/ChatMessage.kt         # Chat message model
│   └── repository/GatewayRepository.kt
├── service/
│   ├── ChatoAccessibilityService.kt # Screen context reader
│   ├── TextToSpeechManager.kt       # Android TTS wrapper
│   └── VoiceInputManager.kt        # SpeechRecognizer wrapper
└── ui/main/
    ├── MainActivity.kt
    ├── MainScreen.kt               # Chat + Debug tabs
    ├── MainViewModel.kt
    ├── SettingsDialog.kt           # Gateway URL + token config
    └── UiState.kt                  # Idle / Recording / Processing / Speaking / Error
```

---

## Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| HTTP | OkHttp |
| Async | Coroutines + StateFlow |
| Speech input | Android `SpeechRecognizer` |
| Speech output | Android `TextToSpeech` |
| Architecture | MVVM + Repository |

---

## Security Notes

- The gateway token is stored in `SharedPreferences` on-device
- No token is hardcoded — configure it via Settings on first launch
- The accessibility service only reads `windowContentDescription` and `packageName`; it does not log or transmit raw screen content independently

---

## Version

`2026.05.27.01` (versionCode `2026052701`)

---

## License

Private / personal use.
