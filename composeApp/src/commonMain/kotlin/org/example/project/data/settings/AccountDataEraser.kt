package org.example.project.data.settings

import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.ledger.LedgerAccessResolver

/**
 * Deletes everything the app stored for an account in Firestore — required before deleting the
 * account itself (App Store guideline 5.1.1(v), Google Play account-deletion policy).
 *
 * Firestore has no recursive delete over REST, so each known document is removed individually.
 */
class AccountDataEraser(private val firestore: FirestoreRestClient) {

    suspend fun eraseAll(uid: String) {
        val transactions = "users/$uid/transactions"
        firestore.listDocuments(transactions).forEach { doc ->
            firestore.deleteDocument("$transactions/${doc.id}")
        }
        UserSettingsStore.ALL_DOCS.forEach { docId ->
            firestore.deleteDocument(UserSettingsStore.path(uid, docId))
        }
        firestore.deleteDocument(LedgerAccessResolver.accessPath(uid))
    }
}
