package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.availableForReceive
import fr.acinq.phoenix.data.canRequestLiquidity
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.NodeParamsManager

/**
 * Workarounds for various shortcomings between Kotlin and iOS.
 */

/**
 * `id` is a reserved variable in objective-c,
 * so we can't properly access it from within iOS.
 */
fun ContactInfo.kotlinId(): UUID {
    return this.id
}
fun WalletPaymentOrderRow.kotlinId(): WalletPaymentId {
    return this.id
}

fun NodeParamsManager.Companion._liquidityLeaseRate(amount: Satoshi): LiquidityAds_LeaseRate {
    val result = this.liquidityLeaseRate(amount)
    return LiquidityAds_LeaseRate(result)
}

fun LocalChannelInfo.Companion.availableForReceive(
    channels: List<LocalChannelInfo>
): MilliSatoshi? {
    return channels.availableForReceive()
}

fun LocalChannelInfo.Companion.canRequestLiquidity(
    channels: List<LocalChannelInfo>
): Boolean {
    return channels.canRequestLiquidity()
}

fun LocalChannelInfo.Companion.inFlightPaymentsCount(
    channels: List<LocalChannelInfo>
): Int {
    return channels.inFlightPaymentsCount()
}