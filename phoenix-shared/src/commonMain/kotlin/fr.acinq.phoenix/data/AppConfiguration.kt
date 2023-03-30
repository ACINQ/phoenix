package fr.acinq.phoenix.data

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.InitTlv
import kotlinx.serialization.Serializable


sealed class Chain(val name: String, val block: Block) {
    object Regtest : Chain("Regtest", Block.RegtestGenesisBlock)
    object Testnet : Chain("Testnet", Block.TestnetGenesisBlock)
    object Mainnet : Chain("Mainnet", Block.LivenetGenesisBlock)

    fun isMainnet(): Boolean = this is Mainnet
    fun isTestnet(): Boolean = this is Testnet

    val chainHash by lazy { block.hash }
}

interface CurrencyUnit

@Serializable
enum class BitcoinUnit : CurrencyUnit {
    Sat, Bit, MBtc, Btc;

    override fun toString(): String {
        return super.toString().lowercase()
    }

    companion object {
        val values = values().toList()
    }
}

@Serializable
enum class FiatCurrency : CurrencyUnit {
    AED, // United Arab Emirates Dirham
    AFN, // Afghan Afghani
    ALL, // Albanian Lek
    AMD, // Armenian Dram
    ANG, // Netherlands Antillean Guilder
    AOA, // Angolan Kwanza
    ARS, // Argentine Peso
    ARS_BM, // Argentine Peso (blue market)
    AUD, // Australian Dollar
    AWG, // Aruban Florin
    AZN, // Azerbaijani Manat
    BAM, // Bosnia-Herzegovina Convertible Mark
    BBD, // Barbadian Dollar
    BDT, // Bangladeshi Taka
    BGN, // Bulgarian Lev
    BHD, // Bahraini Dinar
    BIF, // Burundian Franc
    BMD, // Bermudan Dollar
    BND, // Brunei Dollar
    BOB, // Bolivian Boliviano
    BRL, // Brazilian Real
    BSD, // Bahamian Dollar
    BTN, // Bhutanese Ngultrum
    BWP, // Botswanan Pula
    BZD, // Belize Dollar
    CAD, // Canadian Dollar
    CDF, // Congolese Franc
    CHF, // Swiss Franc
    CLP, // Chilean Peso
    CNH, // Chinese Yuan (offshore)
    CNY, // Chinese Yuan (onshore)
    COP, // Colombian Peso
    CRC, // Costa Rican Colón
    CUP, // Cuban Peso
    CUP_FM, // Cuban Peso (free market)
    CVE, // Cape Verdean Escudo
    CZK, // Czech Republic Koruna
    DJF, // Djiboutian Franc
    DKK, // Danish Krone
    DOP, // Dominican Peso
    DZD, // Algerian Dinar
    EGP, // Egyptian Pound
    ERN, // Eritrean Nakfa
    ETB, // Ethiopian Birr
    EUR, // Euro
    FJD, // Fijian Dollar
    FKP, // Falkland Islands Pound
    GBP, // British Pound Sterling
    GEL, // Georgian Lari
    GHS, // Ghanaian Cedi
    GIP, // Gibraltar Pound
    GMD, // Gambian Dalasi
    GNF, // Guinean Franc
    GTQ, // Guatemalan Quetzal
    GYD, // Guyanaese Dollar
    HKD, // Hong Kong Dollar
    HNL, // Honduran Lempira
    HRK, // Croatian Kuna
    HTG, // Haitian Gourde
    HUF, // Hungarian Forint
    IDR, // Indonesian Rupiah
    ILS, // Israeli New Sheqel
    INR, // Indian Rupee
    IQD, // Iraqi Dinar
    IRR, // Iranian Rial
    ISK, // Icelandic Króna
    JEP, // Jersey Pound
    JMD, // Jamaican Dollar
    JOD, // Jordanian Dinar
    JPY, // Japanese Yen
    KES, // Kenyan Shilling
    KGS, // Kyrgystani Som
    KHR, // Cambodian Riel
    KMF, // Comorian Franc
    KPW, // North Korean Won
    KRW, // South Korean Won
    KWD, // Kuwaiti Dinar
    KYD, // Cayman Islands Dollar
    KZT, // Kazakhstani Tenge
    LAK, // Laotian Kip
    LBP, // Lebanese Pound
    LBP_BM, // Lebanese Pound (black market)
    LKR, // Sri Lankan Rupee
    LRD, // Liberian Dollar
    LSL, // Lesotho Loti
    LYD, // Libyan Dinar
    MAD, // Moroccan Dirham
    MDL, // Moldovan Leu
    MGA, // Malagasy Ariary
    MKD, // Macedonian Denar
    MMK, // Myanma Kyat
    MNT, // Mongolian Tugrik
    MOP, // Macanese Pataca
    MUR, // Mauritian Rupee
    MVR, // Maldivian Rufiyaa
    MWK, // Malawian Kwacha
    MXN, // Mexican Peso
    MYR, // Malaysian Ringgit
    MZN, // Mozambican Metical
    NAD, // Namibian Dollar
    NGN, // Nigerian Naira
    NIO, // Nicaraguan Córdoba
    NOK, // Norwegian Krone
    NPR, // Nepalese Rupee
    NZD, // New Zealand Dollar
    OMR, // Omani Rial
    PAB, // Panamanian Balboa
    PEN, // Peruvian Sol
    PGK, // Papua New Guinean Kina
    PHP, // Philippine Peso
    PKR, // Pakistani Rupee
    PLN, // Polish Zloty
    PYG, // Paraguayan Guarani
    QAR, // Qatari Rial
    RON, // Romanian Leu
    RSD, // Serbian Dinar
    RUB, // Russian Ruble
    RWF, // Rwandan Franc
    SAR, // Saudi Riyal
    SBD, // Solomon Islands Dollar
    SCR, // Seychellois Rupee
    SDG, // Sudanese Pound
    SEK, // Swedish Krona
    SGD, // Singapore Dollar
    SHP, // Saint Helena Pound
    SLL, // Sierra Leonean Leone
    SOS, // Somali Shilling
    SRD, // Surinamese Dollar
    SYP, // Syrian Pound
    SZL, // Swazi Lilangeni
    THB, // Thai Baht
    TJS, // Tajikistani Somoni
    TMT, // Turkmenistani Manat
    TND, // Tunisian Dinar
    TOP, // Tongan Paʻanga
    TRY, // Turkish Lira
    TTD, // Trinidad and Tobago Dollar
    TWD, // Taiwan Dollar
    TZS, // Tanzanian Shilling
    UAH, // Ukrainian Hryvnia
    UGX, // Ugandan Shilling
    USD, // United States Dollar
    UYU, // Uruguayan Peso
    UZS, // Uzbekistan Som
    VND, // Vietnamese Dong
    VUV, // Vanuatu Vatu
    WST, // Samoan Tala
    XAF, // CFA Franc BEAC
    XCD, // East Caribbean Dollar
    XOF, // CFA Franc BCEAO
    XPF, // CFP Franc
    YER, // Yemeni Rial
    ZAR, // South African Rand
    ZMW; // Zambian Kwacha

    companion object {
        val values = values().toList()
        fun valueOfOrNull(code: String): FiatCurrency? = try {
            valueOf(code)
        } catch (e: Exception) {
            null
        }
    }
}

sealed class ElectrumConfig {
    data class Custom(val server: ServerAddress) : ElectrumConfig()
    object Random : ElectrumConfig()

    override operator fun equals(other: Any?): Boolean {
        if (other !is ElectrumConfig) {
            return false
        }
        return when (this) {
            is Custom -> {
                when (other) {
                    is Custom -> this == other // custom =?= custom
                    is Random -> false         // custom != random
                }
            }
            is Random -> {
                when (other) {
                    is Custom -> false // random != custom
                    is Random -> true  // random == random
                }
            }
        }
    }
}

data class StartupParams(
    /** When true, we use a [InitTlv] to ask our peer whether there are legacy channels to reestablish for the legacy node id. */
    val requestCheckLegacyChannels: Boolean = false,
    /** Tor state must be defined before the node starts. */
    val isTorEnabled: Boolean,
    // TODO: add custom electrum address, fiat currencies, ...
)

object PaymentOptionsConstants {
    val minBaseFee: Satoshi = 0.sat
    val maxBaseFee: Satoshi = 100_000.sat
    const val minProportionalFeePercent: Double = 0.0 // 0%
    const val maxProportionalFeePercent: Double = 50.0 // 50%
}
