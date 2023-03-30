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
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import java.security.cert.CertificateException
import java.util.*

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
    val code = this.name
    val context = LocalContext.current
    return remember(key1 = code) {
        val fullName = when {
            code.length == 3 -> try {
                Currency.getInstance(code).displayName
            } catch (e: Exception) {
                "N/A"
            }
            code == "ARS_BM" -> context.getString(R.string.currency_ars_bm)
            code == "CUP_FM" -> context.getString(R.string.currency_cup_fm)
            code == "LBP_BM" -> context.getString(R.string.currency_lbp_bm)
            else -> "N/A"
        }
        val flag = getFlag(code)
        "$flag $code" to fullName
    }
}

private fun getFlag(code: String): String {
    return when (code) {
        "AED" -> "🇦🇪" // United Arab Emirates Dirham
        "AFN" -> "🇦🇫" // Afghan Afghani
        "ALL" -> "🇦🇱" // Albanian Lek
        "AMD" -> "🇦🇲" // Armenian Dram
        "ANG" -> "🇳🇱" // Netherlands Antillean Guilder
        "AOA" -> "🇦🇴" // Angolan Kwanza
        "ARS_BM" -> "🇦🇷" // Argentine Peso (blue market)
        "ARS" -> "🇦🇷" // Argentine Peso
        "AUD" -> "🇦🇺" // Australian Dollar
        "AWG" -> "🇦🇼" // Aruban Florin
        "AZN" -> "🇦🇿" // Azerbaijani Manat
        "BAM" -> "🇧🇦" // Bosnia-Herzegovina Convertible Mark
        "BBD" -> "🇧🇧" // Barbadian Dollar
        "BDT" -> "🇧🇩" // Bangladeshi Taka
        "BGN" -> "🇧🇬" // Bulgarian Lev
        "BHD" -> "🇧🇭" // Bahraini Dinar
        "BIF" -> "🇧🇮" // Burundian Franc
        "BMD" -> "🇧🇲" // Bermudan Dollar
        "BND" -> "🇧🇳" // Brunei Dollar
        "BOB" -> "🇧🇴" // Bolivian Boliviano
        "BRL" -> "🇧🇷" // Brazilian Real
        "BSD" -> "🇧🇸" // Bahamian Dollar
        "BTN" -> "🇧🇹" // Bhutanese Ngultrum
        "BWP" -> "🇧🇼" // Botswanan Pula
        "BZD" -> "🇧🇿" // Belize Dollar
        "CAD" -> "🇨🇦" // Canadian Dollar
        "CDF" -> "🇨🇩" // Congolese Franc
        "CHF" -> "🇨🇭" // Swiss Franc
        "CLP" -> "🇨🇱" // Chilean Peso
        "CNH" -> "🇨🇳" // Chinese Yuan (offshore)
        "CNY" -> "🇨🇳" // Chinese Yuan (onshore)
        "COP" -> "🇨🇴" // Colombian Peso
        "CRC" -> "🇨🇷" // Costa Rican Colón
        "CUP" -> "🇨🇺" // Cuban Peso
        "CUP_FM" -> "🇨🇺" // Cuban Peso (free market)
        "CVE" -> "🇨🇻" // Cape Verdean Escudo
        "CZK" -> "🇨🇿" // Czech Koruna
        "DJF" -> "🇩🇯" // Djiboutian Franc
        "DKK" -> "🇩🇰" // Danish Krone
        "DOP" -> "🇩🇴" // Dominican Peso
        "DZD" -> "🇩🇿" // Algerian Dinar
        "EGP" -> "🇪🇬" // Egyptian Pound
        "ERN" -> "🇪🇷" // Eritrean Nakfa
        "ETB" -> "🇪🇹" // Ethiopian Birr
        "EUR" -> "🇪🇺" // Euro
        "FJD" -> "🇫🇯" // Fijian Dollar
        "FKP" -> "🇫🇰" // Falkland Islands Pound
        "GBP" -> "🇬🇧" // British Pound Sterling
        "GEL" -> "🇬🇪" // Georgian Lari
        "GHS" -> "🇬🇭" // Ghanaian Cedi
        "GIP" -> "🇬🇮" // Gibraltar Pound
        "GMD" -> "🇬🇲" // Gambian Dalasi
        "GNF" -> "🇬🇳" // Guinean Franc
        "GTQ" -> "🇬🇹" // Guatemalan Quetzal
        "GYD" -> "🇬🇾" // Guyanaese Dollar
        "HKD" -> "🇭🇰" // Hong Kong Dollar
        "HNL" -> "🇭🇳" // Honduran Lempira
        "HRK" -> "🇭🇷" // Croatian Kuna
        "HTG" -> "🇭🇹" // Haitian Gourde
        "HUF" -> "🇭🇺" // Hungarian Forint
        "IDR" -> "🇮🇩" // Indonesian Rupiah
        "ILS" -> "🇮🇱" // Israeli New Sheqel
        "INR" -> "🇮🇳" // Indian Rupee
        "IQD" -> "🇮🇶" // Iraqi Dinar
        "IRR" -> "🇮🇷" // Iranian Rial
        "ISK" -> "🇮🇸" // Icelandic Króna
        "JEP" -> "🇯🇪" // Jersey Pound
        "JMD" -> "🇯🇲" // Jamaican Dollar
        "JOD" -> "🇯🇴" // Jordanian Dinar
        "JPY" -> "🇯🇵" // Japanese Yen
        "KES" -> "🇰🇪" // Kenyan Shilling
        "KGS" -> "🇰🇬" // Kyrgystani Som
        "KHR" -> "🇰🇭" // Cambodian Riel
        "KMF" -> "🇰🇲" // Comorian Franc
        "KPW" -> "🇰🇵" // North Korean Won
        "KRW" -> "🇰🇷" // South Korean Won
        "KWD" -> "🇰🇼" // Kuwaiti Dinar
        "KYD" -> "🇰🇾" // Cayman Islands Dollar
        "KZT" -> "🇰🇿" // Kazakhstani Tenge
        "LAK" -> "🇱🇦" // Laotian Kip
        "LBP" -> "🇱🇧" // Lebanese Pound
        "LBP_BM" -> "🇱🇧" // Lebanese Pound
        "LKR" -> "🇱🇰" // Sri Lankan Rupee
        "LRD" -> "🇱🇷" // Liberian Dollar
        "LSL" -> "🇱🇸" // Lesotho Loti
        "LYD" -> "🇱🇾" // Libyan Dinar
        "MAD" -> "🇲🇦" // Moroccan Dirham
        "MDL" -> "🇲🇩" // Moldovan Leu
        "MGA" -> "🇲🇬" // Malagasy Ariary
        "MKD" -> "🇲🇰" // Macedonian Denar
        "MMK" -> "🇲🇲" // Myanmar Kyat
        "MNT" -> "🇲🇳" // Mongolian Tugrik
        "MOP" -> "🇲🇴" // Macanese Pataca
        "MUR" -> "🇲🇺" // Mauritian Rupee
        "MVR" -> "🇲🇻" // Maldivian Rufiyaa
        "MWK" -> "🇲🇼" // Malawian Kwacha
        "MXN" -> "🇲🇽" // Mexican Peso
        "MYR" -> "🇲🇾" // Malaysian Ringgit
        "MZN" -> "🇲🇿" // Mozambican Metical
        "NAD" -> "🇳🇦" // Namibian Dollar
        "NGN" -> "🇳🇬" // Nigerian Naira
        "NIO" -> "🇳🇮" // Nicaraguan Córdoba
        "NOK" -> "🇳🇴" // Norwegian Krone
        "NPR" -> "🇳🇵" // Nepalese Rupee
        "NZD" -> "🇳🇿" // New Zealand Dollar
        "OMR" -> "🇴🇲" // Omani Rial
        "PAB" -> "🇵🇦" // Panamanian Balboa
        "PEN" -> "🇵🇪" // Peruvian Nuevo Sol
        "PGK" -> "🇵🇬" // Papua New Guinean Kina
        "PHP" -> "🇵🇭" // Philippine Peso
        "PKR" -> "🇵🇰" // Pakistani Rupee
        "PLN" -> "🇵🇱" // Polish Zloty
        "PYG" -> "🇵🇾" // Paraguayan Guarani
        "QAR" -> "🇶🇦" // Qatari Rial
        "RON" -> "🇷🇴" // Romanian Leu
        "RSD" -> "🇷🇸" // Serbian Dinar
        "RUB" -> "🇷🇺" // Russian Ruble
        "RWF" -> "🇷🇼" // Rwandan Franc
        "SAR" -> "🇸🇦" // Saudi Riyal
        "SBD" -> "🇸🇧" // Solomon Islands Dollar
        "SCR" -> "🇸🇨" // Seychellois Rupee
        "SDG" -> "🇸🇩" // Sudanese Pound
        "SEK" -> "🇸🇪" // Swedish Krona
        "SGD" -> "🇸🇬" // Singapore Dollar
        "SHP" -> "🇸🇭" // Saint Helena Pound
        "SLL" -> "🇸🇱" // Sierra Leonean Leone
        "SOS" -> "🇸🇴" // Somali Shilling
        "SRD" -> "🇸🇷" // Surinamese Dollar
        "SYP" -> "🇸🇾" // Syrian Pound
        "SZL" -> "🇸🇿" // Swazi Lilangeni
        "THB" -> "🇹🇭" // Thai Baht
        "TJS" -> "🇹🇯" // Tajikistani Somoni
        "TMT" -> "🇹🇲" // Turkmenistani Manat
        "TND" -> "🇹🇳" // Tunisian Dinar
        "TOP" -> "🇹🇴" // Tongan Paʻanga
        "TRY" -> "🇹🇷" // Turkish Lira
        "TTD" -> "🇹🇹" // Trinidad and Tobago Dollar
        "TWD" -> "🇹🇼" // New Taiwan Dollar
        "TZS" -> "🇹🇿" // Tanzanian Shilling
        "UAH" -> "🇺🇦" // Ukrainian Hryvnia
        "UGX" -> "🇺🇬" // Ugandan Shilling
        "USD" -> "🇺🇸" // United States Dollar
        "UYU" -> "🇺🇾" // Uruguayan Peso
        "UZS" -> "🇺🇿" // Uzbekistan Som
        "VND" -> "🇻🇳" // Vietnamese Dong
        "VUV" -> "🇻🇺" // Vanuatu Vatu
        "WST" -> "🇼🇸" // Samoan Tala
        "XAF" -> "🇨🇲" // CFA Franc BEAC        - multiple options, chose country with highest GDP
        "XCD" -> "🇱🇨" // East Caribbean Dollar - multiple options, chose country with highest GDP
        "XOF" -> "🇨🇮" // CFA Franc BCEAO       - multiple options, chose country with highest GDP
        "XPF" -> "🇳🇨" // CFP Franc             - multiple options, chose country with highest GDP
        "YER" -> "🇾🇪" // Yemeni Rial
        "ZAR" -> "🇿🇦" // South African Rand
        "ZMW" -> "🇿🇲" // Zambian Kwacha
        else -> "🏳️"
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
    is OutgoingPayment -> when (val details = this.details) {
        is OutgoingPayment.Details.Normal -> details.paymentRequest.description ?: details.paymentRequest.descriptionHash?.toHex()
        is OutgoingPayment.Details.ChannelClosing -> context.getString(R.string.paymentdetails_desc_closing_channel)
        is OutgoingPayment.Details.KeySend -> context.getString(R.string.paymentdetails_desc_keysend)
        is OutgoingPayment.Details.SwapOut -> context.getString(R.string.paymentdetails_desc_swapout, details.address)
    }
    is IncomingPayment -> when (val origin = this.origin) {
        is IncomingPayment.Origin.Invoice -> origin.paymentRequest.description ?: origin.paymentRequest.descriptionHash?.toHex()
        is IncomingPayment.Origin.KeySend -> context.getString(R.string.paymentdetails_desc_keysend)
        is IncomingPayment.Origin.SwapIn, is IncomingPayment.Origin.DualSwapIn -> context.getString(R.string.paymentdetails_desc_swapin)
    }
    else -> null
}?.takeIf { it.isNotBlank() }