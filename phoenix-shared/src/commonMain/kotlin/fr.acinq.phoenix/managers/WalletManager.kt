package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager(
    private val chain: Chain
) : CoroutineScope by MainScope() {

    private val _wallet = MutableStateFlow<Wallet?>(null)
    internal val wallet: StateFlow<Wallet?> = _wallet

    private val _hasWallet = MutableStateFlow<Boolean>(false)
    val hasWallet: StateFlow<Boolean> = _hasWallet

    init {
        launch {
            _wallet.collect {
                _hasWallet.value = it != null
            }
        }
    }

    fun loadWallet(seed: ByteArray) {
        val newWallet = Wallet(seed, chain)
        _wallet.value = newWallet
    }
}
