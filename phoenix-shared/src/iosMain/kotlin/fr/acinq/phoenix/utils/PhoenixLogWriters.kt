package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

/**
 * Todo: Replace this with an appropriate LogWriter for iOS.
 * The default implementation of `platformLogWriter` returns `LogcatWriter()`.
 */
actual fun phoenixLogWriters(): List<LogWriter> = listOf(OSLogWriter())