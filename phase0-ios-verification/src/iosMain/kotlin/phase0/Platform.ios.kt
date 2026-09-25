package phase0

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

actual fun phase0RoomBuilder(path: String): RoomDatabase.Builder<Phase0Database> =
    Room.databaseBuilder<Phase0Database>(name = path)

actual fun phase0RoomBuilderV1(path: String): RoomDatabase.Builder<Phase0DatabaseV1> =
    Room.databaseBuilder<Phase0DatabaseV1>(name = path)

actual fun platformEnv(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as? String

/** A test binary is allowed to write to the simulator's temporary directory. */
actual fun phase0TempPath(fileName: String): String = NSTemporaryDirectory() + fileName
