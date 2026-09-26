package com.primenotes.bd.data.cloud

/**
 * What a probe of the cloud found.
 *
 * The two questions are kept apart on purpose: whether the project answered at all,
 * and whether a signed-out client can read notes. The second is a real security
 * assertion, not decoration.
 */
sealed interface CloudProbeResult {

    /** No cloud configured in this build. Not a failure. */
    data object NotConfigured : CloudProbeResult

    /** The project answered with the given key. [notesVisible] must be 0. */
    data class Reachable(val notesVisible: Int) : CloudProbeResult

    /** The project answered and refused to read notes — the correct answer before sign-in. */
    data object Denied : CloudProbeResult

    data class Unreachable(val reason: String) : CloudProbeResult
}

/**
 * Asks the cloud those two questions.
 *
 * **Contract:** implementations do not throw. Anything that goes wrong — a refused
 * key, a dead network, a project that is not there — comes back as
 * [CloudProbeResult.Unreachable]. Callers therefore have one shape to handle, and
 * cannot accidentally leave a failure looking like a success.
 *
 * An interface so the screen's logic can be tested without a network or a project.
 */
fun interface CloudProbe {
    suspend fun probe(): CloudProbeResult
}
