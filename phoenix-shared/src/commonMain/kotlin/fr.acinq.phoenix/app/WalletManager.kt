package fr.acinq.phoenix.app

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.db.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager : CoroutineScope by MainScope() {
    private val _wallet = MutableStateFlow<Wallet?>(null)
    public val walletState: StateFlow<Wallet?> = _wallet
    public val wallet by _wallet

    fun loadWallet(seed: ByteArray): Unit {
        val newWallet = Wallet(seed = seed)
        _wallet.value = newWallet
    }
}
