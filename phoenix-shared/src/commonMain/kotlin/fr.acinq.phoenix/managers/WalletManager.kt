package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager(
    private val chain: Chain
) : CoroutineScope by MainScope() {

    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet

    fun loadWallet(seed: ByteArray) {
        val newWallet = Wallet(seed, chain)
        _wallet.value = newWallet
    }
}
