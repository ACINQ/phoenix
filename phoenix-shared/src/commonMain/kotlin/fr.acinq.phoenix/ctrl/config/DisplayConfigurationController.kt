package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.phoenix.data.*

typealias DisplayConfigurationController = MVI.Controller<DisplayConfiguration.Model, DisplayConfiguration.Intent>

object DisplayConfiguration {

    data class Model(
        val fiatCurrency: FiatCurrency = FiatCurrency.USD,
        val bitcoinUnit: BitcoinUnit = BitcoinUnit.Satoshi,
        val appTheme: AppTheme = AppTheme.System
    ) : MVI.Model()

    sealed class Intent : MVI.Intent() {
        data class UpdateFiatCurrency(val fiatCurrency: FiatCurrency) : Intent()
        data class UpdateBitcoinUnit(val bitcoinUnit: BitcoinUnit) : Intent()
        data class UpdateAppTheme(val appTheme: AppTheme) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
