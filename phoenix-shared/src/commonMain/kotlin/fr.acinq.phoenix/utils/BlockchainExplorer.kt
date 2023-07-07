package fr.acinq.phoenix.utils

import fr.acinq.lightning.NodeParams


class BlockchainExplorer(private val chain: NodeParams.Chain) {

    sealed class Website(val base: String) {
        object MempoolSpace: Website("https://mempool.space")
        object BlockstreamInfo: Website("https://blockstream.info")
    }

    fun txUrl(txId: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    NodeParams.Chain.Mainnet -> "${website.base}/tx/$txId"
                    NodeParams.Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    NodeParams.Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    NodeParams.Chain.Mainnet -> "${website.base}/tx/$txId"
                    NodeParams.Chain.Testnet -> "${website.base}/testnet/tx/$txId"
                    NodeParams.Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
        }
    }

    fun addressUrl(addr: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    NodeParams.Chain.Mainnet -> "${website.base}/address/$addr"
                    NodeParams.Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    NodeParams.Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    NodeParams.Chain.Mainnet -> "${website.base}/address/$addr"
                    NodeParams.Chain.Testnet -> "${website.base}/testnet/address/$addr"
                    NodeParams.Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
        }
    }
}