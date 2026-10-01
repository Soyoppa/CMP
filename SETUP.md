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
| `SHEET_SCHEMA` | yes | `tracker_1` or `tracker_2` — the household sheet layout for the developer-only Sheets ledger. |
| `<schema>.SHEETS_GATEWAY_URL` | optional | `/exec` URL of the Sheets gateway (step 4). Builds without it never use the sheet — leave it out of store builds unless your granted accounts should reach the sheet there too. |

The build fails loudly when a required key is missing.

## 2. Firebase project

1. Enable **Authentication** → Email/Password (the Anonymous provider is no longer used).
2. Create a **Cloud Firestore** database, then deploy the rules:
   ```bash
   npx -y firebase-tools@latest deploy --only firestore:rules
   ```
3. Android: add an Android app with package `org.example.project`, download
   `google-services.json` into `composeApp/`.
4. Enable **Firebase AI Logic** (Gemini Developer API) and **Remote Config**
   (booleans `signup_enabled`, `chat_enabled`).
5. Strongly recommended before a public launch: enable **App Check** (Play Integrity on
   Android, App Attest on iOS, reCAPTCHA Enterprise on web) and enforce it for Firestore and
   AI Logic, so only your apps can spend your quota.

## 3. Where data lives

| Who | Ledger | Budgets / lists |
|---|---|---|
| Mobile without an account | This phone (app-private files on Android, NSUserDefaults on iOS) | This phone |
| Any account (mobile or web — the web always requires one) | `users/{uid}/transactions` in Firestore | `users/{uid}/budgets/{cut-off}` and `users/{uid}/settings/*` |
| Account with the developer Sheets switch on | Household Google Sheet via the gateway | Firestore, as above |

- Categories, income sources, payment modes and budgets start **empty** — users create, rename and delete their own.
- When a phone user signs in or creates an account, everything on the phone is uploaded to the account (lists merged, missing budgets copied, transactions written under their own ids so a retry never duplicates them), then cleared from the phone.
- Accounts re-read their data whenever the app returns to the foreground, so web and phone stay in sync.
- Budgets run per **cut-off** (the 1st–15th `YYYY-MM-1`, the 16th–end `YYYY-MM-2`) or per **month** (`YYYY-MM`), chosen in Settings → "How you budget" (`settings/preferences.budgetCycle`). Both sets are kept, so switching back restores the old figures.

## 4. Granting Sheets access (developer only)

The household sheet is a developer option, invisible to everyone else: the **Developer** section
in Settings (with the "Household sheet ledger" switch) only appears for accounts holding a grant.
It is reachable only through `apps-script/sheets-gateway.gs`, which verifies the caller's Firebase
ID token and an allow-list. To grant an account:

1. Find the user's **uid** in Firebase console → Authentication.
2. In Firestore, create document `users/{uid}/access/ledger` with field `source` = `"sheets"`.
   Clients can't write this path, so nobody can grant it to themselves.
3. Add the same uid to the gateway's `ALLOWED_UIDS` script property.
4. In the app, Settings → Developer → "Household sheet ledger" switches that account between the
   sheet and its own cloud ledger (stored at `users/{uid}/settings/developer`; on by default once granted).

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
