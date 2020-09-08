package fr.acinq.phoenix.data

import kotlinx.serialization.Serializable
import org.kodein.db.model.orm.Metadata

@Serializable
data class AppConfiguration(
    // Unique ID a their is only one configuration per app
    override val id: Int = 0,
    // Global
    val chain: Chain = Chain.TESTNET,
    // Display
    val fiatCurrency: FiatCurrency = FiatCurrency.USD,
    val bitcoinUnit: BitcoinUnit = BitcoinUnit.Satoshi,
    val appTheme: AppTheme = AppTheme.System,
    // Electrum Server
    val electrumServer: String = ""
) : Metadata


enum class Chain { MAINNET, TESTNET }

@Serializable
enum class BitcoinUnit(val label: String) {
    Satoshi("Satoshi (0.00000001 BTC)"),
    Bits("Bits (0.000001 BTC)"),
    MilliBitcoin("Milli-Bitcoin (0.001 BTC)"),
    Bitcoin("Bitcoin");

    companion object default {
        val values = listOf(Satoshi, Bits, MilliBitcoin, Bitcoin)
    }
}

@Serializable
enum class AppTheme(val label: String) {
     Dark("Dark theme"),
     Light("Light theme"),
     System("System default");

    companion object default {
        val values = listOf(Dark, Light, System)
    }
}

@Serializable
enum class FiatCurrency(val label: String) {
    AUD("(AUD) Australian Dollar"),
    BRL("(BRL) Brazilian Real"),
    CAD("(CAD) Canadian Dollar"),
    CHF("(CHF) Swiss Franc"),
    CLP("(CLP) Chilean Peso"),
    CNY("(CNY) Chinese Yuan"),
    DKK("(DKK) Danish Krone"),
    EUR("(EUR) Euro"),
    GBP("(GBP) Great British Pound"),
    HKD("(HKD) Hong Kong Dollar"),
    INR("(INR) Indian Rupee"),
    ISK("(ISK) Icelandic Kròna"),
    JPY("(JPY) Japanese Yen"),
    KRW("(KRW) Korean Won"),
    MXN("(MXN) Mexican Peso"),
    NZD("(NZD) New Zealand Dollar"),
    PLN("(PLN) Polish Zloty"),
    RUB("(RUB) Russian Ruble"),
    SEK("(SEK) Swedish Krona"),
    SGD("(SGD) Singapore Dollar"),
    THB("(THB) Thai Baht"),
    TWD("(TWD) Taiwan New Dollar"),
    USD("(USD) United States Dollar");

    companion object default {
        val values = listOf(
            AUD, BRL, CAD, CHF, CLP, CNY, DKK, EUR, GBP, HKD,
            INR, ISK, JPY, KRW, MXN, NZD, PLN, RUB, SEK, SGD,
            THB, TWD, USD
        )
    }
}
