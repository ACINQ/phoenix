package fr.acinq.phoenix.controllers.init

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch


class AppInitController(
    loggerFactory: LoggerFactory
) : AppController<Initialization.Model, Initialization.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Initialization.Model.Ready
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory
    )

    override fun process(intent: Initialization.Intent) {
        when (intent) {
            is Initialization.Intent.GenerateWallet -> {
                launch {
                    val mnemonics = MnemonicCode.toMnemonics(
                        entropy = intent.entropy,
                        wordlist = intent.language.wordlist()
                    )
                    val seed = MnemonicCode.toSeed(mnemonics, "")
                    model(Initialization.Model.GeneratedWallet(mnemonics, intent.language, seed))
                }
            }
        }
    }
}
