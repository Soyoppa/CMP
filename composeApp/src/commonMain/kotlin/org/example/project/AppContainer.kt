package org.example.project

import org.example.project.auth.AuthProvider
import org.example.project.auth.AuthRepository
import org.example.project.auth.Session
import org.example.project.auth.createAuthProvider
import org.example.project.config.ConfigManager
import org.example.project.data.ai.AiRepository
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.ledger.FirestoreLedgerDataSource
import org.example.project.data.ledger.LedgerAccessResolver
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.settings.AccountDataEraser
import org.example.project.data.settings.UserSettingsStore
import org.example.project.data.sheets.SheetDataSourceFactory
import org.example.project.data.sheets.SheetsGatewayClient

/**
 * App-wide singletons, created lazily on first use. Keeps construction in one place so
 * repositories and ViewModels only declare what they need (and tests can pass fakes instead).
 */
object AppContainer {

    val authProvider: AuthProvider by lazy { createAuthProvider() }

    val firestore: FirestoreRestClient by lazy {
        FirestoreRestClient(
            projectId = { ConfigManager.getConfig().firebaseProjectId },
            idToken = { forceRefresh -> authProvider.idToken(forceRefresh) },
        )
    }

    val userSettings: UserSettingsStore by lazy { UserSettingsStore(firestore) }

    val cloudLedger: LedgerDataSource by lazy {
        FirestoreLedgerDataSource(firestore, currentUid = { Session.currentUser?.uid })
    }

    /** Only touched for accounts granted the Sheets ledger (web). */
    val sheetsLedger: LedgerDataSource by lazy {
        SheetDataSourceFactory.create(SheetsGatewayClient(idToken = { authProvider.idToken(forceRefresh = false) }))
    }

    val aiRepository: AiRepository by lazy { AiRepository() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            provider = authProvider,
            ledgerAccess = LedgerAccessResolver(firestore),
            dataEraser = AccountDataEraser(firestore),
        )
    }
}
