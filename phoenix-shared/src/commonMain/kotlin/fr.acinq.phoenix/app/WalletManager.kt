package fr.acinq.phoenix.app

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.TAG_APPLICATION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.kodein.db.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager (override val di: DI) : DIAware, CoroutineScope by MainScope() {
    private val db: DB by instance(tag = TAG_APPLICATION)

    private val walletUpdates = ConflatedBroadcastChannel<Wallet>()
    fun openWalletUpdatesSubscription(): ReceiveChannel<Wallet> = walletUpdates.openSubscription()

    init {
        db.on<Wallet>().register {
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
        db.put(Wallet(mnemonics = mnemonics))
    }

    fun getWallet() : Wallet? {
        val key = db.key<Wallet>(0)
        return db[key]
    }

}
