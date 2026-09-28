/*
 * Web Speech API bridge for voice transaction entry. Exposes
 * window.__voiceInput.{supported, listen, stop}; called from WebSpeechVoiceInputController
 * (wasmJsMain). One-shot recognition: listen() resolves with the final transcript (or an error
 * code) when the session ends, so the Kotlin side can await it like any other Promise.
 */
window.__voiceInput = {
  _rec: null,
  supported() {
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  },
  /** Returns Promise<JSON string> of { transcript, error }. error is "" on success. */
  listen(lang) {
    return new Promise((resolve) => {
      const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!Ctor) { resolve(JSON.stringify({ transcript: "", error: "unsupported" })); return; }
      let settled = false;
      const done = (payload) => {
        if (settled) return;
        settled = true;
        this._rec = null;
        resolve(JSON.stringify(payload));
      };
      const rec = new Ctor();
      this._rec = rec;
      rec.lang = lang || "en-US";
      rec.continuous = false;
      rec.interimResults = false;
      rec.maxAlternatives = 1;
      let finalText = "";
      rec.onresult = (e) => {
        for (let i = e.resultIndex; i < e.results.length; i++) {
          if (e.results[i].isFinal) finalText += e.results[i][0].transcript;
        }
      };
      rec.onerror = (e) => done({ transcript: finalText.trim(), error: (e && e.error) || "error" });
      rec.onend = () => {
        const t = finalText.trim();
        done({ transcript: t, error: t ? "" : "no-speech" });
      };
      try { rec.start(); } catch (err) { done({ transcript: "", error: String(err) }); }
    });
  },
  stop() {
    if (this._rec) { try { this._rec.stop(); } catch (e) {} }
  },
};
