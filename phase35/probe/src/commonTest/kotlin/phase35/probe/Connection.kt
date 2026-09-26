package phase35.probe

import androidx.sqlite.SQLiteConnection

/**
 * The seam the tests open a connection through, so `commonTest` names no platform type. One actual
 * per platform, each using that platform's bundled SQLite.
 */
expect fun probeConnection(): SQLiteConnection
