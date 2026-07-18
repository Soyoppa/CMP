# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## What this project is

A personal finance tracker built with **Kotlin Multiplatform + Compose Multiplatform**, targeting Android, iOS, JVM desktop, JS, and wasmJS (web). The backing data store is **Google Sheets** — no traditional backend. AI features use **Firebase AI Logic (Gemini)** as the primary provider with **Ollama** as a local fallback.

There are two separate sheet schemas (`tracker_1` and `tracker_2`) that represent two different households/trackers. One build serves one schema at a time, controlled by a single `local.properties` key.

---

## Build commands

All commands run from the repo root. On Windows use `.\gradlew`.

```bash
# Web (wasmJS) — primary target for development
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Android debug APK
./gradlew :composeApp:assembleDebug

# Full build (all targets)
./gradlew :composeApp:build

# Deploy web app to Firebase Hosting
.\scripts\deploy-all.ps1
```

There are no unit tests in this project yet.

---

## local.properties — the single source of truth

All runtime config lives in `local.properties` (gitignored). **Never add fallbacks in code** — if a key is missing the build should fail loudly. The schema system uses a namespaced prefix: keys under `tracker_1.*` are only active when `SHEET_SCHEMA=tracker_1`.

```properties
SHEET_SCHEMA=tracker_1          # switches the entire active schema

# Shared across all schemas
GOOGLE_API_KEY=...
OLLAMA_URL=...
FIREBASE_API_KEY=...            # + other Firebase fields

# Schema-scoped keys (prefix = schema name)
tracker_1.SPREADSHEET_ID=...
tracker_1.SHEET_RANGE='Data Dump'!A:H
tracker_1.BUDGET_RANGE='Bugdet vs Expense'!A:N     # ← misspelling is intentional
tracker_1.Summary='Summary Trend'!A:M              # ← key is "Summary" (not SUMMARY)
tracker_1.SCRIPT_URL=...

tracker_2.SPREADSHEET_ID=...
tracker_2.SHEET_RANGE='Data Dump'!A:E
tracker_2.SCRIPT_URL=...
```

The `build.gradle.kts` reads these via `schemaProp(key)` which resolves `$activeSchema.$key` first, then falls back to the bare key.

---

## Critical sheet tab naming — do not "fix" these

Both misspellings exist in the live Google Sheets and **must be preserved exactly**:

| Config key | Actual tab name | Note |
|---|---|---|
| `BUDGET_RANGE` | `'Bugdet vs Expense'!A:N` | "Budget" is misspelled |
| `Summary` | `'Summary Trend'!A:M` | config key is short `Summary`, not `SUMMARY_RANGE` |

The `getBudget()` parser uses `takeWhile` (not filter) to stop at the first blank/summary row — rows below the budget block are unrelated tables that must not be read as categories.

The `getSummary()` parser filters out section-header rows by name: `income`, `expenses`, `savings`, `total`, `net`, `category` (case-insensitive) and all-blank rows.

---

## Architecture

### Config chain

```
local.properties
  └─ build.gradle.kts  (schemaProp + buildConfigField)
       └─ BuildConfig  (generated)
            └─ ApiConfig.kt  (typed accessors)
                 └─ ConfigManager.kt  (ApiConfiguration data class, injectable)
                      └─ Tracker1Repository / Tracker2Repository
```

### Repository layer

`SheetRepository` interface → `SheetRepositoryFactory` picks the implementation from `SHEET_SCHEMA` at runtime. Both repositories (`Tracker1Repository`, `Tracker2Repository`) use Ktor HTTP client to call the Google Sheets API v4 directly with a read-only API key. Writes go through a Google Apps Script web app (`apps-script/tracker_1.gs` / `tracker_2.gs`).

`TransactionRepository` is a thin facade the rest of the app calls — never call the sheet repositories directly from the UI layer.

### Schema differences

| Feature | tracker_1 | tracker_2 |
|---|---|---|
| Income/Expense split | ✓ | ✗ (signed amount) |
| Budget tab | ✓ (`getBudget()`) | ✗ (returns empty list) |
| Summary trend tab | ✓ (`getSummary()`) | ✗ (returns empty list) |
| AI analysis | ✓ | ✗ ("coming soon" state) |
| Paid toggle | ✓ | ✗ |

`SchemaFeatures.current()` gates UI elements — always check it rather than comparing schema strings in UI code.

### UI architecture — MVI

All screens follow unidirectional data flow: `UI Event → ViewModel → StateFlow → Compose`.

- `TransactionViewModel` + `TransactionFormReducer` — full MVI with sealed `Event`/`Effect`/`State`
- `AiViewModel` — chat; reads 'Data Dump' fresh on each message (no eager prefetch, no Budget/Summary tabs)
- `SummaryViewModel` — loads `getSummary()` and `getBudget()` in parallel; exposes `totalMonthlyBudget` for the chart reference line
- `AuthViewModel` — Firebase Auth (email/password + anonymous guest)

Screen composables are stateless — they observe the ViewModel's `StateFlow` and delegate all logic upward.

### Navigation

Three-tab floating pill nav in `App.kt`:

```kotlin
private enum class NavTab { SUMMARY, ADD, SETTINGS }
```

- `SUMMARY` → `SummaryScreen` — spending summary / trend (hidden when the schema has no analysis sheets, i.e. `SchemaFeatures.current().aiAnalysisAvailable = false`)
- `ADD` → `TransactionInputScreen` — add a transaction
- `SETTINGS` → `SettingsScreen` (file: `SettingsScreen.kt`) — account, appearance, AI config, diagnostics

The **AI chat is not a tab** — it lives in a floating, Messenger-style chat bubble (`ChatBubble`) anchored bottom-end above the nav pill, which opens the chat as an origin-aware modal (`ChatModal`, both in `ui/ChatLauncher.kt`) that scales up from the bubble's corner. The bubble shows when `featureFlags.chatEnabled`; the modal hosts the regular `ChatScreen` with an `onClose` collapse affordance. The `aiViewModel` is owned by `App()` so the conversation survives open/close cycles.

`FeatureFlagStore` holds remote flags loaded from Firebase Remote Config at startup.

### AI provider chain

```
AiRepository.chat()
  ├─ if Firebase configured → GeminiProvider (Firebase AI Logic, JS SDK bridge on web)
  └─ on failure → OllamaProvider (HTTP, configurable URL)
```

`AiUsageTracker` tracks per-session token usage. `AiPrefs` persists the user's preferred provider mode. The JS/wasmJS targets use a JavaScript bridge (`jsMain`) to call the Firebase AI Logic SDK since the Kotlin Firebase SDK doesn't support web yet.

### Auth

Firebase Auth gates the app. Three states: `Loading`, `SignedOut`, `Authenticated`. Authenticated includes a guest path (anonymous sign-in). Guests can view the chat (one free message) but cannot add transactions, use AI freely, or see diagnostics write actions. `Session` is a singleton `StateFlow<AuthState>`.

---

## Design system rules

**Colors — never hardcode hex values.** All colors must come from:
- `MaterialTheme.colorScheme.*` tokens for structural colors
- Named palette constants in `ui/theme/Color.kt` for brand colors: `GoldenYellow`, `SageGreen`, `AmberBrown`, `DarkForest`, `IncomeGreen`, `ExpenseTerracotta`

**Shapes — always use `AppShapes`** (`ui/theme/Shapes.kt`):
- `AppShapes.field` — inputs, chips, list rows (14dp)
- `AppShapes.card` — surface cards (20dp)
- `AppShapes.pill` — buttons, nav pill, badges (full round)

**Typography** — Material 3 type scale only. `Fraunces` (serif) for display/headline, `Inter` (sans) for body/UI. Defined in `ui/theme/Type.kt`.

**Components** — use `BounceSurface` (in `ui/components/`) instead of raw `Surface` or `Box + clickable` whenever a press-bounce interaction is needed.

---

## KMP platform notes

- `String.format` / `"%.1f".format(x)` — **not available in commonMain**. Use manual string building or `kotlin.math` for formatting numbers cross-platform.
- `Canvas` / `PathEffect.dashPathEffect` — available in commonMain via `androidx.compose.foundation.Canvas`.
- `rememberTextMeasurer` — available in commonMain.
- Platform `ViewModelProvider.kt` files exist for: `androidMain`, `jvmMain`, `jsMain`, `wasmJsMain`, `iosMain`. When adding a new `expect fun createXxxViewModel()`, all five must get an `actual` implementation.

---

## Apps Script web app

Writes go to Google Apps Script (`apps-script/tracker_1.gs`). After editing the script, deploy a **new version** of the **existing deployment** (not "New deployment") to keep the same `/exec` URL. The URL is stored in `local.properties` as `tracker_1.WRITE_SCRIPT_URL`.

`safeText()` in the script prefixes formula-injection characters with `'`. `SCRIPT_SECRET` is empty by default (no auth required).

---

## Summary / trend chart

`SummaryScreen` (file: `SummaryScreen.kt`) is the `SUMMARY` nav tab — a full screen (it was previously a `ModalBottomSheet` launched from the chat). It loads from the `'Summary Trend'` tab (not the Budget tab). The bar chart is pure Compose — no chart library. The budget reference line uses `Canvas + PathEffect.dashPathEffect`.

`SummaryViewModel` fetches `getSummary()` and `getBudget()` in parallel. `totalMonthlyBudget = budget.sumOf { it.monthlyBudget }` is the chart Y-axis ceiling and reference line.
