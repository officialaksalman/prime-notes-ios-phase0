package phase0

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun phase0RoomBuilder(path: String): RoomDatabase.Builder<Phase0Database> =
    Room.databaseBuilder<Phase0Database>(name = path)

actual fun phase0RoomBuilderV1(path: String): RoomDatabase.Builder<Phase0DatabaseV1> =
    Room.databaseBuilder<Phase0DatabaseV1>(name = path)

actual fun platformEnv(name: String): String? = System.getenv(name)

actual fun phase0TempPath(fileName: String): String =
    File(System.getProperty("java.io.tmpdir"), fileName).absolutePath
