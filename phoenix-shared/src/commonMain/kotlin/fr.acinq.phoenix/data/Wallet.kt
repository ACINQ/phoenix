package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.crypto.Digest
import fr.acinq.bitcoin.crypto.hmac
import fr.acinq.secp256k1.Hex
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*


data class Wallet(val seed: ByteVector64, val chain: Chain) {

    constructor(seed: ByteArray, chain: Chain): this(ByteVector64(seed), chain)

    private val master by lazy { DeterministicWallet.generate(seed) }

    fun masterPublicKey(path: String): String {
        val publicKey =
            DeterministicWallet.publicKey(
                DeterministicWallet.derivePrivateKey(master, path)
            )
        return DeterministicWallet.encode(
            input = publicKey,
            prefix = if (chain.isMainnet()) DeterministicWallet.zpub else DeterministicWallet.vpub
        )
    }

    /** Get the wallet (xpub, path) */
    fun xpub(): Pair<String, String> {
        val isMainnet = chain.isMainnet()
        val masterPubkeyPath = if (isMainnet) "m/84'/0'/0'" else "m/84'/1'/0'"
        return masterPublicKey(masterPubkeyPath) to masterPubkeyPath
    }

    fun onchainAddress(path: String): String {
        val isMainnet = chain.isMainnet()
        val chainHash = if (isMainnet) Block.LivenetGenesisBlock.hash else Block.TestnetGenesisBlock.hash
        val publicKey = DeterministicWallet.derivePrivateKey(master, path).publicKey
        return Bitcoin.computeBIP84Address(publicKey, chainHash)
    }

    // For cloud storage, we need:
    // - an encryption key
    // - a cleartext string that's tied to a specific nodeId
    //
    // But we also don't want to expose the nodeId.
    // So we deterministically derive both values from the seed.
    //
    fun cloudKeyAndEncryptedNodeId(): Pair<ByteVector32, String> {
        val path = if (chain.isMainnet()) "m/51'/0'/0'/0" else "m/51'/1'/0'/0"
        val extPrivKey = DeterministicWallet.derivePrivateKey(master, path)

        val cloudKey = extPrivKey.privateKey.value
        val hash = Crypto.hash160(cloudKey).byteVector().toHex()

        return Pair(cloudKey, hash)
    }

    fun lnurlAuthLinkingKey(domain: String): PrivateKey {
        val hashingKey = DeterministicWallet.derivePrivateKey(master, "m/138'/0")
        val path = lnurlAuthPath(domain, hashingKey.privateKey.value.toByteArray())
        return DeterministicWallet.derivePrivateKey(master, path).privateKey
    }

    /* lnurl-auth path derivation, as described in spec:
     * https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
     *
     * Test vectors exist for path derivation.
     */
    internal fun lnurlAuthPath(domain: String, hashingKey: ByteArray): KeyPath {
        val fullHash = Digest.sha256().hmac(
            key = hashingKey,
            data = domain.encodeToByteArray(),
            blockSize = 64
        )
        require(fullHash.size >= 16) { "domain hash must be at least 16 bytes" }
        val path1 = fullHash.sliceArray(IntRange(0, 3)).readUInt(0, ByteOrder.BIG_ENDIAN)
        val path2 = fullHash.sliceArray(IntRange(4, 7)).readUInt(0, ByteOrder.BIG_ENDIAN)
        val path3 = fullHash.sliceArray(IntRange(8, 11)).readUInt(0, ByteOrder.BIG_ENDIAN)
        val path4 = fullHash.sliceArray(IntRange(12, 15)).readUInt(0, ByteOrder.BIG_ENDIAN)

        return KeyPath("m/138'/${path1}/${path2}/${path3}/${path4}")
    }

    override fun toString(): String = "Wallet"
}

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.readUInt(index: Int, order: ByteOrder): UInt {
    // According to the docs for `ByteArray.getXAt(index: Int)`:
    // > [These operations] extract primitive values out of the [ByteArray] byte buffers.
    // > Data is treated as if it was in Least-Significant-Byte first (little-endian) byte order.
    val littleEndian = this.get(index) as UInt // as getUIntAt(index)
    return when (order) {
        ByteOrder.LITTLE_ENDIAN -> littleEndian
        ByteOrder.BIG_ENDIAN -> littleEndian.reverseByteOrder()
    }
}
