package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

/**
 * Todo: Replace this with an appropriate LogWriter for Android.
 * The default implementation of `platformLogWriter` returns `LogcatWriter()`.
 */
actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> = listOf(platformLogWriter())
