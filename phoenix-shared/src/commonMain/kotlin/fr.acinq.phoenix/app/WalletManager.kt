package fr.acinq.phoenix.app

import fr.acinq.bitcoin.Block
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.fee.FeerateTolerance
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
 import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager : CoroutineScope by MainScope() {
    private val _wallet = MutableStateFlow<Wallet?>(null)
    public val wallet: StateFlow<Wallet?> = _wallet

    fun loadWallet(seed: ByteArray): Unit {
        val newWallet = Wallet(seed = seed)
        _wallet.value = newWallet
    }
}
