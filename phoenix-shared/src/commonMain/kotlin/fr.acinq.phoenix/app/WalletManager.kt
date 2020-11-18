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
class WalletManager (private val appDb: DB) : CoroutineScope by MainScope() {
    private val walletUpdates = ConflatedBroadcastChannel<Wallet>()
    fun openWalletUpdatesSubscription(): ReceiveChannel<Wallet> = walletUpdates.openSubscription()

    init {
        appDb.on<Wallet>().register {
            didPut {
                launch { walletUpdates.send(it) }
            }
        }
        getWallet()?.let {
            launch { walletUpdates.send(it) }
        }
    }

    fun createWallet(mnemonics: List<String>): Unit {
        MnemonicCode.validate(mnemonics)
        appDb.put(Wallet(mnemonics = mnemonics))
    }

    fun getWallet() : Wallet? {
        val key = appDb.key<Wallet>(0)
        return appDb[key]
    }

}
