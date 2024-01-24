package fr.acinq.phoenix.controllers.config

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch


class AppConfigurationController(
    loggerFactory: Logger,
    private val walletManager: WalletManager
) : AppController<Configuration.Model, Configuration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Configuration.Model.SimpleMode
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory,
        walletManager = business.walletManager
    )

    init {
        launch {
            model(
                if (!walletManager.isLoaded())
                    Configuration.Model.SimpleMode
                else
                    Configuration.Model.FullMode
            )
        }
    }

    override fun process(intent: Configuration.Intent) = error("Nothing to process")
}
