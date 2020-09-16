package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration.Intent
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration.Intent.*
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class AppDisplayConfigurationController(di: DI) : AppController<DisplayConfiguration.Model, Intent>(di, DisplayConfiguration.Model()) {
    private val configurationManager: AppConfigurationManager by instance()

    init { sendDisplayConfigurationModel() }

    override fun process(intent: Intent) {
        when(intent) {
            is UpdateFiatCurrency -> configurationManager.putFiatCurrency(intent.fiatCurrency)
            is UpdateBitcoinUnit -> configurationManager.putBitcoinUnit(intent.bitcoinUnit)
            is UpdateAppTheme -> configurationManager.putAppTheme(intent.appTheme)
        }

        sendDisplayConfigurationModel()
    }

    private fun sendDisplayConfigurationModel() {
        launch {
            val config = configurationManager.getAppConfiguration()
            model(
                DisplayConfiguration.Model(
                    fiatCurrency = config.fiatCurrency,
                    bitcoinUnit = config.bitcoinUnit,
                    appTheme = config.appTheme
                )
            )
        }
    }
}
