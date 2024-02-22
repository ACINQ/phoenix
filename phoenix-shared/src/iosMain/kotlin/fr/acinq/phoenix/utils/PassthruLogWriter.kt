package fr.acinq.phoenix.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class PassthruLogWriter(
    val logger: (Severity, String, String) -> Unit
) : LogWriter() {

    constructor(ctx: PlatformContext) : this(
        logger = ctx.logger!!
    )

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        logger(severity, message, tag)
    }
}