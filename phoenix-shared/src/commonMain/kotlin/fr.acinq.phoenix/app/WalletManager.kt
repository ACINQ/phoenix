package fr.acinq.phoenix.app

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.kodein.db.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager : CoroutineScope by MainScope() {
    private val walletUpdates = ConflatedBroadcastChannel<Wallet>()
    private var wallet: Wallet? = null

    fun openWalletUpdatesSubscription(): ReceiveChannel<Wallet> = walletUpdates.openSubscription()

    fun loadWallet(seed: ByteArray): Unit {
        val newWallet = Wallet(seed = seed)
        wallet = newWallet
        launch { walletUpdates.send(newWallet) }
    }

    fun getWallet() : Wallet? {
        return wallet
    }

}
