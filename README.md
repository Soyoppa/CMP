# Household Finance Tracker

A personal finance tracking app built for households who already live in Google Sheets — no new database, no subscription, no data migration. Your spreadsheet stays the source of truth; this app is the interface on top of it.

---

## What it does

### Track every peso in and out
Log income and expenses in seconds. Each transaction captures the amount, description, category, payment mode, and whether it has already left your account. The data writes directly to your existing Google Sheet — open the sheet and the row is already there.

### Know exactly where your money goes
The **Spending Summary** screen breaks down monthly spending by category with a visual bar chart. Drag across the months to compare — January vs April, slow months vs heavy ones. Each category shows how much was spent against the monthly budget, and whether you are over or under.

### Ask the AI assistant
A built-in AI chat (powered by Gemini via Firebase) knows your budget and current month's spending. Ask it anything:
- *"Can we still spend on groceries this week?"*
- *"Which category went most over budget last month?"*
- *"How are we tracking compared to March?"*

It answers with your actual numbers, not generic finance advice.

### Built for two households
Two separate sheet schemas (`tracker_1` and `tracker_2`) run from a single codebase, each with its own spreadsheet, categories, and feature set. One build serves one household — switch schemas in config. No code changes required.

### Guest preview
Share a read-only guest link so family members or a partner can explore the app before creating an account. Guest access is intentionally limited: they can browse and ask one AI question, but cannot add transactions or see sensitive diagnostics.

---

## Key capabilities

| Capability | Detail |
|---|---|
| **Transaction entry** | Amount, description, category, payment mode, paid toggle |
| **Budget tracking** | Per-category monthly budget vs actual, over/under indicator |
| **Spending summary** | Monthly bar chart + category breakdown with drag-to-select |
| **AI assistant** | Gemini-powered chat with live budget context; Ollama fallback for local/offline use |
| **Multi-household** | Two independent sheet schemas from one codebase |
| **Auth** | Email/password sign-in, anonymous guest mode, Firebase Remote Config kill-switches |
| **Platforms** | Android, Web (primary), iOS, Desktop — one codebase |
| **No backend** | Google Sheets is the database; writes go through Google Apps Script |

---

## Access control

Sign-up and guest mode can be turned on or off remotely from **Firebase Remote Config** without a new deployment — useful for private household use where you do not want open self-registration.

| Flag | Default | Effect when off |
|---|---|---|
| `signup_enabled` | on | Hides all "Create account" and "Sign up" entry points |
| `guest_mode_enabled` | on | Hides the guest / explore option on the login screen |
| `chat_enabled` | on | Removes the AI chat tab entirely |

---

## How the data flows

```
App  →  Google Apps Script (write)  →  Google Sheet
App  ←  Google Sheets API v4 (read) ←  Google Sheet
App  →  Firebase AI Logic (Gemini)  →  AI response
```

No proprietary backend. No monthly infrastructure cost beyond Firebase's free tier. The spreadsheet is always readable and editable directly — the app does not lock your data.

---

## Setup

See [SETUP.md](SETUP.md) for full configuration instructions including Google Sheets setup, Firebase project wiring, and `local.properties` keys.

For deployment to Firebase Hosting (web), run:
```powershell
.\scripts\deploy-all.ps1
```
