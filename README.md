# TwinMind - AI Meeting Assistant

A voice recording app with transcription and summary generation for Android.

## Features

**1. Robust Audio Recording**
- Foreground service with persistent notification
- 30-second audio chunks with ~2s overlap
- Edge case handling: phone calls, audio focus, headset changes, low storage, silence detection
- Process death recovery

**2. Transcription Pipeline**
- Real-time transcription as chunks complete
- Mock + OpenAI Whisper implementations
- Automatic retry on failure

**3. Summary Generation**
- Streaming UI updates
- Structured output: Title, Summary, Action Items, Key Points
- Background resilience via WorkManager

## Tech Stack

- **Kotlin** + **Jetpack Compose** (100%)
- **MVVM** Architecture
- **Hilt** Dependency Injection
- **Room** Database
- **Retrofit/OkHttp** Networking
- **WorkManager** Background Processing
- **Coroutines + Flow** Async

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Android SDK (API 35)
- Min SDK: API 24 (Android 7.0)

### Setup

1. Open the project in Android Studio
2. Let Gradle sync complete
3. (Optional) Add your API key in `local.properties`:
   ```properties
   OPENAI_API_KEY=sk-your-key-here
   USE_MOCK_API=false
   ```
4. Build and run on a device/emulator

### Build APK
```bash
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
app/src/main/java/com/twinmind/app/
├── data/
│   ├── local/          # Room DB, DAOs, Entities
│   ├── remote/         # API services (Mock + OpenAI)
│   └── repository/     # Repository layer
├── di/                 # Hilt dependency injection
├── domain/model/       # Domain models
├── service/            # Foreground recording service
├── ui/
│   ├── dashboard/      # Meeting list screen
│   ├── recording/      # Recording screen
│   ├── summary/        # Summary display screen
│   ├── navigation/     # Compose navigation
│   └── theme/          # Material 3 dark theme
├── viewmodel/          # MVVM ViewModels
└── workers/            # WorkManager workers
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `OPENAI_API_KEY` | (empty) | OpenAI API key for Whisper + GPT |
| `USE_MOCK_API` | `true` | Use mock services (no API needed) |
