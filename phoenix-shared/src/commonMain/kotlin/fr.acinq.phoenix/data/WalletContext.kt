package fr.acinq.phoenix.data

/** Contains contextual information for the wallet, fetched from https://acinq.co/phoenix/walletcontext.json. */
data class WalletContext(
    val isMempoolFull: Boolean,
    val androidLatestVersion: Int,
    val androidLatestCriticalVersion: Int,
)
