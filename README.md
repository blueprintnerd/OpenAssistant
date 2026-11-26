# OpenAssistant

An open-source, drop-in Android replacement for Google Assistant and Gemini that uses **zero Google servers** and **zero LLMs**.

OpenAssistant registers as a native Android `VoiceInteractionService` and `RecognitionService`, replicates the classic Google Assistant bottom-sheet overlay and 4-color voice bar, runs always-on wake-word detection ("Hey Google", "OK Google", or any custom phrase) completely on-device via Sherpa-ONNX, transcribes speech locally, and executes commands through a fast, deterministic intent engine.

## Key Features

- **Replaces Gemini as Android's Default Digital Assistant**
  - Implements `VoiceInteractionService`, `VoiceInteractionSessionService`, `RecognitionService`, and `ACTION_ASSIST` / `ACTION_VOICE_COMMAND`.
  - Works with power-button long press, corner swipe gesture, Bluetooth triggers, and always-on wake word.
- **100% On-Device Speech Pipeline (Sherpa-ONNX)**
  - **Wake Word (KWS)**: Open-vocabulary Zipformer keyword spotting (`sherpa-onnx-kws-zipformer-gigaspeech-3.3M`) with automatic SentencePiece BPE tokenization for `"HEY GOOGLE"`, `"OK GOOGLE"`, `"HEY ASSISTANT"`, or any custom English wake phrase.
  - **Speech-to-Text (ASR)**: Streaming Zipformer English transducer (`sherpa-onnx-streaming-zipformer-en-20M`) with real-time partial transcripts and automatic endpoint detection.
  - **Text-to-Speech (TTS)**: Optional offline Piper VITS neural voice (`vits-piper-en_US-lessac-medium-int8`) with automatic fallback to local Android TTS.
  - Built-in **Model Manager** downloads and verifies models directly from `k2-fsa/sherpa-onnx` GitHub Releases.
- **Deterministic Classic Assistant Command Engine (No LLM)**
  - **Device Controls**: Flashlight on/off, volume up/down/mute/percentage, battery status, Wi-Fi/Bluetooth/Display/Location/Sound settings panels, camera & selfie capture.
  - **Timers, Alarms, Calendar & Reminders**: Native `AlarmClock` timers and alarms, `CalendarContract` events, and local `AlarmManager` notification reminders.
  - **Phone, Messages & Apps**: Contact lookup and calling, SMS drafting, fuzzy app launcher for installed apps.
  - **Media & Navigation**: Media playback keys (play/pause/next/previous/stop), `MEDIA_PLAY_FROM_SEARCH`, and standard `geo:` navigation URIs (compatible with Organic Maps, OsmAnd, Magic Earth, etc.).
  - **Weather & Knowledge (Zero Google)**:
    - Live weather and 4-day forecast via [Open-Meteo](https://open-meteo.com/) (no API key required).
    - Calculator & spoken math evaluator (`15% of 85`, `what is 42 times 17`, `square root of 144`, `2^10`).
    - Offline unit converter (temperature, length, mass, volume, speed, data, time).
    - Dictionary definitions via [Free Dictionary API](https://dictionaryapi.dev/).
    - Factual instant summaries via Wikimedia REST API (Wikipedia) and DuckDuckGo Instant Answers.
    - Privacy-respecting web search fallback (DuckDuckGo, Brave Search, Startpage, or Kagi).
  - **Local Smart Home**: Direct LAN integration with **Home Assistant** REST API (`turn on living room lights`, `turn off fan`, scenes/scripts).
  - **Routines & Lists**: Multi-step voice routines (`Good morning`, `Good night`, or custom routines) and a local persistent shopping list.

## Replacing Gemini on Android

1. Install the OpenAssistant APK.
2. Open **OpenAssistant** and go to the **Models** tab to download the on-device **Wake Word** and **Speech-to-Text** models (and optionally **Piper Neural Voice**).
3. On the **Home** tab, grant Microphone and Notification permissions.
4. Tap **Open Default Digital Assistant Settings** (or go to `Settings -> Apps -> Default apps -> Digital assistant app`) and select **OpenAssistant**.
5. Toggle **Always-On Wake Word** on the **Home** tab and customize your wake phrases in the **Wake Word** tab.

## Building from Source

Prerequisites:
- JDK 17 or JDK 21
- Android SDK (compileSdk 35)
- Gradle 8.10+

```bash
git clone https://github.com/blueprintnerd/OpenAssistant.git
cd OpenAssistant
gradle testDebugUnitTest assembleDebug
```

The Gradle build automatically downloads `sherpa-onnx-2.0.3.aar` from `k2-fsa/sherpa-onnx` GitHub Releases during `preBuild` if it is not already present in `app/libs/`.

The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```text
app/src/main/java/com/blueprintnerd/openassistant/
├── OpenAssistantApp.kt                  # Application singletons & notification channels
├── data/
│   ├── AssistantDataStore.kt            # Storage interface for router testability
│   └── AssistantPreferences.kt          # SharedPreferences + JSON persistence (routines, lists, settings)
├── engine/
│   ├── AssistantResponse.kt             # Structured UI card & speech response models
│   ├── CommandRouter.kt                 # Deterministic zero-LLM command & intent router
│   ├── DeviceActionExecutor.kt          # Android system/hardware/intent actions
│   ├── MathEvaluator.kt                 # Recursive-descent & spoken math parser
│   ├── UnitConverter.kt                 # Offline multi-category unit converter
│   └── WebKnowledgeClient.kt            # Open-Meteo, Wikipedia, Dictionary, DDG, Home Assistant
├── service/
│   ├── BootReceiver.kt                  # Restarts wake-word listener after reboot
│   ├── OpenAssistantInteractionService.kt # VoiceInteractionService (replaces Gemini)
│   ├── OpenAssistantRecognitionService.kt # System RecognitionService backed by Sherpa-ONNX
│   ├── OpenAssistantSessionService.kt   # VoiceInteractionSessionService
│   ├── ReminderReceiver.kt              # Local reminder notifications
│   └── WakeWordForegroundService.kt     # Microphone foreground service for KWS
├── speech/
│   ├── BpeKeywordTokenizer.kt           # Converts wake phrases to Sherpa-ONNX BPE tokens
│   ├── LocalTtsEngine.kt                # Piper OfflineTts + Android local TTS fallback
│   ├── ModelManager.kt                  # Downloads & extracts Sherpa-ONNX .tar.bz2 models
│   ├── SherpaSttEngine.kt               # Streaming on-device ASR with endpointing
│   └── SherpaWakeWordEngine.kt          # Always-on Sherpa-ONNX KeywordSpotter
└── ui/
    ├── AssistActivity.kt                # Translucent Google Assistant-style bottom sheet overlay
    ├── MainActivity.kt                  # Hub: Setup, Models, Wake Word, Routines, Settings
    ├── components/AssistantLightBar.kt  # Animated 4-color voice bar & listening dots
    └── theme/Theme.kt                   # Material 3 theme & classic 4-color palette
```

## License

MIT License. See [LICENSE](LICENSE).
