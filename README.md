# Voice Assist AI

> System-wide voice dictation and contextual AI reply assistant for Android with a floating overlay, Quick Settings tile, and multi-provider LLM failover routing.

---

## Overview

**Voice Assist AI** is a native Android application built with Kotlin and Jetpack Compose. It provides system-wide assistance across any app on your device through a floating overlay pill, accessibility context detection, and a Quick Settings tile.

The application features a resilient **Multi-Provider AI Routing Engine** with primary endpoints and automatic fallback chains across multiple specialized AI categories (Real-time Reply Generator, Multimodal Screen/Chat Extractor, Rolling Chat Summarizer, and Whisper Speech-to-Text).

---

## Key Features

### 1. System-Wide Floating Overlay & Quick Settings
- **Floating Overlay (`SYSTEM_ALERT_WINDOW`)**: Accessible everywhere without leaving your active chat or app.
- **Quick Settings Tile (`VoiceAssistTileService`)**: Toggle or summon the overlay instantly from the Android notification shade.
- **Accessibility Service (`VoiceAccessibilityService`)**: Reads chat screen context and injects AI-generated replies directly into the focused input field.

### 2. Dual Assistant Modes
- **Smart Voice Dictation**: Streams audio recording to Whisper or OpenAI-compatible STT APIs for accurate speech-to-text.
- **Tone-Based Contextual Reply**: Analyzes chat history and context to generate tailored replies in customizable tones (Professional, Casual, Witty, Direct, Empathic, Flirty, etc.).

### 3. Multi-Category AI Roles & Failover Architecture
The application separates tasks into dedicated AI models:
- **Real-time Reply Generator**: Low-latency conversational models (e.g., Gemini 2.5 Flash, Claude 3.5 Haiku, Groq Llama 3.3, GPT-4o-mini).
- **Conversation Extractor**: Vision and OCR models that extract structured `<Sender>: <Message>` chat bubbles from screenshots.
- **Chat Summarizer**: Context compression models that maintain an updated Relationship Summary over time.
- **Whisper / Audio Dictation**: Speech-to-text models (e.g., OpenAI `whisper-1`, Groq `whisper-large-v3`).

### 4. Resilient Fallback Routing & 1-Tap Balanced Setup
- **Primary & Fallback Chains**: If a primary provider encounters rate limits (HTTP 429), timeouts, or service disruptions, calls automatically cascade down the configured fallback chain.
- **1-Tap Balanced Pool**: Instantly configure a balanced multi-cloud chain:
  `Google Gemini 2.5 Flash -> Anthropic Claude 3.5 Haiku -> Groq Llama 3.3 -> DeepSeek Chat`
- **Path Normalization**: Seamlessly handles Google Gemini's OpenAI-compatible endpoint (`https://generativelanguage.googleapis.com/v1beta/openai`) alongside standard endpoints (`https://api.openai.com/v1`).
- **Local LLM Support**: Fully compatible with offline and local endpoints such as Ollama (`http://10.0.2.2:11434`) and LM Studio / vLLM (`http://10.0.2.2:1234/v1`).

### 5. Security & Privacy
- **Encrypted Storage**: Sensitive API keys are encrypted at rest using Android KeyStore (`EncryptedKeyStoreManager`).
- **Route Import & Export**: Easily backup, share, or import provider and fallback configurations via formatted JSON without leaking keys.

---

## Supported AI Providers

| Provider | Supported Models | Endpoint Format | Best Suited For |
| :--- | :--- | :--- | :--- |
| **Google Gemini** | `gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-pro` | `https://generativelanguage.googleapis.com/v1beta/openai` | Vision extraction, high speed, high rate limits |
| **Groq Cloud** | `llama-3.3-70b-versatile`, `whisper-large-v3` | `https://api.groq.com/openai/v1` | Ultra-low latency replies & fast audio transcription |
| **Anthropic Claude** | `claude-3-5-haiku-20241022`, `claude-3-5-sonnet-20241022` | Via OpenRouter / OpenAI Proxy | High-reasoning replies & nuanced conversational tones |
| **DeepSeek** | `deepseek-chat`, `deepseek-reasoner` | `https://api.deepseek.com/v1` | Cost-effective, high-throughput fallback |
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `whisper-1` | `https://api.openai.com/v1` | Standard reference completions & speech-to-text |
| **OpenRouter** | Any OpenRouter model ID | `https://openrouter.ai/api/v1` | Universal proxy access to hundreds of open/closed models |
| **Local / Offline** | `llama3.2`, `mistral`, `qwen2.5` | `http://10.0.2.2:11434` (Ollama) | 100% private, offline inference |

---

## Architecture

```
app/src/main/java/com/example/
├── MainActivity.kt                     # Main navigation and app entry point
├── service/
│   ├── OverlayService.kt               # Floating overlay lifecycle & window management
│   ├── VoiceAccessibilityService.kt    # Screen text reading & auto-input injection
│   └── VoiceAssistTileService.kt       # Quick Settings toggle tile
├── network/
│   └── OpenAiClient.kt                 # Normalized OpenAI/Gemini/Local LLM HTTP client
├── data/
│   ├── model/
│   │   ├── ModelCategoryConfig.kt      # Primary & Fallback configuration schemas & presets
│   │   ├── ProviderConfig.kt           # Legacy provider configurations
│   │   ├── Person.kt                   # Relationship & contact memory models
│   │   └── TonePreset.kt               # Conversational tone definitions
│   └── storage/
│       ├── AppPreferencesRepository.kt # Settings persistence & fallback chain manager
│       └── EncryptedKeyStoreManager.kt # Android KeyStore AES-GCM API key encryption
└── ui/
    ├── screens/
    │   ├── ProvidersScreen.kt          # AI Routes, Fallback chains, & 1-tap balance UI
    │   ├── PresetConfigurationScreen.kt# Presets catalog, custom presets & testing
    │   ├── SettingsScreen.kt           # System settings, permissions, and accessibility
    │   └── PersonaScreen.kt            # Custom persona and tone definitions
    └── theme/                          # Material Design 3 theming
```

---

## Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer recommended
- **JDK**: Java 17 or Java 21
- **Android SDK**: `minSdk 24`, `targetSdk 36`, `compileSdk 36`

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/voice-assist.git
   cd voice-assist
   ```

2. **Configure environment variables (Optional):**
   Copy `.env.example` to `.env` if you wish to configure default API keys at compile time:
   ```bash
   cp .env.example .env
   ```

3. **Build debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## Permissions Required

To take full advantage of Voice Assist, grant the following permissions when prompted:

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**: Required to display the floating pill and expanded control panel over active apps.
2. **Microphone (`RECORD_AUDIO`)**: Required for recording voice dictation and processing speech-to-text.
3. **Accessibility Service**: Required to read conversational context from the active chat window and inject generated replies automatically.
4. **Post Notifications (`POST_NOTIFICATIONS`)**: Required to keep the foreground overlay service responsive in the background.

---

## Configuration Export & Import Format

Voice Assist supports exporting and importing your entire AI routing setup as JSON. Sample schema:

```json
{
  "version": 3,
  "exportedAt": 1741160400000,
  "categories": {
    "reply": {
      "primaryBaseUrl": "https://generativelanguage.googleapis.com/v1beta/openai",
      "primaryModel": "gemini-2.5-flash",
      "fallbacks": [
        {
          "name": "Claude 3.5 Haiku",
          "baseUrl": "https://openrouter.ai/api/v1",
          "model": "anthropic/claude-3-5-haiku",
          "enabled": true
        },
        {
          "name": "Groq Llama 3.3 70B",
          "baseUrl": "https://api.groq.com/openai/v1",
          "model": "llama-3.3-70b-versatile",
          "enabled": true
        },
        {
          "name": "DeepSeek Chat",
          "baseUrl": "https://api.deepseek.com/v1",
          "model": "deepseek-chat",
          "enabled": true
        }
      ]
    }
  },
  "customPresets": []
}
```

---

## License

This project is licensed under the Apache 2.0 License.
