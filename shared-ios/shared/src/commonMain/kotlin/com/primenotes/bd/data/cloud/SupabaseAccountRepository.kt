package com.primenotes.bd.data.cloud

import com.primenotes.bd.domain.account.AccountRepository
import com.primenotes.bd.domain.account.PasswordChangeResult
import com.primenotes.bd.domain.account.ResetCodeResult
import com.primenotes.bd.domain.account.ResetRequestResult
import com.primenotes.bd.domain.account.SignInResult
import com.primenotes.bd.domain.account.SignUpResult
import com.primenotes.bd.domain.account.Username
import com.primenotes.bd.domain.model.Account
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Accounts, on the real project.
 *
 * An account is created with a synthetic identity built from the username, so the
 * username is what people sign in with. The real email travels in the sign-up
 * metadata and is written to the profile by a database trigger inside the same
 * transaction — which is why a sign-up cannot half-succeed.
 *
 * Signing in with an email resolves it to a username first; signing in with a
 * username does not need to resolve anything.
 */
class SupabaseAccountRepository(private val client: SupabaseClient) : AccountRepository {

    override val account: Flow<Account?> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.toAccount()
            else -> null
        }
    }

    override suspend fun signUp(username: String, email: String, password: String): SignUpResult {
        val cleanUsername = Username.normalise(username)
        val cleanEmail = email.trim().lowercase()

        return try {
            client.auth.signUpWith(Email) {
                this.email = Username.identity(cleanUsername)
                this.password = password
                data = buildJsonObject {
                    put("username", cleanUsername)
                    put("contact_email", cleanEmail)
                }
            }
            SignUpResult.Created
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            rest.toSignUpResult()
        } catch (error: Exception) {
            SignUpResult.Failed(error.toCloudReason())
        }
    }

    override suspend fun signIn(identifier: String, password: String): SignInResult {
        val typed = identifier.trim()

        return try {
            val username = if (Username.isEmail(typed)) {
                // The email is only an alias; the account is the username behind it.
                usernameForEmail(typed) ?: return SignInResult.InvalidCredentials
            } else {
                Username.normalise(typed)
            }

            client.auth.signInWith(Email) {
                email = Username.identity(username)
                this.password = password
            }
            SignInResult.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            rest.toSignInResult()
        } catch (error: Exception) {
            SignInResult.Failed(error.toCloudReason())
        }
    }

    /**
     * Revokes the session where that is possible, and always forgets it here.
     *
     * A global sign-out needs the network; when there is none the local session is
     * dropped anyway, because the half that matters is that this device stops being
     * signed in.
     */
    override suspend fun signOut() {
        try {
            client.auth.signOut(SignOutScope.GLOBAL)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            runCatching { client.auth.signOut(SignOutScope.LOCAL) }
        }
    }

    override suspend fun isUsernameAvailable(username: String): Boolean =
        booleanRpc("username_available", Username.normalise(username))

    override suspend fun isEmailAvailable(email: String): Boolean =
        booleanRpc("email_available", email.trim().lowercase())

    /**
     * Asks the project's own function to mail a reset code.
     *
     * Only the username is sent. The function reads the real address out of the profile with the
     * service key, which is the one thing this device may not do — and which is why the mail can
     * reach somebody at all: the account's identity address is at a domain reserved for mail that
     * never arrives.
     */
    override suspend fun requestPasswordReset(email: String): ResetRequestResult {
        return try {
            // The address is only an alias; the account is the username behind it — the same
            // resolution signing in with an email makes.
            val username = usernameForEmail(email.trim().lowercase())
                ?: return ResetRequestResult.NoAccount

            client.functions.invoke(
                function = RESET_FUNCTION,
                body = buildJsonObject { put("username", username) }
            )

            ResetRequestResult.Sent(Username.identity(username))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Safe to report: this says the function could not be reached, not whether the account
            // exists. The function answers 200 whatever it finds.
            ResetRequestResult.Failed(error.toCloudReason())
        }
    }

    /**
     * Checks the code, and takes the session it comes with.
     *
     * A wrong code and an unreachable server are told apart deliberately: one is an answer, and the
     * other is the absence of one, and a person who mistyped a digit should not be told to check
     * their connection.
     */
    override suspend fun verifyResetCode(identity: String, code: String): ResetCodeResult {
        return try {
            client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = identity,
                token = code
            )
            ResetCodeResult.Accepted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            when {
                error.isNetworkFailure() -> ResetCodeResult.Failed(error.toCloudReason())
                error.saysExpired() -> ResetCodeResult.Expired
                // Everything else GoTrue can answer here is "that is not the code": it does not
                // distinguish a code for another account from one that never existed.
                else -> ResetCodeResult.Invalid
            }
        }
    }

    /** The password of the account the verified code signed in. */
    override suspend fun setNewPassword(password: String): PasswordChangeResult {
        // Held in a local because the builder below has a `password` of its own, and
        // `password = password` inside it would be the builder assigning to itself.
        val chosen = password

        return try {
            client.auth.updateUser { this.password = chosen }
            PasswordChangeResult.Changed
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (error.isNetworkFailure()) {
                PasswordChangeResult.Failed(error.toCloudReason())
            } else {
                PasswordChangeResult.Rejected(error.toCloudReason())
            }
        }
    }

    /** Whether the auth service said the code was right once and is too old now. */
    private fun Throwable.saysExpired(): Boolean =
        generateSequence(this) { error -> error.cause }
            .any { error -> error.message?.contains(EXPIRED, ignoreCase = true) == true }

    /** The username behind an email, or null when no account uses it. */
    private suspend fun usernameForEmail(email: String): String? {
        val answer = client.postgrest
            .rpc("username_for_email", buildJsonObject { put("candidate", email) })
            .decodeAs<JsonElement>()

        return (answer as? JsonPrimitive)?.contentOrNull
    }

    private suspend fun booleanRpc(function: String, candidate: String): Boolean {
        val answer = client.postgrest
            .rpc(function, buildJsonObject { put("candidate", candidate) })
            .decodeAs<JsonElement>()

        return (answer as? JsonPrimitive)?.contentOrNull == "true"
    }

    /**
     * The username is the identity, so it can be read straight off the address the
     * account was created with — no query, and it cannot disagree with the account.
     * The real email comes from the sign-up metadata, which is where the profile got
     * it from.
     */
    private fun UserSession.toAccount(): Account? {
        val user = user ?: return null
        val identity = user.email ?: return null

        return Account(
            id = user.id,
            username = Username.fromIdentity(identity) ?: identity.substringBefore('@'),
            email = user.userMetadata?.get("contact_email")?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * A sign-up that reached the database and was refused.
     *
     * The profile is written by a trigger, so a duplicate username or email comes back
     * as the constraint that stopped it, which is exactly what the screen needs to say
     * which one to change. Anything else is reported as a plain failure rather than
     * guessed at.
     */
    private fun RestException.toSignUpResult(): SignUpResult {
        val detail = describe()

        return when {
            detail.contains(USERNAME_CONSTRAINT) -> SignUpResult.UsernameTaken
            detail.contains(EMAIL_CONSTRAINT) -> SignUpResult.EmailTaken
            detail.contains(USERNAME_SHAPE) -> SignUpResult.Rejected(
                "Usernames use 3–30 letters, numbers or underscores."
            )
            else -> SignUpResult.Failed("the account could not be created")
        }
    }

    /**
     * Signing in does not distinguish "no such account" from "wrong password", and
     * neither does the app — otherwise it would become a way to discover usernames.
     */
    private fun RestException.toSignInResult(): SignInResult =
        if (statusCode in CLIENT_ERRORS) {
            SignInResult.InvalidCredentials
        } else {
            SignInResult.Failed("the project answered $statusCode")
        }

    /** Everything the exception can say about what went wrong, in one string. */
    private fun RestException.describe(): String =
        listOf(message.orEmpty(), toString()).joinToString(separator = " ")

    private companion object {
        /**
         * The function that mails a reset code — see `supabase/functions/request-password-reset`.
         *
         * It exists because the auth service mails the account's *identity* address, and that address
         * is deliberately one nothing can be delivered to. This function is the only part of the
         * project that may read a profile's real address, and it does so with a key the app never has.
         */
        const val RESET_FUNCTION = "request-password-reset"

        const val USERNAME_CONSTRAINT = "profiles_username_key"
        const val EMAIL_CONSTRAINT = "profiles_email_key"
        const val USERNAME_SHAPE = "profiles_username_shape"

        /** What the auth service says about a code that was right and is now too old. */
        const val EXPIRED = "expired"

        val CLIENT_ERRORS = 400..499
    }
}
