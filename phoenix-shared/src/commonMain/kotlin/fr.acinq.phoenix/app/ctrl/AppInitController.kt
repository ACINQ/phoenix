package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Initialization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppInitController(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : AppController<Initialization.Model, Initialization.Intent>(loggerFactory, Initialization.Model.Ready) {

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
