/*
 * Copyright 2024 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION")

package fr.acinq.phoenix.android.utils.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.utils.extensions.desc

@Composable
fun LightningOutgoingPayment.smartDescription(): String? = when (val details = this.details) {
    is LightningOutgoingPayment.Details.Normal -> details.paymentRequest.desc
    is LightningOutgoingPayment.Details.SwapOut -> stringResource(id = R.string.paymentdetails_desc_swapout, details.address)
    is LightningOutgoingPayment.Details.Blinded -> details.paymentRequest.description
}?.takeIf { it.isNotBlank() }

@Composable
fun SpliceOutgoingPayment.smartDescription(): String = stringResource(id = R.string.paymentdetails_desc_splice_out)

@Composable
fun SpliceCpfpOutgoingPayment.smartDescription(): String = stringResource(id = R.string.paymentdetails_desc_cpfp)

@Composable
fun ChannelCloseOutgoingPayment.smartDescription(): String = stringResource(id = R.string.paymentdetails_desc_closing_channel)

@Composable
fun ManualLiquidityPurchasePayment.smartDescription(): String =
    stringResource(id = R.string.paymentdetails_desc_liquidity_manual, liquidityPurchase.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true))

@Composable
fun AutomaticLiquidityPurchasePayment.smartDescription(): String =
    stringResource(id = R.string.paymentdetails_desc_liquidity_automated, liquidityPurchase.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true))

@Suppress("DEPRECATION")
@Composable
fun IncomingPayment.smartDescription() : String? = when (this) {
    is Bolt11IncomingPayment -> paymentRequest.description
    is Bolt12IncomingPayment -> null
    is OnChainIncomingPayment -> stringResource(id = R.string.paymentdetails_desc_swapin)
    is LegacySwapInIncomingPayment -> stringResource(id = R.string.paymentdetails_desc_swapin)
    is LegacyPayToOpenIncomingPayment -> when (val origin = origin) {
        is LegacyPayToOpenIncomingPayment.Origin.Invoice -> origin.paymentRequest.description
        is LegacyPayToOpenIncomingPayment.Origin.Offer -> null
    }
}?.takeIf { it.isNotBlank() }

/**
 * Returns a trimmed, localized description of the payment, based on the type and information available. May be null!
 *
 * For example, a payment closing a channel has no description, and it's up to us to create one. Others like a LN
 * payment with an invoice do have a description baked in, and that's what is returned.
 */
@Composable
fun WalletPayment.smartDescription(): String? = when (this) {
    is LightningOutgoingPayment -> smartDescription()
    is IncomingPayment -> smartDescription()
    is ChannelCloseOutgoingPayment -> smartDescription()
    is SpliceOutgoingPayment -> smartDescription()
    is SpliceCpfpOutgoingPayment -> smartDescription()
    is ManualLiquidityPurchasePayment -> smartDescription()
    is AutomaticLiquidityPurchasePayment -> smartDescription()
}

@Suppress("DEPRECATION")
fun WalletPayment.basicDescription(): String? = when (this) {
    is Bolt11IncomingPayment -> paymentRequest.description?.takeIf { it.isNotBlank() }
    is LegacyPayToOpenIncomingPayment -> when (val origin = origin) {
        is LegacyPayToOpenIncomingPayment.Origin.Invoice -> origin.paymentRequest.description
        is LegacyPayToOpenIncomingPayment.Origin.Offer -> null
    }
    is IncomingPayment -> null
    is LightningOutgoingPayment -> when (val details = this.details) {
        is LightningOutgoingPayment.Details.Normal -> details.paymentRequest.desc
        is LightningOutgoingPayment.Details.SwapOut -> null
        is LightningOutgoingPayment.Details.Blinded -> details.paymentRequest.description
    }
    is ChannelCloseOutgoingPayment -> null
    is SpliceOutgoingPayment -> null
    is SpliceCpfpOutgoingPayment -> null
    is ManualLiquidityPurchasePayment -> null
    is AutomaticLiquidityPurchasePayment -> null
}?.takeIf { it.isNotBlank() }
