package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.Configuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigurationController(di: DI) : AppController<Configuration.Model, Configuration.Intent>(di, Configuration.Model.SimpleMode) {
    private val walletManager: WalletManager by instance()

    init {
        launch {
            model(
                if (walletManager.getWallet() == null)
                    Configuration.Model.SimpleMode
                else
                    Configuration.Model.FullMode
            )
        }
    }

    override fun process(intent: Configuration.Intent) = error("Nothing to process")
}
