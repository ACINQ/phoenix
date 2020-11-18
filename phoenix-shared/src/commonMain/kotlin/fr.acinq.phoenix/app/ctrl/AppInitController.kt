package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Initialization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppInitController(loggerFactory: LoggerFactory, private val walletManager: WalletManager) : AppController<Initialization.Model, Initialization.Intent>(loggerFactory, Initialization.Model.Initialization) {
    override fun process(intent: Initialization.Intent) = when (intent) {
        Initialization.Intent.CreateWallet -> {
            launch { model(Initialization.Model.Creating) }
            walletManager.createWallet(
                MnemonicCode.toMnemonics(Random.secure().nextBytes(16))
            )
        }
    }

}
