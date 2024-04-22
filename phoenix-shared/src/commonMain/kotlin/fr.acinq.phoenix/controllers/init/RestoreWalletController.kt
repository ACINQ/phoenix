package fr.acinq.phoenix.controllers.init

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch


class AppRestoreWalletController(
    loggerFactory: LoggerFactory
) : AppController<RestoreWallet.Model, RestoreWallet.Intent>(
    loggerFactory = loggerFactory,
    firstModel = RestoreWallet.Model.Ready
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory
    )

    override fun process(intent: RestoreWallet.Intent) {
        when (intent) {
            is RestoreWallet.Intent.FilterWordList -> launch {
                processIntent(intent)
            }
            is RestoreWallet.Intent.Validate -> launch {
                processIntent(intent)
            }
        }
    }

    private suspend fun processIntent(
        intent: RestoreWallet.Intent.FilterWordList
    ) {
        when {
            intent.predicate.length > 1 -> {
                val words = intent.language.matches(intent.predicate)
                model(RestoreWallet.Model.FilteredWordlist(
                    uuid = intent.uuid,
                    predicate = intent.predicate,
                    words = words
                ))
            }
            else -> {
                model(RestoreWallet.Model.FilteredWordlist(
                    uuid = intent.uuid,
                    predicate = intent.predicate,
                    words = emptyList()
                ))
            }
        }
    }

    private suspend fun processIntent(
        intent: RestoreWallet.Intent.Validate
    ) {
        try {
            MnemonicCode.validate(intent.mnemonics, intent.language.wordlist())
            val seed = MnemonicCode.toSeed(intent.mnemonics, passphrase = "")
            model(RestoreWallet.Model.ValidMnemonics(intent.mnemonics, intent.language, seed))
        } catch (e: IllegalArgumentException) {
            model(RestoreWallet.Model.InvalidMnemonics)
        }
    }
}
