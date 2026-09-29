# Household Finance Tracker

A personal finance tracker for Android, iOS and the web. Every account keeps its own private ledger in the cloud; households that already live in Google Sheets can keep using their sheet on the web.

---

## What it does

### Track every peso in and out
Log income and expenses in seconds (or by voice on the web). Each transaction captures the amount, description, category, payment mode, and whether it has been paid.

### Know exactly where your money goes
The **Spending Summary** screen breaks down monthly spending by category with a visual bar chart. Drag across the months to compare — January vs April, slow months vs heavy ones. Each category shows how much was spent against the monthly budget, and whether you are over or under.

### Ask the AI assistant
A built-in AI chat (powered by Gemini via Firebase) knows your budget and current month's spending. Ask it anything:
- *"Can we still spend on groceries this week?"*
- *"Which category went most over budget last month?"*
- *"How are we tracking compared to March?"*

It answers with your actual numbers, not generic finance advice.

### Your data, your account
Each account's transactions, budgets and lists live under its own Firestore path, protected by security rules. Accounts the owner grants Sheets access to read and write a household Google Sheet instead, through an authenticated Apps Script gateway — see [SETUP.md](SETUP.md).

### Guest preview
Share a read-only guest link so family members or a partner can explore the app before creating an account. Guest access is intentionally limited: they can browse and ask one AI question, but cannot add transactions or see sensitive diagnostics.

---

## Key capabilities

| Capability | Detail |
|---|---|
| **Transaction entry** | Amount, description, category, payment mode, paid toggle |
| **Budget tracking** | Per-category monthly budget vs actual, over/under indicator |
| **Spending summary** | Monthly bar chart + category breakdown with drag-to-select |
| **AI assistant** | Gemini chat (Firebase AI Logic) with your ledger as context — web and Android |
| **Data** | Firestore per account; optional household Google Sheet on the web |
| **Auth** | Email/password, anonymous guest mode, in-app account deletion, Remote Config kill-switches |
| **Platforms** | Android, iOS, Web, Desktop — one Kotlin Multiplatform codebase |

---

## Access control

Sign-up and guest mode can be turned on or off remotely from **Firebase Remote Config** without a new deployment — useful for private household use where you do not want open self-registration.

---

## How the data flows

```
App  →  Firebase Auth (ID token)
App  ↔  Cloud Firestore (REST, users/{uid}/…)            every account
App  ↔  Apps Script gateway (verifies token) ↔  Sheet    granted accounts only
App  →  Firebase AI Logic (Gemini)                        chat + voice categories
```

No custom server; everything runs on Firebase's free tier plus one Apps Script. See
[SETUP.md](SETUP.md) to configure and [RELEASE.md](RELEASE.md) for the store checklist.

---

