package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Satoshi
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
