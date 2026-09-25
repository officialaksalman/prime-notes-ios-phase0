package phase0.uicloud

import platform.Foundation.NSProcessInfo

actual fun platformEnv(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as? String
