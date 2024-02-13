package fr.acinq.phoenix.controllers.config

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


class AppLogsConfigurationController(
    loggerFactory: LoggerFactory,
    private val ctx: PlatformContext,
) : AppController<LogsConfiguration.Model, LogsConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = LogsConfiguration.Model.Awaiting
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        ctx = business.ctx,
    )


    override fun process(intent: LogsConfiguration.Intent) {
    }
}
