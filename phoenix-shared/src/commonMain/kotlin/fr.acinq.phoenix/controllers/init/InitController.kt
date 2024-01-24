package fr.acinq.phoenix.controllers.init

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch


class AppInitController(
    loggerFactory: Logger
) : AppController<Initialization.Model, Initialization.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Initialization.Model.Ready
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory
    )

    override fun process(intent: Initialization.Intent) {
        when (intent) {
            is Initialization.Intent.GenerateWallet -> {
                launch {
                    val mnemonics = MnemonicCode.toMnemonics(intent.entropy)
                    val seed = MnemonicCode.toSeed(mnemonics, "")
                    model(Initialization.Model.GeneratedWallet(mnemonics, seed))
                }
            }
        }
    }
}
