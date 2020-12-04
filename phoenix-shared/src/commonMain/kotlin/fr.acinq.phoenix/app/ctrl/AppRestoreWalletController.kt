package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.RestoreWallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppRestoreWalletController(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : AppController<RestoreWallet.Model, RestoreWallet.Intent>(
    loggerFactory,
    RestoreWallet.Model.Ready
) {

    override fun process(intent: RestoreWallet.Intent) {
        when (intent) {
            is RestoreWallet.Intent.FilterWordList -> when {
                intent.predicate.length > 1 -> launch {
                    model(
                        RestoreWallet.Model.FilteredWordlist(
                            MnemonicCode.englishWordlist.filter {
                                it.startsWith(intent.predicate, ignoreCase = true)
                            }
                        )
                    )
                }
                else -> launch {
                    model(RestoreWallet.Model.FilteredWordlist(emptyList()))
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
