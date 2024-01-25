package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter

actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> {
    return if (ctx.logger != null) {
        listOf(PassthruLogWriter(ctx))
    } else {
        listOf(OSLogWriter())
    }
}