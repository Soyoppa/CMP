package org.example.project.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.DeviceData
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.data.ledger.LedgerRecord
import org.example.project.data.settings.AccountDataEraser
import org.example.project.data.sheets.SheetsAccessStore
import org.example.project.data.sync.DeviceDataUploader
import org.example.project.data.sync.RecordImporter
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

class SessionRepositoryTest {

    private class FakeAuth(var signedIn: AuthUser? = null) : AuthProvider {
        var signOuts = 0
        override suspend fun currentUser() = signedIn
        override suspend fun signIn(email: String, password: String): AuthUser {
            if (password != "secret1") throw UserFacingException("Wrong email or password.")
            return AuthUser(uid = "u1", email = email, isAnonymous = false).also { signedIn = it }
        }
        override suspend fun signUp(email: String, password: String) = signIn(email, password)
        override suspend fun signOut() { signOuts++; signedIn = null }
        override suspend fun deleteCurrentUser() { signedIn = null }
        override suspend fun idToken(forceRefresh: Boolean) = signedIn?.let { "token" }
    }

    // Never reached: no gateway is configured and nothing here deletes an account.
    private val firestore = FirestoreRestClient(
        projectId = { "p" },
        idToken = { "t" },
        http = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
    )
    private val cloudRows = mutableListOf<LedgerRecord>()
    private val cloudConfig = DeviceConfigStore(InMemoryDeviceStore())

    private fun repository(auth: AuthProvider, device: DeviceData?) = SessionRepository(
        auth = auth,
        device = device,
        uploader = device?.let {
            DeviceDataUploader(it, cloudConfig = { cloudConfig }, cloudLedger = { RecordImporter { r -> cloudRows += r } })
        },
        sheetsAccess = SheetsAccessStore(firestore, gatewayConfigured = { false }),
        eraser = AccountDataEraser(firestore),
    )

    @AfterTest
    fun tearDown() {
        Session.end()
        Session.consumeNotice()
    }

    @Test
    fun theWebHasNoAccountFreeMode() = runTest {
        val sessions = repository(FakeAuth(), device = null)
        assertFalse(sessions.deviceModeAvailable)
        assertTrue(sessions.useWithoutAccount().isFailure)
        sessions.restore()
        assertEquals(SessionState.SignedOut, Session.state.value)
    }

    @Test
    fun choosingThePhoneIsRememberedAcrossLaunches() = runTest {
        val device = DeviceData(InMemoryDeviceStore())
        repository(FakeAuth(), device).useWithoutAccount().getOrThrow()
        assertEquals(AppUser.Device, Session.currentUser)

        Session.end()
        repository(FakeAuth(), device).restore() // next launch
        assertEquals(AppUser.Device, Session.currentUser)
    }

    @Test
    fun signingInMovesThePhonesDataIntoTheAccount() = runTest {
        val device = DeviceData(InMemoryDeviceStore())
        val sessions = repository(FakeAuth(), device)
        sessions.useWithoutAccount()
        device.ledger.addTransaction(Transaction(date = "9/20/2026", description = "Lunch", outflow = 250.0, category = ""))

        sessions.signIn("a@b.co", "secret1").getOrThrow()

        assertEquals(AppUser.Account(uid = "u1", email = "a@b.co"), Session.currentUser)
        assertEquals(listOf("Lunch"), cloudRows.map { it.description })
        assertFalse(device.hasData())
        assertFalse(device.isModeChosen())
        assertEquals("Moved 1 transaction from this phone to your account.", Session.notice.value)
    }

    @Test
    fun aWrongPasswordKeepsThePhoneSession() = runTest {
        val device = DeviceData(InMemoryDeviceStore())
        val sessions = repository(FakeAuth(), device)
        sessions.useWithoutAccount()
        assertEquals("Wrong email or password.", sessions.signIn("a@b.co", "nope").exceptionOrNull()?.message)
        assertEquals(AppUser.Device, Session.currentUser)
    }

    @Test
    fun leftoverGuestSessionsAreSignedOut() = runTest {
        val auth = FakeAuth(signedIn = AuthUser(uid = "anon", email = null, isAnonymous = true))
        repository(auth, DeviceData(InMemoryDeviceStore())).restore()
        assertEquals(1, auth.signOuts)
        assertEquals(SessionState.SignedOut, Session.state.value)
    }

    @Test
    fun erasingThePhoneReturnsToWelcome() = runTest {
        val device = DeviceData(InMemoryDeviceStore())
        val sessions = repository(FakeAuth(), device)
        sessions.useWithoutAccount()
        device.ledger.addTransaction(Transaction(date = "9/20/2026", description = "Lunch", outflow = 250.0, category = ""))

        sessions.eraseDeviceData().getOrThrow()

        assertFalse(device.hasData())
        assertEquals(SessionState.SignedOut, Session.state.value)
    }
}
