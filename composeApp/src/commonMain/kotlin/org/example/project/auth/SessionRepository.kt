package org.example.project.auth

import org.example.project.data.device.DeviceData
import org.example.project.data.settings.AccountDataEraser
import org.example.project.data.sheets.SheetsAccessStore
import org.example.project.data.sync.DeviceDataUploader
import org.example.project.util.UserFacingException

/**
 * Starts, switches and ends sessions, publishing the result to [Session].
 *
 *  - Mobile: people can [useWithoutAccount] (data stays on the phone) or sign in / sign up. Signing
 *    in from a phone that holds data moves it into the account ([DeviceDataUploader]).
 *  - Web: there is no [device] storage, so an account is required.
 *
 * Every method returning [Result] fails with a [UserFacingException] message suitable for the UI.
 */
class SessionRepository(
    private val auth: AuthProvider,
    private val device: DeviceData?,
    private val uploader: DeviceDataUploader?,
    private val sheetsAccess: SheetsAccessStore,
    private val eraser: AccountDataEraser,
) {
    /** False on the web, where every session needs an account. */
    val deviceModeAvailable: Boolean get() = device != null

    /** Restores the previous session at startup, or lands on the welcome screen. */
    suspend fun restore() {
        val user = runCatching { auth.currentUser() }.getOrNull()
        when {
            user != null && !user.isAnonymous -> startAccount(user)
            else -> {
                // Anonymous "guest" sessions are retired; don't carry one over.
                if (user != null) runCatching { auth.signOut() }
                if (device?.isModeChosen() == true) Session.start(AppUser.Device) else Session.end()
            }
        }
    }

    /** True when this phone holds data that signing in would move to the account. */
    suspend fun hasDeviceData(): Boolean = runCatching { uploader?.hasPendingData() == true }.getOrDefault(false)

    suspend fun useWithoutAccount(): Result<Unit> = runCatching {
        val device = device ?: throw UserFacingException("Sign in to use the app on the web.")
        device.setModeChosen(true)
        Session.start(AppUser.Device)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { startAccount(auth.signIn(email.trim(), password)) }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        runCatching { startAccount(auth.signUp(email.trim(), password)) }

    suspend fun signOut() {
        runCatching { auth.signOut() }
        Session.end()
    }

    /**
     * Permanently deletes the account and all of its cloud data (App Store 5.1.1(v), Google Play
     * account-deletion policy). Firebase only deletes accounts that signed in recently, so the
     * [password] re-authenticates first — otherwise the data could be erased while the account
     * deletion itself is rejected.
     */
    suspend fun deleteAccount(password: String): Result<Unit> = runCatching {
        val user = Session.currentUser as? AppUser.Account ?: throw UserFacingException("You're not signed in.")
        val email = user.email ?: throw UserFacingException("This account can't be verified.")
        if (password.isEmpty()) throw UserFacingException("Enter your password to confirm.")
        auth.signIn(email, password) // re-authenticates; throws "Wrong email or password."
        eraser.eraseAll(user.uid)
        auth.deleteCurrentUser()
        Session.end()
    }

    /** Deletes everything stored on this phone and returns to the welcome screen. */
    suspend fun eraseDeviceData(): Result<Unit> = runCatching {
        val device = device ?: throw UserFacingException("There's nothing stored on this device.")
        device.clear()
        device.setModeChosen(false)
        Session.end()
    }

    /** The developer switch: read the ledger from the household sheet instead of the cloud. */
    suspend fun setSheetsEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val user = Session.currentUser as? AppUser.Account ?: throw UserFacingException("You're not signed in.")
        if (!user.sheets.granted) throw UserFacingException("This account can't use the household sheet.")
        sheetsAccess.setEnabled(user.uid, enabled)
        Session.start(user.copy(sheets = user.sheets.copy(enabled = enabled)))
    }

    private suspend fun startAccount(user: AuthUser) {
        device?.setModeChosen(false)
        val notice = moveDeviceData(user.uid)
        Session.start(AppUser.Account(uid = user.uid, email = user.email, sheets = sheetsAccess.load(user.uid)))
        notice?.let(Session::post)
    }

    /**
     * Uploads whatever the phone still holds. Never blocks signing in: on failure the data stays
     * on the phone and the upload runs again on the next launch.
     */
    private suspend fun moveDeviceData(uid: String): String? {
        val uploader = uploader ?: return null
        if (!hasDeviceData()) return null
        return runCatching { uploader.upload(uid) }.fold(
            onSuccess = { moved ->
                if (moved == 1) "Moved 1 transaction from this phone to your account."
                else "Moved your data from this phone to your account."
            },
            onFailure = { "Some data on this phone hasn't reached your account yet. It'll retry next time you open the app." },
        )
    }
}
