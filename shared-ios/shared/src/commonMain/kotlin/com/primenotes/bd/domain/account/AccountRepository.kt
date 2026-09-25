package com.primenotes.bd.domain.account

import com.primenotes.bd.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * What happened when someone tried to create an account.
 *
 * Results rather than exceptions, like [com.primenotes.bd.data.cloud.CloudProbe]:
 * every way this can fail has a shape the screen must deal with, and none of them can
 * quietly look like success.
 */
sealed interface SignUpResult {

    data object Created : SignUpResult

    /** The username is already someone else's. */
    data object UsernameTaken : SignUpResult

    /** The email is already on another account. */
    data object EmailTaken : SignUpResult

    /** The app refused the details before they were ever sent. */
    data class Rejected(val reason: String) : SignUpResult

    /** The request itself did not get an answer the app could use. */
    data class Failed(val reason: String) : SignUpResult
}

/** What happened when someone tried to sign in. */
sealed interface SignInResult {

    data object SignedIn : SignInResult

    /**
     * The identifier and password do not match an account.
     *
     * Deliberately one outcome rather than "no such user" and "wrong password": the
     * app should not become a way to find out which usernames exist.
     */
    data object InvalidCredentials : SignInResult

    data class Rejected(val reason: String) : SignInResult

    data class Failed(val reason: String) : SignInResult
}

/** What happened when a password reset code was asked for. */
sealed interface ResetRequestResult {

    /**
     * The code is on its way, and [identity] is the account it belongs to.
     *
     * The identity comes back because the code has to be *checked* against it later, and it is
     * derived from a username the app has no other way to reach from an email. It is not a secret:
     * it is the username somebody already knows, at a domain reserved for mail that never arrives.
     */
    data class Sent(val identity: String) : ResetRequestResult

    /**
     * No account uses that address.
     *
     * Answered honestly rather than opaquely, and it gives nothing away that the project does not
     * already give: `username_for_email` is callable by anyone holding an email, answers with a
     * username and never with an address, and has been documented as that since accounts were built.
     * Saying "no account uses that address" is better than sending somebody to wait for a code that
     * was never going to arrive.
     */
    data object NoAccount : ResetRequestResult

    /** The app refused what was typed before it was ever sent. */
    data class Rejected(val reason: String) : ResetRequestResult

    data class Failed(val reason: String) : ResetRequestResult
}

/** What happened when a reset code was checked. */
sealed interface ResetCodeResult {

    /** The code was right, and the session it came with is now this device's. */
    data object Accepted : ResetCodeResult

    /** Not the code that was sent, or not the one for this account. */
    data object Invalid : ResetCodeResult

    /** The code was right once, and is too old now. Ask for another. */
    data object Expired : ResetCodeResult

    data class Failed(val reason: String) : ResetCodeResult
}

/** What happened when the new password was set. */
sealed interface PasswordChangeResult {

    data object Changed : PasswordChangeResult

    /** The app refused it before it was sent. */
    data class Rejected(val reason: String) : PasswordChangeResult

    data class Failed(val reason: String) : PasswordChangeResult
}

/**
 * Accounts, as the rest of the app sees them.
 *
 * Signing up, in and out all need the network. **Being signed in does not** — the
 * session is stored, so an account never becomes a requirement for using the app.
 */
interface AccountRepository {

    /** The signed-in account, or null. Emits on every change, including sign-out. */
    val account: Flow<Account?>

    suspend fun signUp(username: String, email: String, password: String): SignUpResult

    /**
     * Signs in with either a username or an email — [identifier] decides which.
     *
     * The username is the account; the email is resolved to one first.
     */
    suspend fun signIn(identifier: String, password: String): SignInResult

    /**
     * Whether a name is still free, so the form can say so before creating anything.
     *
     * Only manners: sign-up itself is what actually enforces uniqueness, and a name
     * can always be taken by someone else between this answer and the request.
     */
    suspend fun isUsernameAvailable(username: String): Boolean

    /** Whether an email is already on another account. See [isUsernameAvailable]. */
    suspend fun isEmailAvailable(email: String): Boolean

    /**
     * Asks for a reset code to be sent to the address on the account that uses [email].
     *
     * A code rather than a link, because a link would need a deep link, an App Link and a hosted
     * page, and a six-digit code needs none of them — it is typed into this app.
     *
     * **It is not `resetPasswordForEmail`.** That sends its mail to the account's *identity*
     * address, and a Prime Notes identity is `alice@users.primenotes.invalid` — a domain reserved
     * precisely so that nothing can be delivered to it. The real address lives in the profile, and
     * only something holding the service key may read it, which is why this is a request to the
     * project's own function rather than to the auth service.
     */
    suspend fun requestPasswordReset(email: String): ResetRequestResult

    /**
     * Checks the code that arrived.
     *
     * On success this device holds a session for the account — which is what makes
     * [setNewPassword] possible, and is why signing out afterwards is a deliberate step rather than
     * an accident.
     */
    suspend fun verifyResetCode(identity: String, code: String): ResetCodeResult

    /** Sets the password of the account the verified code signed in. */
    suspend fun setNewPassword(password: String): PasswordChangeResult

    suspend fun signOut()
}
