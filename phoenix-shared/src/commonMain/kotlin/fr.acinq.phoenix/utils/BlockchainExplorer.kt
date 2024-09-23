package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.TxId


class BlockchainExplorer(private val chain: Chain) {

    sealed class Website(val base: String) {
        object MempoolSpace: Website("https://mempool.space")
        object BlockstreamInfo: Website("https://blockstream.info")
    }

    fun txUrl(txId: TxId, website: Website = Website.MempoolSpace): String {
        return when (website) {
            Website.MempoolSpace -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/tx/$txId"
                    Chain.Testnet3 -> "${website.base}/testnet/tx/$txId"
                    Chain.Testnet4 -> "${website.base}/testnet4/tx/$txId"
                    Chain.Signet -> "${website.base}/signet/tx/$txId"
                    Chain.Regtest -> "${website.base}/_REGTEST_/tx/$txId"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/tx/$txId"
                    Chain.Testnet3 -> "${website.base}/testnet/tx/$txId"
                    Chain.Testnet4 -> "${website.base}/testnet4/tx/$txId"
                    Chain.Signet -> "${website.base}/signet/tx/$txId"
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
                    Chain.Testnet3 -> "${website.base}/testnet/address/$addr"
                    Chain.Testnet4 -> "${website.base}/testnet4/address/$addr"
                    Chain.Signet -> "${website.base}/signet/address/$addr"
                    Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
            Website.BlockstreamInfo -> {
                when (chain) {
                    Chain.Mainnet -> "${website.base}/address/$addr"
                    Chain.Testnet3 -> "${website.base}/testnet/address/$addr"
                    Chain.Testnet4 -> "${website.base}/testnet4/address/$addr"
                    Chain.Signet -> "${website.base}/signet/address/$addr"
                    Chain.Regtest -> "${website.base}/_REGTEST_/address/$addr"
                }
            }
        }
    }
}