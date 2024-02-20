package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Bitcoin
import fr.acinq.bitcoin.TxId


class BlockchainExplorer(private val chain: Bitcoin.Chain) {

    sealed class Website(val base: String) {
        object MempoolSpace: Website("https://mempool.space")
        object BlockstreamInfo: Website("https://blockstream.info")
    }

    fun txUrl(txId: TxId, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    Bitcoin.Chain.Mainnet -> "${website.base}/tx/$txId"
                    Bitcoin.Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    Bitcoin.Chain.Signet -> "${website.base}/signet/tx/$txId"
                    Bitcoin.Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Bitcoin.Chain.Mainnet -> "${website.base}/tx/$txId"
                    Bitcoin.Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    Bitcoin.Chain.Signet -> "${website.base}/signet/tx/$txId"
                    Bitcoin.Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
        }
    }

    fun addressUrl(addr: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    Bitcoin.Chain.Mainnet -> "${website.base}/address/$addr"
                    Bitcoin.Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    Bitcoin.Chain.Signet -> "${website.base}/signet/address/$addr"
                    Bitcoin.Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Bitcoin.Chain.Mainnet -> "${website.base}/address/$addr"
                    Bitcoin.Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    Bitcoin.Chain.Signet -> "${website.base}/signet/address/$addr"
                    Bitcoin.Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
        }
    }
}