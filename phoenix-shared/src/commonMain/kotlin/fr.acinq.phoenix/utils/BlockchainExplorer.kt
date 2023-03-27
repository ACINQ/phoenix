package fr.acinq.phoenix.utils

import fr.acinq.phoenix.data.Chain

class BlockchainExplorer(private val chain: Chain) {

    sealed class Website(val base: String) {
        object MempoolSpace: Website("https://mempool.space")
        object BlockstreamInfo: Website("https://blockstream.info")
    }

    fun txUrl(txId: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/tx/$txId"
                    Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/tx/$txId"
                    Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
        }
    }

    fun addressUrl(addr: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/address/$addr"
                    Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/address/$addr"
                    Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
        }
    }
}