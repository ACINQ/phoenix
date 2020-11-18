package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.RestoreWallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppRestoreWalletController(loggerFactory: LoggerFactory, private val walletManager: WalletManager) : AppController<RestoreWallet.Model, RestoreWallet.Intent>(loggerFactory, RestoreWallet.Model.Warning) {

    override fun process(intent: RestoreWallet.Intent) {
        when (intent) {
            RestoreWallet.Intent.AcceptWarning -> launch { model(RestoreWallet.Model.Ready) }
            is RestoreWallet.Intent.ValidateSeed -> {
                try {
                    walletManager.createWallet(intent.mnemonics)
                } catch (e: IllegalArgumentException) {
                    launch { model(RestoreWallet.Model.InvalidSeed) }
                }
            }
            is RestoreWallet.Intent.FilterWordList -> when {
                intent.predicate.length > 1 -> launch {
                    model(
                        RestoreWallet.Model.Wordlist(
                            MnemonicCode.englishWordlist.filter { it.startsWith(intent.predicate, ignoreCase = true) }
                        )
                    )
                }
                else -> launch { model(RestoreWallet.Model.Wordlist(emptyList())) }
            }
        }
    }

}
