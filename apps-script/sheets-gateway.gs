/**
 * Sheets gateway — the only way the app reads or writes the household Google Sheet.
 *
 * Replaces the old API-key reads + open /exec writes. The spreadsheet can (and should) now be
 * PRIVATE: this script runs as the owner and serves only callers that present a valid Firebase
 * ID token for an allow-listed account. Nothing secret ships in the app.
 *
 * Setup (once per spreadsheet):
 *   1. Sheet -> Extensions -> Apps Script. Paste this file as Code.gs.
 *   2. Project Settings -> Script properties:
 *        SCHEMA            tracker_1 | tracker_2
 *        SHEET_TAB         tab the ledger lives in, e.g. Data Dump
 *        FIREBASE_API_KEY  the Firebase web API key (same as the app's FIREBASE_API_KEY)
 *        ALLOWED_UIDS      comma-separated Firebase uids allowed to use the sheet
 *      Use the same uids you granted in Firestore (users/{uid}/access/ledger).
 *   3. Deploy -> New deployment -> Web app: Execute as "Me", Who has access "Anyone".
 *      ("Anyone" only means the URL is reachable; every request is authenticated below.)
 *   4. Put the /exec URL in local.properties as <schema>.SHEETS_GATEWAY_URL.
 *   5. Share settings: remove "Anyone with the link" from the spreadsheet.
 *
 * Protocol: POST, body = JSON text { idToken, action, ... }. Responses are JSON:
 *   { success: true, rows: [[...], ...] }        for action "list"  (header row included)
 *   { success: true }                             for action "append"
 *   { success: false, error: "..." }              on any failure
 */

function doPost(e) {
  try {
    var req = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    var caller = verifyCaller(req.idToken);
    if (!caller) return jsonOut({ success: false, error: 'Unauthorized' });

    var props = PropertiesService.getScriptProperties();
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(props.getProperty('SHEET_TAB'));
    if (!sheet) return jsonOut({ success: false, error: 'Ledger tab not found' });

    if (req.action === 'list') {
      return jsonOut({ success: true, rows: sheet.getDataRange().getDisplayValues() });
    }
    if (req.action === 'append') {
      var row = props.getProperty('SCHEMA') === 'tracker_2' ? tracker2Row(req) : tracker1Row(req);
      if (!row) return jsonOut({ success: false, error: 'Invalid transaction' });
      sheet.appendRow(row);
      return jsonOut({ success: true });
    }
    return jsonOut({ success: false, error: 'Unknown action' });
  } catch (err) {
    // Don't echo internals to the client; details are in the Executions log.
    console.error(err);
    return jsonOut({ success: false, error: 'Server error' });
  }
}

/** Plain GET (e.g. someone opening the URL) reveals nothing. */
function doGet() {
  return jsonOut({ success: false, error: 'Unauthorized' });
}

/**
 * Validates the Firebase ID token server-side via Identity Toolkit (accounts:lookup rejects
 * forged/expired tokens) and checks the uid against ALLOWED_UIDS. Returns the uid or null.
 */
function verifyCaller(idToken) {
  if (!idToken || typeof idToken !== 'string') return null;
  var props = PropertiesService.getScriptProperties();
  var allowed = (props.getProperty('ALLOWED_UIDS') || '').split(',')
    .map(function (s) { return s.trim(); })
    .filter(function (s) { return s; });

  var resp = UrlFetchApp.fetch(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + props.getProperty('FIREBASE_API_KEY'),
    { method: 'post', contentType: 'application/json', payload: JSON.stringify({ idToken: idToken }), muteHttpExceptions: true }
  );
  if (resp.getResponseCode() !== 200) return null;
  var users = JSON.parse(resp.getContentText()).users || [];
  var uid = users.length ? users[0].localId : null;
  return uid && allowed.indexOf(uid) >= 0 ? uid : null;
}

/** tracker_1: Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks */
function tracker1Row(r) {
  if (!r.date || !r.description) return null;
  return [
    safeText(r.date),
    safeText(r.description),
    safeNumber(r.inflow),
    safeNumber(r.outflow),
    safeText(r.category),
    safeText(r.modeOfPayment),
    r.isPaid === true ? 'TRUE' : 'FALSE',
    ''
  ];
}

/** tracker_2: Date | Description | Amount (signed, "₱") | Credit Card | c/o */
function tracker2Row(r) {
  var amt = Number(r.amount);
  if (!r.date || !r.description || isNaN(amt)) return null;
  return [
    safeText(r.date),
    safeText(r.description),
    safeText((amt < 0 ? '-₱' : '₱') + Math.abs(amt).toLocaleString('en-US')),
    safeText(r.creditCard),
    safeText(r.careOf)
  ];
}

/**
 * Neutralizes spreadsheet formula injection: a value starting with = + - @ (or a control char)
 * is prefixed with an apostrophe so Sheets stores it as text. Also clamps length.
 */
function safeText(v) {
  var s = (v == null) ? '' : String(v);
  if (s.length > 1000) s = s.substring(0, 1000);
  if (/^[=+\-@\t\r\n]/.test(s)) s = "'" + s;
  return s;
}

/** Coerces numeric fields to real numbers (also blocks formula injection via amounts). */
function safeNumber(v) {
  if (v == null || v === '') return '';
  var n = Number(String(v).replace(/,/g, ''));
  return isNaN(n) ? '' : n;
}

function jsonOut(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
