package org.example.project.data.settings

import org.example.project.data.config.CloudConfigStore
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.ledger.FirestoreLedgerDataSource
import org.example.project.data.sheets.SheetsAccessStore

/**
 * Deletes everything the app stored for an account in Firestore — required before deleting the
 * account itself (App Store guideline 5.1.1(v), Google Play account-deletion policy).
 *
 * Firestore has no recursive delete over REST, so each known document is removed individually.
 */
class AccountDataEraser(private val firestore: FirestoreRestClient) {

    suspend fun eraseAll(uid: String) {
        deleteCollection("users/$uid/${FirestoreLedgerDataSource.TRANSACTIONS}")
        deleteCollection(CloudConfigStore.budgetsPath(uid))
        // Option lists, the developer switch, and documents from earlier versions alike.
        deleteCollection(CloudConfigStore.settingsPath(uid))
        firestore.deleteDocument(SheetsAccessStore.grantPath(uid))
    }

    private suspend fun deleteCollection(path: String) {
        firestore.listDocuments(path).forEach { doc -> firestore.deleteDocument("$path/${doc.id}") }
    }
}
