package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.*
import fr.acinq.lightning.utils.Either
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.Chain
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class Utilities(
    loggerFactory: LoggerFactory,
    private val chain: Chain
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain
    )

    private val logger = newLogger(loggerFactory)

    enum class BitcoinAddressType {
        Base58PubKeyHash,
        Base58ScriptHash,
        SegWitPubKeyHash,
        SegWitScriptHash
    }

    data class BitcoinAddressInfo(
        val address: String, // may be different than input; e.g. input is "bitcoin:xyz"
        val chain: Chain,
        val type: BitcoinAddressType,
        val hash: ByteVector,
        val params: Parameters
    )

    sealed class BitcoinAddressError {
        data class ChainMismatch(val myChain: Chain, val addrChain: Chain): BitcoinAddressError()
        data class UnknownBase58Prefix(val prefix: Byte): BitcoinAddressError()
        data class UnknownBech32Prefix(val hrp: String): BitcoinAddressError()
        data class UnknownBech32Version(val version: Byte): BitcoinAddressError()
        object UnknownFormat: BitcoinAddressError()
    }

    fun parseBitcoinAddress(
        input: String
    ): Either<BitcoinAddressError, BitcoinAddressInfo> {

        // Ignore excess input, including additional lines, and whitespace
        var addr = input.lines().firstOrNull { it.isNotBlank() }?.let {
            it
                .replace("\\u00A0", "") // \u00A0 = 'non-breaking space'
                .trim() // leading & trailing whitespace
        } ?: ""
        val (prefixLength, isUrl) = when {
            addr.startsWith("bitcoin://", true) -> Pair(10, true)
            addr.startsWith("bitcoin:", true) -> Pair(8, true)
            else -> Pair(0, false)
        }
        var parameters = Parameters.Empty
        if (isUrl) {
            // The input might look like:
            // bitcoin:tb1qla78tll0eua3l5f4nvfq3tx58u35yc3m44flfu?time=1618931109&exp=604800
            // We want to parse the parameters too.
            try {
                val url = Url(addr)
                parameters = url.parameters
                // Can we extract the bitcoin address string from the Url ?
                // Surprisingly, the Url api lacks a simple property for this:
                // - url.host == localhost
                // - url.authority == localhost
                // - url.encodedPath = "/tb1..."
                addr = addr.drop(prefixLength)
                val idx = addr.indexOf("?")
                if (idx >= 0) {
                    addr = addr.dropLast(addr.length - idx)
                }
            } catch (e: Throwable) {
                return Either.Left(BitcoinAddressError.UnknownFormat)
            }
        }

        try { // is Base58 ?
            val (prefix, bin) = Base58Check.decode(addr)

            // BUG in Kotlin ?!?! :
            //
            // val tuple = when (foo) {
            //   "bar" -> Pair("a", "b")
            //   else -> null
            // }
            //
            // The type of `tuple` should be: `Pair<String, String>?` - an OPTIONAL Pair
            // But it's NOT ! It's actually: `Pair<String, String>`
            //
            // That `else` case is seemingly ignored by the compiler.

            val (addrChain, type) = when (prefix) {
                Base58.Prefix.PubkeyAddress -> Pair(Chain.Mainnet, BitcoinAddressType.Base58PubKeyHash)
                Base58.Prefix.ScriptAddress -> Pair(Chain.Mainnet, BitcoinAddressType.Base58ScriptHash)
                Base58.Prefix.PubkeyAddressTestnet -> Pair(Chain.Testnet, BitcoinAddressType.Base58PubKeyHash)
                Base58.Prefix.ScriptAddressTestnet -> Pair(Chain.Testnet, BitcoinAddressType.Base58ScriptHash)
                Base58.Prefix.PubkeyAddressSegnet -> Pair(Chain.Regtest, BitcoinAddressType.Base58PubKeyHash)
                Base58.Prefix.ScriptAddressSegnet -> Pair(Chain.Regtest, BitcoinAddressType.Base58ScriptHash)
                else -> Pair(null, null)
            }
            if (addrChain == null || type == null) {
                return Either.Left(BitcoinAddressError.UnknownBase58Prefix(prefix))
            }
            if (addrChain != chain) {
                return Either.Left(BitcoinAddressError.ChainMismatch(chain, addrChain))
            }
            return Either.Right(BitcoinAddressInfo(
                address = addr,
                chain = addrChain,
                type = type,
                hash = bin.byteVector(),
                params = parameters
            ))
        } catch (e: Throwable) {
            // Not Base58Check
        }

        try { // is Bech32 ?
            val (hrp, version, bin) = Bech32.decodeWitnessAddress(addr)
            val addrChain = when (hrp) {
                "bc" -> Chain.Mainnet
                "tb" -> Chain.Testnet
                "bcrt" -> Chain.Regtest
                else -> null
            } ?: return Either.Left(BitcoinAddressError.UnknownBech32Prefix(hrp))
            if (addrChain != chain) {
                return Either.Left(BitcoinAddressError.ChainMismatch(chain, addrChain))
            }
            if (version == 0.toByte()) {
                val type = when (bin.size) {
                    20 -> BitcoinAddressType.SegWitPubKeyHash
                    32 -> BitcoinAddressType.SegWitScriptHash
                    else -> null
                } ?: return Either.Left(BitcoinAddressError.UnknownFormat)
                return Either.Right(BitcoinAddressInfo(
                    address = addr,
                    chain = addrChain,
                    type = type,
                    hash = bin.byteVector(),
                    params = parameters
                ))
            } else {
                // Unknown version - we don't have any validation logic in place for it
                return Either.Left(BitcoinAddressError.UnknownBech32Version(version))
            }
        } catch (e: Throwable) {
            // Not Bech32
        }

        return Either.Left(BitcoinAddressError.UnknownFormat)
    }

    fun addressToPublicKeyScript(address: String): ByteArray? {
        val info = parseBitcoinAddress(address).right ?: return null
        val script = when (info.type) {
            BitcoinAddressType.Base58PubKeyHash -> Script.pay2pkh(
                pubKeyHash = info.hash.toByteArray()
            )
            BitcoinAddressType.Base58ScriptHash -> {
                // We cannot use Script.pay2sh() here, because that function expects a script.
                // And what we have is a script hash.
                listOf(OP_HASH160, OP_PUSHDATA(info.hash), OP_EQUAL)
            }
            BitcoinAddressType.SegWitPubKeyHash -> Script.pay2wpkh(
                pubKeyHash = info.hash.toByteArray()
            )
            BitcoinAddressType.SegWitScriptHash -> {
                // We cannot use Script.pay2wsh() here, because that function expects a script.
                // And what we have is a script hash.
                listOf(OP_0, OP_PUSHDATA(info.hash))
            }
        }
        return Script.write(script)
    }
}