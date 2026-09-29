package org.example.project.data.ledger

import org.example.project.auth.AuthUser
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.firestore.string

/**
 * Decides which ledger a newly signed-in account uses.
 *
 * Every account gets its own cloud ledger ([LedgerSource.CLOUD]). The household Google Sheet
 * ([LedgerSource.SHEETS]) is an explicit grant, managed in the Firebase console — create
 *
 *     users/{uid}/access/ledger   { source: "sheets" }
 *
 * for each account that should keep using the sheet. `firestore.rules` makes that document
 * read-only for clients, so nobody can grant it to themselves. The grant applies on every
 * platform; the gateway independently checks the same accounts against its own allow-list.
 *
 * Any failure resolves to [LedgerSource.CLOUD] — the sheet is a privilege, so it fails closed.
 * So does a build without a gateway URL (e.g. a store build), where the sheet is unreachable.
 */
class LedgerAccessResolver(
    private val firestore: FirestoreRestClient,
    private val sheetsConfigured: () -> Boolean,
) {

    suspend fun resolve(user: AuthUser): LedgerSource {
        if (user.isAnonymous || !sheetsConfigured()) return LedgerSource.CLOUD
        val grant = runCatching { firestore.getDocument(accessPath(user.uid)) }.getOrNull()
        return if (grant?.string("source") == SHEETS_GRANT) LedgerSource.SHEETS else LedgerSource.CLOUD
    }

    companion object {
        const val SHEETS_GRANT = "sheets"
        fun accessPath(uid: String) = "users/$uid/access/ledger"
    }
}
