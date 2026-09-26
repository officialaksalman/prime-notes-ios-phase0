package com.primenotes.bd.data.cloud

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The JVM classification, used by the Android target and by the host test compilation.
 *
 * The order matters: the three network types are checked before the general [IOException],
 * because an [UnknownHostException] is also an [IOException] and a timeout often arrives
 * wrapped in one. `SecurityException` last among the specials, since it is a
 * [RuntimeException] and never an [IOException].
 */
actual fun Throwable.networkFailureKind(): FailureKind = when (this) {
    is UnknownHostException -> FailureKind.HostNotResolved
    is SocketTimeoutException -> FailureKind.TimedOut
    is SecurityException -> FailureKind.NotPermitted
    is IOException -> FailureKind.Unreachable
    else -> FailureKind.None
}
