package fr.acinq.phoenix.utils

import fr.acinq.phoenix.data.Chain

class BlockchainExplorer(private val chain: Chain) {

    sealed class Website {
        object MempoolSpace: Website()
        object BlockstreamInfo: Website()
    }

    fun txUrl(txId: String, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                val base = "https://mempool.space"
                when (chain) {
                    Chain.Mainnet -> "$base/tx/$txId"
                    Chain.Testnet -> "$base/testnet/tx/$txId"
                    Chain.Regtest -> "$base/_REGTEST_/tx/$txId"
                }
            }
            Website.BlockstreamInfo -> {
                val base = "https://blockstream.info"
                when (chain) {
                    Chain.Mainnet -> "$base/tx/$txId"
                    Chain.Testnet -> "$base/testnet/tx/$txId"
                    Chain.Regtest -> "$base/_REGTEST_/tx/$txId"
                }
            }
        }
    }
}