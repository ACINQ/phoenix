package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration.Intent
import fr.acinq.phoenix.ctrl.config.DisplayConfiguration.Intent.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppDisplayConfigurationController(loggerFactory: LoggerFactory, private val configurationManager: AppConfigurationManager) : AppController<DisplayConfiguration.Model, Intent>(loggerFactory, DisplayConfiguration.Model()) {
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
