package org.example.project.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import org.example.project.util.UserFacingException

actual fun createAuthProvider(): AuthProvider = FirebaseAndroidAuthProvider()

/**
 * Firebase Auth via the native Android SDK (configured from `google-services.json`).
 * The SDK persists the session and refreshes ID tokens itself.
 */
internal class FirebaseAndroidAuthProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthProvider {

    override suspend fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthUser = firebaseCall {
        auth.signInWithEmailAndPassword(email, password).await().user.required()
    }

    override suspend fun signUp(email: String, password: String): AuthUser = firebaseCall {
        auth.createUserWithEmailAndPassword(email, password).await().user.required()
    }

    override suspend fun signInAnonymously(): AuthUser = firebaseCall {
        auth.signInAnonymously().await().user.required()
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun deleteCurrentUser() {
        firebaseCall {
            val user = auth.currentUser ?: throw UserFacingException("You're not signed in.")
            user.delete().await()
        }
    }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val user = auth.currentUser ?: return null
        return firebaseCall { user.getIdToken(forceRefresh).await().token }
    }

    private fun FirebaseUser?.required(): AuthUser =
        this?.toAuthUser() ?: throw UserFacingException("Authentication failed.")

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, isAnonymous = isAnonymous)

    /** Runs an SDK call, translating Firebase exceptions into friendly [UserFacingException]s. */
    private suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
        block()
    } catch (e: UserFacingException) {
        throw e
    } catch (e: FirebaseAuthException) {
        throw UserFacingException(friendlyAuthMessage(e.errorCode.removePrefix("ERROR_")))
    } catch (e: FirebaseNetworkException) {
        throw UserFacingException(friendlyAuthMessage("NETWORK"))
    } catch (e: FirebaseTooManyRequestsException) {
        throw UserFacingException(friendlyAuthMessage("TOO_MANY_ATTEMPTS"))
    } catch (e: Exception) {
        throw UserFacingException("Authentication failed.")
    }
}
