// Firebase JS SDK bridges for the Kotlin/wasmJs app (loaded as an ES module from index.html).
// Kept out of index.html so the Content-Security-Policy can forbid inline scripts.

const SDK = "https://www.gstatic.com/firebasejs/12.13.0";

/*
 * Firebase AI Logic bridge for the Kotlin/wasmJs app.
 * Exposes window.__financeAi.{init, generate}; called from FirebaseAiProvider (wasmJsMain).
 * Uses the Gemini Developer API backend (free tier). The Gemini key stays server-side —
 * only the non-secret Firebase web config is passed in from the app at runtime.
 */
window.__financeAi = {
  _ai: null,
  _lib: null,
  _ready: null,
  /** Lazily loads firebase/app + firebase/ai and initialises the AI Logic backend. */
  init(configJson) {
    if (this._ready) return this._ready;
    const config = JSON.parse(configJson);
    this._ready = (async () => {
      const appMod = await import(`${SDK}/firebase-app.js`);
      const aiMod = await import(`${SDK}/firebase-ai.js`);
      this._lib = aiMod;
      // Share a single Firebase app instance with the auth bridge.
      const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(config);
      this._ai = aiMod.getAI(app, { backend: new aiMod.GoogleAIBackend() });
      // Ready — no console.log; avoid leaking SDK/backend hints to devtools.
      return true;
    })().catch((e) => {
      console.error("[financeAi] init failed", e);
      this._ready = null; // allow a later retry
      throw e;
    });
    return this._ready;
  },
  /** One budget-aware chat turn. Returns JSON: { text, promptTokens, responseTokens, totalTokens }. */
  async generate(model, systemPrompt, historyJson, userMessage) {
    if (!this._ready) throw new Error("financeAi not initialized");
    await this._ready;
    const history = JSON.parse(historyJson);
    const genModel = this._lib.getGenerativeModel(this._ai, {
      model: model,
      systemInstruction: systemPrompt,
    });
    const chat = genModel.startChat({ history: history });
    const result = await chat.sendMessage(userMessage);
    const resp = result.response;
    const usage = (resp && resp.usageMetadata) || {};
    return JSON.stringify({
      text: resp.text(),
      promptTokens: usage.promptTokenCount || 0,
      responseTokens: usage.candidatesTokenCount || 0,
      totalTokens: usage.totalTokenCount || 0,
    });
  },
};

/*
 * Firebase Auth bridge. Exposes window.__financeAuth.{init, signIn, signUp, guest, signOut,
 * idToken, deleteUser}; called from FirebaseJsAuthProvider (wasmJsMain). Email/Password + Anonymous (guest) providers,
 * with browserLocalPersistence so sessions survive restarts until explicit sign-out.
 */
window.__financeAuth = {
  _auth: null,
  _lib: null,
  _ready: null,
  _ensure(configJson) {
    if (this._ready) return this._ready;
    const config = JSON.parse(configJson);
    this._ready = (async () => {
      const appMod = await import(`${SDK}/firebase-app.js`);
      const authMod = await import(`${SDK}/firebase-auth.js`);
      this._lib = authMod;
      const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(config);
      this._auth = authMod.getAuth(app);
      try { await authMod.setPersistence(this._auth, authMod.browserLocalPersistence); } catch (e) {}
      return true;
    })();
    return this._ready;
  },
  _user(u) {
    return JSON.stringify({
      signedIn: !!u,
      email: (u && u.email) || null,
      isGuest: !!(u && u.isAnonymous),
      uid: (u && u.uid) || null,
    });
  },
  _friendly(e) {
    const code = (e && e.code) || "";
    const map = {
      "auth/invalid-credential": "Wrong email or password.",
      "auth/invalid-email": "That email looks invalid.",
      "auth/user-not-found": "No account with that email.",
      "auth/wrong-password": "Wrong email or password.",
      "auth/email-already-in-use": "That email is already registered.",
      "auth/weak-password": "Password must be at least 6 characters.",
      "auth/too-many-requests": "Too many attempts — try again later.",
      "auth/operation-not-allowed": "Email/Password sign-in isn't enabled in Firebase.",
      "auth/admin-restricted-operation": "Guest mode (Anonymous auth) isn't enabled in Firebase.",
      "auth/requires-recent-login": "For your security, sign out, sign back in, and try again.",
      "auth/network-request-failed": "No connection — check your network and try again.",
    };
    // Do NOT fall back to e.message — it can carry internal Firebase details
    // (including the submitted email address in some SDK versions).
    return map[code] || "Authentication failed.";
  },
  // Resolves once the persisted auth state is known (first onAuthStateChanged emission).
  async init(configJson) {
    await this._ensure(configJson);
    const authMod = this._lib, auth = this._auth;
    const user = await new Promise((resolve) => {
      const unsub = authMod.onAuthStateChanged(auth, (u) => { unsub(); resolve(u); });
    });
    return this._user(user);
  },
  async signIn(email, password) {
    await this._ready;
    try {
      const cred = await this._lib.signInWithEmailAndPassword(this._auth, email, password);
      return this._user(cred.user);
    } catch (e) { throw new Error(this._friendly(e)); }
  },
  async signUp(email, password) {
    await this._ready;
    try {
      const cred = await this._lib.createUserWithEmailAndPassword(this._auth, email, password);
      return this._user(cred.user);
    } catch (e) { throw new Error(this._friendly(e)); }
  },
  async guest() {
    await this._ready;
    try {
      const cred = await this._lib.signInAnonymously(this._auth);
      return this._user(cred.user);
    } catch (e) { throw new Error(this._friendly(e)); }
  },
  async signOut() {
    await this._ready;
    await this._lib.signOut(this._auth);
    return "ok";
  },
  /** The signed-in user's ID token for Firestore REST calls, or "" when signed out. */
  async idToken(forceRefresh) {
    await this._ready;
    const u = this._auth.currentUser;
    return u ? await u.getIdToken(!!forceRefresh) : "";
  },
  /** Permanently deletes the signed-in account (caller re-authenticates first). */
  async deleteUser() {
    await this._ready;
    const u = this._auth.currentUser;
    if (!u) throw new Error("You're not signed in.");
    try {
      await this._lib.deleteUser(u);
      return "ok";
    } catch (e) { throw new Error(this._friendly(e)); }
  },
};

/*
 * Firebase Remote Config bridge. Exposes window.__financeFlags.get(configJson);
 * called from RemoteConfigRepository (wasmJsMain). Acts as a remote kill-switch for UI
 * features (sign-up, guest mode, chat). Defaults below are the "fail-open" values used
 * before the first successful fetch or if Remote Config is unreachable.
 */
const DEFAULTS = { signup_enabled: true, guest_mode_enabled: true, chat_enabled: true };
window.__financeFlags = {
  _rc: null,
  _lib: null,
  _ready: null,
  _ensure(configJson) {
    if (this._ready) return this._ready;
    const config = JSON.parse(configJson);
    this._ready = (async () => {
      const appMod = await import(`${SDK}/firebase-app.js`);
      const rcMod = await import(`${SDK}/firebase-remote-config.js`);
      this._lib = rcMod;
      const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(config);
      const rc = rcMod.getRemoteConfig(app);
      // Kill-switch latency: how stale a cached value may be before a refetch. 10 min.
      rc.settings.minimumFetchIntervalMillis = 600000;
      rc.defaultConfig = DEFAULTS;
      this._rc = rc;
      return true;
    })();
    return this._ready;
  },
  /** Fetches+activates, then returns JSON of the boolean flags. Falls back to DEFAULTS on error. */
  async get(configJson) {
    try {
      await this._ensure(configJson);
      await this._lib.fetchAndActivate(this._rc);
      return JSON.stringify({
        signup_enabled: this._lib.getValue(this._rc, "signup_enabled").asBoolean(),
        guest_mode_enabled: this._lib.getValue(this._rc, "guest_mode_enabled").asBoolean(),
        chat_enabled: this._lib.getValue(this._rc, "chat_enabled").asBoolean(),
      });
    } catch (e) {
      console.error("[financeFlags] fetch failed, using defaults", e);
      return JSON.stringify(DEFAULTS);
    }
  },
};
