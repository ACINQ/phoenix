/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.app.*
import android.content.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.utils.extensions.desc
import java.security.cert.CertificateException
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Utility method rebinding any exceptions thrown by a method into another exception, using the origin exception as the root cause.
 * Helps with pattern matching.
 */
inline fun <T> tryWith(exception: Exception, action: () -> T): T = try {
    action.invoke()
} catch (t: Exception) {
    exception.initCause(t)
    throw exception
}

inline fun <T1 : Any, T2 : Any, R : Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2) -> R?): R? {
    return if (p1 != null && p2 != null) block(p1, p2) else null
}

@OptIn(ExperimentalContracts::class)
inline fun <T, R> T.ifLet(block: (T) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(this)
}

fun Context.findActivity(): MainActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is MainActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("not in the context of the main Phoenix activity")
}

@Composable
fun BitcoinUnit.label(): String = when (this) {
    BitcoinUnit.Sat -> stringResource(id = R.string.prefs_display_coin_sat_label)
    BitcoinUnit.Bit -> stringResource(id = R.string.prefs_display_coin_bit_label)
    BitcoinUnit.MBtc -> stringResource(id = R.string.prefs_display_coin_mbtc_label)
    BitcoinUnit.Btc -> stringResource(id = R.string.prefs_display_coin_btc_label)
}

@Composable
fun FiatCurrency.labels(): Pair<String, String> {
    val context = LocalContext.current
    return remember(key1 = displayCode) {
        val fullName = when {
            // use the free market rates as default. Name for official rates gets a special tag, as those rates are usually inaccurate.
            this == FiatCurrency.ARS -> context.getString(R.string.currency_ars_official)
            this == FiatCurrency.ARS_BM -> context.getString(R.string.currency_ars_bm)
            this == FiatCurrency.CUP -> context.getString(R.string.currency_cup_official)
            this == FiatCurrency.CUP_FM -> context.getString(R.string.currency_cup_fm)
            this == FiatCurrency.LBP -> context.getString(R.string.currency_lbp_official)
            this == FiatCurrency.LBP_BM -> context.getString(R.string.currency_lbp_bm)
            // use the JVM API otherwise to get the name
            displayCode.length == 3 -> try {
                Currency.getInstance(displayCode).displayName
            } catch (e: Exception) {
                "N/A"
            }
            else -> "N/A"
        }
        "$flag $displayCode" to fullName
    }
}

@Composable
fun UserTheme.label(): String {
    val context = LocalContext.current
    return remember(key1 = this.name) {
        when (this) {
            UserTheme.DARK -> context.getString(R.string.prefs_display_theme_dark_label)
            UserTheme.LIGHT -> context.getString(R.string.prefs_display_theme_light_label)
            UserTheme.SYSTEM -> context.getString(R.string.prefs_display_theme_system_label)
        }
    }
}

fun Connection.CLOSED.isBadCertificate() = this.reason?.cause is CertificateException

/**
 * Returns a trimmed, localized description of the payment, based on the type and information available. May be null!
 *
 * For example, a payment closing a channel has no description, and it's up to us to create one. Others like a LN
 * payment with an invoice do have a description baked in, and that's what is returned.
 */
fun WalletPayment.smartDescription(context: Context): String? = when (this) {
    is LightningOutgoingPayment -> when (val details = this.details) {
        is LightningOutgoingPayment.Details.Normal -> details.paymentRequest.desc
        is LightningOutgoingPayment.Details.SwapOut -> context.getString(R.string.paymentdetails_desc_swapout, details.address)
        is LightningOutgoingPayment.Details.Blinded -> details.paymentRequest.description
    }
    is IncomingPayment -> when (val origin = this.origin) {
        is IncomingPayment.Origin.Invoice -> origin.paymentRequest.description
        is IncomingPayment.Origin.KeySend -> context.getString(R.string.paymentdetails_desc_keysend)
        is IncomingPayment.Origin.SwapIn, is IncomingPayment.Origin.OnChain -> context.getString(R.string.paymentdetails_desc_swapin)
        is IncomingPayment.Origin.Offer -> context.getString(R.string.paymentdetails_desc_offer_incoming, origin.metadata.offerId.toHex())
    }
    is SpliceOutgoingPayment -> context.getString(R.string.paymentdetails_desc_splice_out)
    is ChannelCloseOutgoingPayment -> context.getString(R.string.paymentdetails_desc_closing_channel)
    is SpliceCpfpOutgoingPayment -> context.getString(R.string.paymentdetails_desc_cpfp)
    is InboundLiquidityOutgoingPayment -> context.getString(R.string.paymentdetails_desc_inbound_liquidity, lease.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true))
}?.takeIf { it.isNotBlank() }