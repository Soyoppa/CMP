# Setup

All deployment-specific values live in **`local.properties`** (build-time) and
**`composeApp/google-services.json`** (Android Firebase config). Both are gitignored.
Release signing lives in **`keystore.properties`** (also gitignored) — see [RELEASE.md](RELEASE.md).

## 1. Local config

```bash
cp local.properties.example local.properties
```

| Key | Required | What it is |
|---|---|---|
| `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_STORAGE_BUCKET`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID` | yes | Firebase console → Project settings → Web app `firebaseConfig`. Not secret: access is enforced by Firebase Auth + `firestore.rules`. |
| `GEMINI_MODEL` | yes | Firebase AI Logic model, e.g. `gemini-2.0-flash`. |
| `SHEET_SCHEMA` | yes | `tracker_1` or `tracker_2` — the household sheet layout for accounts granted Sheets access. |
| `<schema>.SHEETS_GATEWAY_URL` | web only | `/exec` URL of the Sheets gateway (step 4). Leave out for store builds. |

The build fails loudly when a required key is missing.

## 2. Firebase project

1. Enable **Authentication** → Email/Password and Anonymous providers.
2. Create a **Cloud Firestore** database, then deploy the rules:
   ```bash
   npx -y firebase-tools@latest deploy --only firestore:rules
   ```
3. Android: add an Android app with package `org.example.project`, download
   `google-services.json` into `composeApp/`.
4. Enable **Firebase AI Logic** (Gemini Developer API) and **Remote Config**
   (booleans `signup_enabled`, `guest_mode_enabled`, `chat_enabled`).
5. Strongly recommended before a public launch: enable **App Check** (Play Integrity on
   Android, App Attest on iOS, reCAPTCHA Enterprise on web) and enforce it for Firestore and
   AI Logic, so only your apps can spend your quota.

## 3. Where data lives

| Who | Ledger | Budgets / lists |
|---|---|---|
| Every account (store users) | `users/{uid}/transactions` in Firestore | `users/{uid}/settings/*` |
| Accounts granted Sheets access (web only) | Household Google Sheet via the gateway | `users/{uid}/settings/*` |
| Guests | Built-in demo data (read-only) | — |

## 4. Granting Sheets access (web)

The household sheet is reachable only through `apps-script/sheets-gateway.gs`, which verifies the
caller's Firebase ID token and an allow-list. To grant an account:

1. Find the user's **uid** in Firebase console → Authentication.
2. In Firestore, create document `users/{uid}/access/ledger` with field `source` = `"sheets"`.
   Clients can't write this path, so nobody can grant it to themselves.
3. Add the same uid to the gateway's `ALLOWED_UIDS` script property.

Deploying the gateway (once per spreadsheet) is described at the top of
`apps-script/sheets-gateway.gs`. After updating the script (e.g. to get the `delete` action used by
the Transactions screen), deploy a **new version of the existing deployment** so the `/exec` URL
stays the same. Afterwards, remove "Anyone with the link" sharing from the sheet —
the app no longer uses a Sheets API key.

## 5. Build & deploy the web app

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
.\scripts\deploy-all.ps1            # builds + deploys tracker_1 and tracker_2 hosting targets
```

## 6. Tests

```bash
./gradlew :composeApp:jvmTest
```
