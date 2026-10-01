# Store release checklist

What the code already covers is marked ✅. The rest needs your accounts, assets or a Mac.

## Must do before submitting

- [ ] **App ID / bundle ID.** Both are still the template `org.example.project`. Pick a real one
      (e.g. `com.yourname.treasurer`), then:
      Android — `applicationId`/`namespace` in `composeApp/build.gradle.kts`, register the new
      Android app in Firebase and replace `google-services.json`;
      iOS — `PRODUCT_BUNDLE_IDENTIFIER` and `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.
      The Kotlin package name can stay.
- [ ] **Signing (Android).** Create an upload key and `keystore.properties` in the repo root:
      ```properties
      storeFile=keystore/upload.jks
      storePassword=...
      keyAlias=upload
      keyPassword=...
      ```
      `bundleRelease` refuses to run without it. Enroll in Play App Signing.
- [ ] **Signing (iOS).** Set `TEAM_ID`, archive in Xcode on a Mac. iOS code compiles on Windows
      (`compileKotlinIosArm64`), but it has never been linked or run.
- [ ] **Firestore rules.** `npx -y firebase-tools@latest deploy --only firestore:rules`.
- [ ] **App Check** enforced for Firestore + AI Logic (see SETUP.md §2).
- [ ] **Privacy policy URL** (both stores require one) and a support email/URL.
- [ ] **Icons.** Android launcher icons are generated from the 300px `app_logo.png`; the iOS
      1024×1024 App Store icon and Play's 512×512 listing icon need a high-resolution logo.
- [ ] **Store listings.** Screenshots, description, Play Data safety form and App Store privacy
      labels (match `iosApp/iosApp/PrivacyInfo.xcprivacy`: email, user ID, financial info;
      linked to the user; no tracking). Say that an account is optional: without one, data stays
      on the device and is never sent anywhere.
- [ ] **Store build without the sheet.** Leave `<schema>.SHEETS_GATEWAY_URL` out of the release
      `local.properties` if the developer Sheets ledger shouldn't ship at all; with it, the switch
      still only appears for accounts granted in the Firebase console.
- [ ] **Version.** Bump `app.versionCode` (every upload) and `app.versionName` in
      `gradle.properties`; `CURRENT_PROJECT_VERSION`/`MARKETING_VERSION` in `Config.xcconfig`.

## Covered in code

- ✅ Mobile works without an account (everything stored on the phone); signing in later uploads it to the account.
- ✅ The web requires an account; every account has its own Firestore ledger, lists and budgets.
- ✅ No pre-filled categories, payment modes or budgets — users create, rename and delete their own.
- ✅ Household sheet is a developer-only switch: only accounts granted in the Firebase console see it, only via the authenticated Apps Script gateway; no Sheets API key in any build.
- ✅ Real sign-in on every platform (JS SDK web, native Android, REST + Keychain iOS).
- ✅ In-app account deletion that also erases the user's Firestore data (App Store 5.1.1(v), Play policy); no-account users can erase the phone's data from Settings.
- ✅ Android: R8 minify + resource shrink, cleartext disabled, system CAs only, no backup of app data,
  predictive back, adaptive icon, brand launch background.
- ✅ iOS: display name, export-compliance flag, launch screen, privacy manifest.
- ✅ Web: CSP without inline scripts, HSTS, frame-ancestors none, mic allowed for voice entry.

## Known gaps

- Phone-only data isn't backed up (Android backup is off on purpose), so uninstalling or losing the
  phone loses it — the Settings card nudges these users to create an account.
- The AI assistant is for accounts only; no-account users get local voice parsing without the Gemini fallback.

- iOS and desktop have no AI chat yet (the chat bubble is hidden there) and no voice entry.
- Android voice entry isn't wired (the mic button is hidden).
- Firestore lists are read in full each time; fine for personal ledgers, add paging/queries if
  users keep thousands of transactions.
