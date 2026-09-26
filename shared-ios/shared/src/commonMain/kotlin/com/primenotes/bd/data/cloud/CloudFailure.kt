package com.primenotes.bd.data.cloud

/**
 * A short, human reason for a failed cloud call.
 *
 * Exception messages from an HTTP stack name the full URL, the HTTP verb and the
 * nested cause, which is unreadable in a settings card and says nothing a user can
 * act on. The common failures are recognised by type — through the whole cause chain,
 * because the useful exception is usually wrapped — and anything else falls back to
 * the first line of the message, trimmed to something that fits.
 *
 * The types are named by [networkFailureKind] rather than matched here, because the exception
 * classes are not the same on every platform: the JVM has `java.net`, Kotlin/Native does not,
 * and what the Darwin engine throws for an unreachable host is an `NSURLError` code inside its
 * own exception type. Classifying per platform is what lets the reasons above — the part a person
 * actually reads — be written once.
 */
fun Throwable.toCloudReason(): String {
    val chain = generateSequence(this) { error -> error.cause }.toList()

    return when {
        chain.any { error -> error.networkFailureKind() == FailureKind.HostNotResolved } ->
            "the project host could not be resolved"

        chain.any { error -> error.networkFailureKind() == FailureKind.TimedOut } ->
            "the project did not answer in time"

        chain.any { error -> error.networkFailureKind() == FailureKind.NotPermitted } ->
            "this app is not allowed to use the network"

        chain.any { error -> error.networkFailureKind() == FailureKind.Unreachable } ->
            "the network could not be reached"

        else -> (message ?: this::class.simpleName ?: "the request failed")
            .lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            ?.take(MAX_REASON_LENGTH)
            ?: "the request failed"
    }
}

/**
 * Whether this failure is the network rather than an answer.
 *
 * The distinction matters everywhere the app reports a failure: a request that never arrived says
 * nothing about what was asked — a wrong code and an unreachable server must not read the same way,
 * and a row the server refused must not be confused with one it never saw.
 */
fun Throwable.isNetworkFailure(): Boolean =
    generateSequence(this) { error -> error.cause }
        .any { error -> error.networkFailureKind() != FailureKind.None }

/** How a failure is classified. [None] means "not a network problem". */
enum class FailureKind {
    None,
    Unreachable,
    HostNotResolved,
    TimedOut,
    NotPermitted
}

/**
 * Classifies this throwable on the platform that raised it.
 *
 * A function rather than a type test, because the same *situation* arrives as different *classes*:
 * `UnknownHostException` on the JVM, an `NSURLError` on Darwin.
 */
expect fun Throwable.networkFailureKind(): FailureKind

private const val MAX_REASON_LENGTH = 120
