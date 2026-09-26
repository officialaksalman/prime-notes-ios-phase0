package com.primenotes.bd.data.cloud

import com.primenotes.bd.domain.account.AccountRepository
import com.primenotes.bd.domain.account.PasswordChangeResult
import com.primenotes.bd.domain.account.ResetCodeResult
import com.primenotes.bd.domain.account.ResetRequestResult
import com.primenotes.bd.domain.account.SignInResult
import com.primenotes.bd.domain.account.SignUpResult
import com.primenotes.bd.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The account when there is no cloud to have one on.
 *
 * A build with no project configured has no client, so there is nothing to sign in
 * to. Every attempt says so rather than failing obscurely, and the account is
 * permanently absent — which is also why nothing in the app can accidentally start
 * talking to somewhere that was never set up.
 */
object UnconfiguredAccountRepository : AccountRepository {

    private const val NO_CLOUD = "no cloud project is configured"

    override val account: Flow<Account?> = flowOf(null)

    override suspend fun signUp(username: String, email: String, password: String): SignUpResult =
        SignUpResult.Failed(NO_CLOUD)

    override suspend fun signIn(identifier: String, password: String): SignInResult =
        SignInResult.Failed(NO_CLOUD)

    override suspend fun isUsernameAvailable(username: String): Boolean = false

    override suspend fun isEmailAvailable(email: String): Boolean = false

    override suspend fun requestPasswordReset(email: String): ResetRequestResult =
        ResetRequestResult.Failed(NO_CLOUD)

    override suspend fun verifyResetCode(identity: String, code: String): ResetCodeResult =
        ResetCodeResult.Failed(NO_CLOUD)

    override suspend fun setNewPassword(password: String): PasswordChangeResult =
        PasswordChangeResult.Failed(NO_CLOUD)

    override suspend fun signOut() = Unit
}
