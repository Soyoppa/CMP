package org.example.project.data.ledger

import org.example.project.auth.AuthUser
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.string
import org.example.project.getPlatform

/**
 * Decides which ledger a newly signed-in account uses.
 *
 * Every account gets its own cloud ledger ([LedgerSource.CLOUD]). The household Google Sheet
 * ([LedgerSource.SHEETS]) is an explicit grant, managed in the Firebase console — create
 *
 *     users/{uid}/access/ledger   { source: "sheets" }
 *
 * for each account that should keep using the sheet. `firestore.rules` makes that document
 * read-only for clients, so nobody can grant it to themselves. The grant is honoured on the web
 * only; mobile builds always use the cloud ledger.
 *
 * Any failure resolves to [LedgerSource.CLOUD] — the sheet is a privilege, so it fails closed.
 */
class LedgerAccessResolver(private val firestore: FirestoreRestClient) {

    suspend fun resolve(user: AuthUser): LedgerSource {
        if (user.isAnonymous || !getPlatform().supportsSheetsLedger) return LedgerSource.CLOUD
        val grant = runCatching { firestore.getDocument(accessPath(user.uid)) }.getOrNull()
        return if (grant?.string("source") == SHEETS_GRANT) LedgerSource.SHEETS else LedgerSource.CLOUD
    }

    companion object {
        const val SHEETS_GRANT = "sheets"
        fun accessPath(uid: String) = "users/$uid/access/ledger"
    }
}
