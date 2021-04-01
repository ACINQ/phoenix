package fr.acinq.phoenix.app

import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager : CoroutineScope by MainScope() {
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet

    fun loadWallet(seed: ByteArray) {
        val newWallet = Wallet(seed = seed)
        _wallet.value = newWallet
    }
}
