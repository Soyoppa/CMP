# Project Structure

## Root Layout
```
KotlinProject/
├── build.gradle.kts          # Root build file (plugin declarations only)
├── gradle/libs.versions.toml # Version catalog for all dependencies
├── local.properties          # Local secrets — never commit
├── composeApp/               # Single KMP module containing all source sets
└── .kiro/steering/           # AI steering rules
```

## Source Sets (`composeApp/src/`)
```
commonMain/     # Shared code for all platforms (UI, logic, models)
androidMain/    # Android-specific: MainActivity, OkHttp engine, Firebase
iosMain/        # iOS-specific: expect/actual implementations
jvmMain/        # Desktop-specific: CIO engine, Swing coroutines
jsMain/         # JS browser: Ktor JS engine
wasmJsMain/     # WasmJS browser: Ktor JS engine
webMain/        # Shared web utilities (JS + WasmJS)
commonTest/     # Shared tests
```

## commonMain Package Structure (`org.example.project`)
```
config/
  ApiConfig.kt          # Typed accessors for BuildConfig values
  Environment.kt        # Build-time injection with dev fallbacks
  ConfigManager.kt      # Runtime config resolution

model/
  Transaction.kt        # Core data class + TransactionCategory/PaymentMode enums

data/
  GoogleSheetsApi.kt    # Ktor HTTP — reads transactions from Google Sheets
  GoogleAppsScriptRepository.kt  # Ktor HTTP — writes via Apps Script
  AiRepository.kt       # Ktor HTTP — Ollama chat with transaction context

repository/
  TransactionRepository.kt  # Facade over GoogleSheetsApi + GoogleAppsScriptRepository

viewmodel/
  TransactionViewModel.kt   # Form state + transaction CRUD, uses StateFlow
  AiViewModel.kt            # Chat history + Ollama calls
  ViewModelProvider.kt      # expect fun — platform provides actual ViewModel instance

ui/
  TransactionInputScreen.kt # Main form screen
  ChatScreen.kt             # AI chat screen
  TestConnectionScreen.kt   # Debug/connectivity screen
  components/
    DatePickerDialog.kt
  state/
    TransactionUiState.kt   # Immutable UI state data class for TransactionViewModel
  theme/                    # Material3 theme setup

util/
  DateUtils.kt              # Date formatting helpers
  FormatUtils.kt            # Peso formatting (₱)

App.kt                      # Root @Composable — navigation between screens via state flags
Platform.kt                 # expect val platform — platform name string
```

## Architecture Pattern
- **MVVM** — ViewModels hold state, screens observe via `collectAsState()`
- **Repository pattern** — ViewModels talk to repositories, never directly to data sources
- **expect/actual** — used for `createTransactionViewModel()`, `createAiViewModel()`, and `Platform`
- UI state is split: `TransactionUiState` (StateFlow, async feedback) + `formState` (mutableStateOf, immediate field updates)
- Navigation is simple boolean state flags in `App.kt` (no navigation library)

## Config / Secrets Flow
```
local.properties → BuildConfig (build time) → ApiConfig → ConfigManager → repositories
```
Never hardcode secrets. Always add new config keys to `local.properties` + `BuildConfig` block in `composeApp/build.gradle.kts`.
