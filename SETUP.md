# Fork-and-Deploy Setup

This project is designed so a new operator can clone the repo, point it at their own Google Sheet + Firebase project, and ship a personal web app deployment without editing source code.

All deployment-specific values live in **`local.properties`** (build-time) and **`composeApp/google-services.json`** (Firebase). Both are gitignored.

---

## 1. Clone and fill local config

```bash
git clone <this-repo>
cd KotlinProject
cp local.properties.example local.properties
cp composeApp/google-services.json.example composeApp/google-services.json
```

Edit `local.properties` and fill in. Schema-specific values are **namespaced** by their schema name (e.g. `tracker_1.SPREADSHEET_ID`) so both schemas' configs can sit side-by-side and you flip between them with a single line.

**Shared keys (one value, used by every schema):**

| Key | Where to get it |
|---|---|
| `SHEET_SCHEMA` | `tracker_1` or `tracker_2` — picks which schema is active for this build |
| `GOOGLE_API_KEY` | Google Cloud Console → APIs & Services → Credentials → API key with Sheets API enabled |
| `OLLAMA_URL` / `OLLAMA_MODEL` | Your Ollama endpoint and model name |

**Schema-specific keys (one block per schema, prefixed with the schema name):**

| Key | Where to get it |
|---|---|
| `<schema>.SPREADSHEET_ID` | Long ID in your Sheet URL: `docs.google.com/spreadsheets/d/`**`<this>`**`/edit` |
| `<schema>.SHEET_RANGE` | Tab + columns, e.g. `'Data Dump'!A:H` (tracker_1) or `'Charges'!A:E` (tracker_2) |
| `<schema>.SCRIPT_URL` / `<schema>.WRITE_SCRIPT_URL` | Apps Script Web App URL after deploying the writer script |
| `<schema>.WRITE_SPREADSHEET_ID` | Spreadsheet the Apps Script writes to (can equal `<schema>.SPREADSHEET_ID`) |

To switch between schemas later, change one line:
```properties
SHEET_SCHEMA=tracker_2   # was tracker_1
```
No commenting/uncommenting required — the active schema's namespaced keys are picked up automatically.

## 2. Create your Firebase project

1. Go to <https://console.firebase.google.com> → **Add project**.
2. In the project, **Add app → Android**, use package name `org.example.project`.
3. Download the generated `google-services.json` and replace `composeApp/google-services.json`.
4. Enable **Hosting**: `npx -y firebase-tools@latest init hosting` (or skip if you only deploy via CI).

Edit `.firebaserc` and replace `your-firebase-project-id` with your actual project ID:

```json
{
  "projects": {
    "default": "<your-firebase-project-id>"
  }
}
```

Or, leave `.firebaserc` alone and use aliases:

```bash
npx -y firebase-tools@latest use --add  # picks your project and stores an alias
```

## 3. Build and deploy the web app

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
npx -y firebase-tools@latest deploy --only hosting
```

The wasm bundle is built to `composeApp/build/dist/wasmJs/productionExecutable` — that path is already wired up in `firebase.json`.

## 4. Deploy the Apps Script writer

The app sends new rows to your Sheet through a Google Apps Script web app. Two templates ship with this repo:

- `apps-script/tracker_1.gs` — for `SHEET_SCHEMA=tracker_1`
- `apps-script/tracker_2.gs` — for `SHEET_SCHEMA=tracker_2`

Steps:

1. Open your Sheet → **Extensions → Apps Script**.
2. Replace the default `Code.gs` with the contents of the template matching your schema.
3. Edit the `SHEET_TAB` variable at the top to your tab name.
4. **Deploy → New deployment → Web app**: *Execute as: Me*, *Who has access: Anyone*.
5. Copy the deployed URL into `local.properties` as both `SCRIPT_URL` and `WRITE_SCRIPT_URL`.

## 5. Switching schemas

To deploy a credit-card-tracker instance instead of the default finance tracker, change one line in `local.properties`:

```properties
SHEET_SCHEMA=tracker_2
SHEET_RANGE='YourTabName'!A:E
```

The app picks the right parser at build time via `SheetRepositoryFactory`. No code edits needed.

## 6. Common gotchas

**"JSON parse failed — response not JSON" on add-transaction.**
The Apps Script web app returned an HTML page instead of running. Almost always one of:

- **Deployment access is wrong.** It must be `Anyone` — *not* `Anyone with Google account` (that one still requires sign-in and returns a login HTML page to unauthenticated callers). Fix: Apps Script editor → **Deploy → Manage deployments** → edit the active deployment → set *Who has access* to **Anyone** → redeploy.
- **URL is the editor URL, not the deployment URL.** `WRITE_SCRIPT_URL` must end in `/exec`. Anything ending in `/edit` or `/dev` is wrong.
- **Wrong script.** A credit-card fork pointing at the finance Apps Script (or vice versa) will write malformed rows. Each schema has its own template under `apps-script/`.

Sanity test: paste your `WRITE_SCRIPT_URL` into an **incognito** browser window. You should see a JSON response like `{"success":false,"error":"Missing required param: date"}`. If you see a Google sign-in page, deployment access is still wrong.

## 7. Rotate the old API key

If you forked from an upstream that previously committed `google-services.json` with a real Firebase API key, **rotate that key** in the Google Cloud Console — git history retains the leaked value even after the file is removed from tracking.

---

## Adding a new config key

1. Add the key to `local.properties.example` (with a comment).
2. Add a `buildConfigField` line in `composeApp/build.gradle.kts`.
3. Expose it on `ApiConfig` and surface it in `ConfigManager.ApiConfiguration`.

That's the only path. No `Environment.kt` indirection, no scattered defaults.
