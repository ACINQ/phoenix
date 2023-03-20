package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.*
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

    /**
     * - nodeIdHash:
     *   We need to store data in the local filesystem that's associated with the
     *   specific nodeId, but we don't want to leak the nodeId.
     *   (i.e. We don't want to use the nodeId in cleartext anywhere).
     *   So we instead use the nodeIdHash as the identifier for local files.
     * 
     * - cloudKey:
     *   We need a key to encypt/decrypt the blobs we store in the cloud.
     *   And we prefer this key to be seperate from other keys.
     * 
     * - cloudKeyHash:
     *   Similar to the nodeIdHash, we need to store data in the cloud that's associated
     *   with the specific nodeId, but we don't want to leak the nodeId.
     */
    data class WalletInfo(
        val nodeId: PublicKey,
        val nodeIdHash: String,     // used for local storage
        val cloudKey: ByteVector32, // used for cloud storage
        val cloudKeyHash: String    // used for cloud storage
    )

    fun loadWallet(seed: ByteArray): WalletInfo? {
        if (_wallet.value != null) {
            return null
        }

        val newWallet = Wallet(seed, chain)
        _wallet.value = newWallet

        val nodeId = newWallet.nodeId()
        val nodeIdHash = Crypto.hash160(nodeId.value).byteVector().toHex()

        val cloudKey = newWallet.cloudKey()
        val cloudKeyHash = Crypto.hash160(cloudKey).byteVector().toHex()

        return WalletInfo(
            nodeId = nodeId,
            nodeIdHash = nodeIdHash,
            cloudKey = cloudKey,
            cloudKeyHash = cloudKeyHash
        )
    }

    fun getXpub(): Pair<String, String>? = _wallet.value?.xpub()
}
