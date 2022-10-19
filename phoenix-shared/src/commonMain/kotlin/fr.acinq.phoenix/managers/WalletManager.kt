package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


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

    // Converts a mnemonics list to a seed.
    // This is generally called with a mnemonics list that has been previously saved.
    fun mnemonicsToSeed(
        mnemonics: List<String>,
        passphrase: String = ""
    ): ByteArray {
        MnemonicCode.validate(mnemonics = mnemonics, wordlist = MnemonicCode.englishWordlist)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    data class WalletInfo(
        val nodeId: PublicKey,
        val cloudKey: ByteVector32, // used for cloud storage
        val encryptedNodeId: String // used for cloud storage
    )

    fun loadWallet(seed: ByteArray): WalletInfo? {
        if (_wallet.value != null) {
            return null
        }

        val newWallet = Wallet(seed, chain)
        _wallet.value = newWallet

        val (cloudKey, encryptedNodeId) = newWallet.cloudKeyAndEncryptedNodeId()
        return WalletInfo(
            nodeId = newWallet.nodeId(),
            cloudKey = cloudKey,
            encryptedNodeId = encryptedNodeId
        )
    }

    fun getXpub(): Pair<String, String>? = _wallet.value?.xpub()
}
