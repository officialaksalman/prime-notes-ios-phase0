package com.primenotes.bd.domain.model

/**
 * The account someone is signed in as.
 *
 * [username] is the identity they sign in with; [email] is the real address kept on
 * their profile, which may be absent. The Supabase identity behind the account is a
 * synthetic address built from the username and never shown.
 */
data class Account(
    val id: String,
    val username: String,
    val email: String?
)
