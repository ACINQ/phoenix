package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlin.experimental.ExperimentalNativeApi

class PassthruLogWriter(
    val logger: (Severity, String, String) -> Unit
) : LogWriter() {

    constructor(ctx: PlatformContext) : this(
        logger = ctx.logger!!
    )

    @OptIn(ExperimentalNativeApi::class)
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        logger(severity, message, tag)
        if (severity == Severity.Error || severity == Severity.Warn) {
            if (throwable != null) {
                throwable.message?.let {
                    logger(severity, "exception thrown: $it", tag)
                }
                // We don't use the built-in stackTraceToString because we want to restrict the output to the first few lines of the stack, and also trim whitespaces.
                val stackString = throwable.getStackTrace().take(5).joinToString("; ") { it.trim().replace(Regex("\\s+"), " ")  }
                logger(severity, "exception stack: [ $stackString ]", tag)
            }
        }
    }
}