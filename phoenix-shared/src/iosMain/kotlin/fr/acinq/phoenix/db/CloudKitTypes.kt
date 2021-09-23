package fr.acinq.phoenix.db

import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.db.cloud.CloudData

fun CloudData.walletPayment(): WalletPayment? = when {
    incoming != null -> incoming.unwrap()
    outgoing != null -> outgoing.unwrap()
    else -> null
}
