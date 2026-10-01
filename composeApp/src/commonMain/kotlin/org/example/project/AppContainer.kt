package org.example.project

import org.example.project.auth.AppUser
import org.example.project.auth.AuthProvider
import org.example.project.auth.Session
import org.example.project.auth.SessionRepository
import org.example.project.auth.createAuthProvider
import org.example.project.config.ConfigManager
import org.example.project.config.LedgerProfile
import org.example.project.data.ai.AiRepository
import org.example.project.data.config.CloudConfigStore
import org.example.project.data.device.DeviceData
import org.example.project.data.device.createDeviceStore
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.ledger.FirestoreLedgerDataSource
import org.example.project.data.ledger.LedgerSource
import org.example.project.data.settings.AccountDataEraser
import org.example.project.data.sheets.SheetDataSourceFactory
import org.example.project.data.sheets.SheetsAccessStore
import org.example.project.data.sheets.SheetsGatewayClient
import org.example.project.data.sync.DeviceDataUploader
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository

/**
 * The repositories of one session, all bound to where that session's data lives:
 *
 * | session                  | ledger            | lists & budgets |
 * |--------------------------|-------------------|-----------------|
 * | [AppUser.Device]         | this phone        | this phone      |
 * | [AppUser.Account]        | Firestore         | Firestore       |
 * | account with Sheets on   | household sheet   | Firestore       |
 *
 * Built once per session and shared by every screen, so an edit on one screen is seen by all.
 */
class SessionGraph(
    val user: AppUser,
    val profile: LedgerProfile,
    val ledger: LedgerRepository,
    val config: ConfigRepository,
)

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

    /** This phone's storage for people without an account; null on the web. */
    val deviceData: DeviceData? by lazy { createDeviceStore()?.let(::DeviceData) }

    val aiRepository: AiRepository by lazy { AiRepository() }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            auth = authProvider,
            device = deviceData,
            uploader = deviceData?.let { device ->
                DeviceDataUploader(
                    device = device,
                    cloudConfig = { uid -> CloudConfigStore(firestore, uid) },
                    cloudLedger = { uid -> FirestoreLedgerDataSource(firestore, uid) },
                )
            },
            sheetsAccess = SheetsAccessStore(
                firestore,
                gatewayConfigured = { ConfigManager.getConfig().sheetsGatewayUrl.isNotBlank() },
            ),
            eraser = AccountDataEraser(firestore),
        )
    }

    private var graph: SessionGraph? = null

    /** The active session's repositories (rebuilt whenever the session changes). */
    fun session(): SessionGraph {
        val user = Session.currentUser ?: error("No active session")
        graph?.takeIf { it.user == user }?.let { return it }
        return buildGraph(user).also { graph = it }
    }

    private fun buildGraph(user: AppUser): SessionGraph {
        val profile = LedgerProfile.forUser(user)
        return when (user) {
            AppUser.Device -> {
                val device = deviceData ?: error("Device sessions need device storage")
                SessionGraph(user, profile, LedgerRepository(device.ledger), ConfigRepository(device.config, profile))
            }
            is AppUser.Account -> {
                val ledger = if (user.ledgerSource == LedgerSource.SHEETS) {
                    SheetDataSourceFactory.create(SheetsGatewayClient(idToken = { authProvider.idToken(forceRefresh = false) }))
                } else {
                    FirestoreLedgerDataSource(firestore, user.uid)
                }
                SessionGraph(user, profile, LedgerRepository(ledger), ConfigRepository(CloudConfigStore(firestore, user.uid), profile))
            }
        }
    }
}
