package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.Configuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigurationController(loggerFactory: LoggerFactory, private val walletManager: WalletManager) : AppController<Configuration.Model, Configuration.Intent>(loggerFactory, Configuration.Model.SimpleMode) {

    init {
        launch {
            model(
                if (walletManager.wallet == null)
                    Configuration.Model.SimpleMode
                else
                    Configuration.Model.FullMode
            )
        }
    }

    override fun process(intent: Configuration.Intent) = error("Nothing to process")
}
