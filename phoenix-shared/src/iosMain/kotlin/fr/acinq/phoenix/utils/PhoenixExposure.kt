package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.KeyPath
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.crypto.div
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.NodeParamsManager

/**
 * Workarounds for various shortcomings between Kotlin and iOS.
 */

/**
 * `id` is a reserved variable in objective-c,
 * so we can't properly access it from within iOS.
 */
fun WalletPaymentOrderRow.kotlinId(): WalletPaymentId {
    return this.id
}

fun NodeParamsManager.Companion._liquidityLeaseRate(amount: Satoshi): LiquidityAds_LeaseRate {
    val result = this.liquidityLeaseRate(amount)
    return LiquidityAds_LeaseRate(result)
}

/**
 * KeyManager.Bip84OnChainKeys.xpriv is private...
 */
fun PhoenixBusiness.walletManager_finalOnChainWallet_xprv(
    mnemonics: List<String>,
    wordList: List<String>,
    passphrase: String = ""
): String? {
    val keyManager = walletManager.keyManager.value ?: return null
    val seed = walletManager.mnemonicsToSeed(
        mnemonics = mnemonics,
        wordList = wordList,
        passphrase = passphrase
    )
    val master = DeterministicWallet.generate(seed)
    val bip84Path = KeyManager.Bip84OnChainKeys.bip84BasePath(chain)
    val xprvPath = bip84Path / DeterministicWallet.hardened(0)
    val xprv = DeterministicWallet.derivePrivateKey(master, xprvPath)
    val xpub = DeterministicWallet.publicKey(xprv)
    val (xpubPrefix, xprvPrefix) = when (chain) {
        Chain.Testnet, Chain.Regtest, Chain.Signet ->
            Pair(DeterministicWallet.vpub, DeterministicWallet.vprv)
        Chain.Mainnet ->
            Pair(DeterministicWallet.zpub, DeterministicWallet.zprv)
    }
    val xpubStr = DeterministicWallet.encode(xpub, xpubPrefix)
    // Safety check: If anything has changed in lightning-kmp, this test will fail
    return if (xpubStr == keyManager.finalOnChainWallet.xpub) {
        DeterministicWallet.encode(xprv, xprvPrefix)
    } else {
        null
    }
}