package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Init
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppInitController(di: DI) : AppController<Init.Model, Init.Intent>(di, Init.Model.Initialization) {
    private val walletManager: WalletManager by instance()

    override fun process(intent: Init.Intent) = when (intent) {
        Init.Intent.CreateWallet -> {
            launch { model { Init.Model.Creating } }
            walletManager.createWallet(
                MnemonicCode.toMnemonics(Random.secure().nextBytes(16))
            )
        }
    }

}
