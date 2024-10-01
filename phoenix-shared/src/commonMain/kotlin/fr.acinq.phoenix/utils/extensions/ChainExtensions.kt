package fr.acinq.phoenix.utils.extensions

import fr.acinq.bitcoin.Chain

val Chain.phoenixName: String
    get() = when (this) {
        Chain.Regtest -> "regtest"
        Chain.Signet -> "signet"
    //  Chain.Testnet -> "testnet"
        Chain.Testnet3 -> "testnet"
        Chain.Testnet4 -> "testnet4"
        Chain.Mainnet -> "mainnet"
    }