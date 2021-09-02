package fr.acinq.phoenix.controllers.init

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
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
                    launch { model(RestoreWallet.Model.ValidMnemonics(seed)) }
                } catch (e: IllegalArgumentException) {
                    launch { model(RestoreWallet.Model.InvalidMnemonics) }
                }
            }
        }
    }

}
