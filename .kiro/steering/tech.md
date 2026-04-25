# Tech Stack

## Language & Framework
- **Kotlin** 2.2.20
- **Kotlin Multiplatform (KMP)** — single codebase targeting Android, iOS, Desktop (JVM), JS, WasmJS
- **Compose Multiplatform** 1.9.1 — shared UI across all targets
- **Compose Hot Reload** 1.0.0-rc02

## Key Libraries
| Library | Version | Purpose |
|---|---|---|
| Ktor Client | 3.0.3 | HTTP (Google Sheets API, Apps Script, Ollama) |
| kotlinx-serialization-json | 1.7.3 | JSON serialization (`@Serializable`) |
| kotlinx-datetime | 0.7.1 | Date handling in common code |
| kotlinx-coroutines | 1.10.2 | Async/coroutines |
| androidx.lifecycle (KMP) | 2.9.5 | ViewModel + StateFlow in common code |
| BuildConfig (gmazzo) | 5.5.0 | Injects `local.properties` values at build time |

## Platform-Specific Ktor Engines
- Android → `ktor-client-okhttp`
- JVM Desktop → `ktor-client-cio`
- JS / WasmJS → `ktor-client-js`

## Backend / External Services
- **Google Sheets API** — read transactions
- **Google Apps Script** — write transactions
- **Ollama** — local/remote LLM for AI chat (default model: `llama3.1:8b`)
- **Firebase** — Auth + Analytics (Android only, via `google-services.json`)

## Build System
- **Gradle** with Kotlin DSL (`build.gradle.kts`)
- Version catalog at `gradle/libs.versions.toml`
- Sensitive config lives in `local.properties` (gitignored) and is injected via `BuildConfig`

## Common Commands
```bash
# Build Android APK (debug)
./gradlew assembleDebug

# Run Desktop app
./gradlew :composeApp:run

# Build JS (browser)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Build WasmJS (browser)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Run all tests
./gradlew :composeApp:allTests

# Sync dependencies
./gradlew --refresh-dependencies
```

## Configuration
Secrets are stored in `local.properties` and must never be committed. Required keys:
```
SPREADSHEET_ID=
GOOGLE_API_KEY=
SHEET_RANGE=
SCRIPT_URL=
OLLAMA_URL=
OLLAMA_MODEL=
sdk.dir=
```
These are read at build time into `BuildConfig` and accessed via `ApiConfig` / `ConfigManager` in common code.
