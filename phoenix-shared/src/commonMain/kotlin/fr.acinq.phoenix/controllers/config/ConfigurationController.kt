package fr.acinq.phoenix.controllers.config

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


class AppConfigurationController(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : AppController<Configuration.Model, Configuration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Configuration.Model.SimpleMode
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        walletManager = business.walletManager
    )

    init {
        launch {
            model(
                if (walletManager.wallet.value == null)
                    Configuration.Model.SimpleMode
                else
                    Configuration.Model.FullMode
            )
        }
    }

    override fun process(intent: Configuration.Intent) = error("Nothing to process")
}
