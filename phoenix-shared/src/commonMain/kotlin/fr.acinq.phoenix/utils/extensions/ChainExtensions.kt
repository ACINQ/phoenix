package fr.acinq.phoenix.utils.extensions

import fr.acinq.bitcoin.Chain

/**
 * Value used by Phoenix for naming files relative to the [Chain].
 * Specifically, testnet3 name must be "testnet", for historical reasons.
 */
val Chain.phoenixName: String
    get() = when (this) {
        Chain.Regtest -> "regtest"
        Chain.Signet -> "signet"
        Chain.Testnet3 -> "testnet"
        Chain.Testnet4 -> "testnet4"
        Chain.Mainnet -> "mainnet"
    }