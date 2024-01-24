package fr.acinq.phoenix.controllers.init

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.launch


class AppRestoreWalletController(
    loggerFactory: Logger
) : AppController<RestoreWallet.Model, RestoreWallet.Intent>(
    loggerFactory = loggerFactory,
    firstModel = RestoreWallet.Model.Ready
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory
    )

    override fun process(intent: RestoreWallet.Intent) {
        when (intent) {
            is RestoreWallet.Intent.FilterWordList -> when {
                intent.predicate.length > 1 -> launch {
                    val words = MnemonicCode.englishWordlist.filter {
                        it.startsWith(intent.predicate, ignoreCase = true)
                    }
                    model(
                        RestoreWallet.Model.FilteredWordlist(
                        uuid = intent.uuid,
                        predicate = intent.predicate,
                        words = words
                    ))
                }
                else -> launch {
                    model(
                        RestoreWallet.Model.FilteredWordlist(
                        uuid = intent.uuid,
                        predicate = intent.predicate,
                        words = emptyList()
                    ))
                }
            }
            is RestoreWallet.Intent.Validate -> {
                try {
                    MnemonicCode.validate(intent.mnemonics)
                    val seed = MnemonicCode.toSeed(intent.mnemonics, passphrase = "")
                    launch { model(RestoreWallet.Model.ValidMnemonics(intent.mnemonics, seed)) }
                } catch (e: IllegalArgumentException) {
                    launch { model(RestoreWallet.Model.InvalidMnemonics) }
                }
            }
        }
    }

}
