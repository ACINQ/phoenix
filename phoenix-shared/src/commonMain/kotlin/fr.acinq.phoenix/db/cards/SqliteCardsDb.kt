package fr.acinq.phoenix.db.cards

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.BoltCardInfo
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.payments.LnurlBase
import fr.acinq.phoenix.db.payments.LnurlMetadata
import fr.acinq.phoenix.db.payments.LnurlSuccessAction
import fr.acinq.phoenix.db.payments.PaymentsMetadataQueries
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.managers.global.CurrencyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SqliteCardsDb(
    val driver: SqlDriver,
    val database: PaymentsDatabase,
    val loggerFactory: LoggerFactory
): CoroutineScope by MainScope() {

    private val log = loggerFactory.newLogger(this::class)

    val cardQueries = BoltCardQueries(database)

    private val _cardsList = MutableStateFlow<List<BoltCardInfo>>(emptyList())
    val cardsList = _cardsList.asStateFlow()

    private val _cardsMap = MutableStateFlow<Map<UUID, BoltCardInfo>>(emptyMap())
    val cardsMap = _cardsMap.asStateFlow()

    init {
        launch {
            cardQueries.monitorCardsFlow(Dispatchers.Default).collect { list ->
                val newMap = list.associateBy { it.id }
                _cardsList.value = list
                _cardsMap.value = newMap
            }
        }
    }

    suspend fun listCards(): List<BoltCardInfo> = withContext(Dispatchers.Default) {
        cardQueries.listCards()
    }

    /**
     * Saves a new card, or updates an existing card.
     */
    suspend fun saveCard(card: BoltCardInfo) = withContext(Dispatchers.Default) {
        cardQueries.saveCard(card)
    }

    suspend fun deleteCard(cardId: UUID) = withContext(Dispatchers.Default) {
        cardQueries.deleteCard(cardId)
    }

    fun cardForId(cardId: UUID): BoltCardInfo? {
        return cardsMap.value[cardId]
    }

    data class CardPayments(
        val daily: List<WalletPaymentInfo>,
        val monthly: List<WalletPaymentInfo>
    ) {
        companion object {
            fun fromMonthly(monthly: List<WalletPaymentInfo>, startOfDay: Long): CardPayments {
                val daily = monthly.filter { (it.payment.completedAt ?: it.payment.createdAt) > startOfDay }
                return CardPayments(monthly = monthly, daily = daily)
            }
        }
    }

    suspend fun fetchCardPayments(cardId: UUID): CardPayments {
        val nowInstant = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val nowLDT = nowInstant.toLocalDateTime(timezone)

        val startOfMonth = LocalDateTime(
            year = nowLDT.year, month = nowLDT.month, day = 1,
            hour = 0, minute = 0, second = 0, nanosecond = 0
        )

        val monthly = fetchRecentCardPayments(
            cardId = cardId,
            minInstant = startOfMonth.toInstant(timezone)
        )

        val startOfDay = LocalDateTime(
            date = nowLDT.date,
            time = LocalTime(hour = 0, minute = 0)
        )
        val startOfDayMillis = startOfDay.toInstant(timezone).toEpochMilliseconds()

        return CardPayments.fromMonthly(
            monthly = monthly,
            startOfDay = startOfDayMillis
        )
    }

    private suspend fun fetchRecentCardPayments(
        cardId: UUID,
        minInstant: Instant
    ): List<WalletPaymentInfo> = withContext(Dispatchers.Default) {

        var done = false
        val maxBatchCount = 50
        var offset = 0
        val results = mutableListOf<WalletPaymentInfo>()
        do {
            val batch = database.paymentsOutgoingQueries.listRecentCardPayments(
                card_id = cardId.toString(),
                min_ts = minInstant.toEpochMilliseconds(),
                limit = maxBatchCount.toLong(),
                offset = offset.toLong(),
                mapper = ::mapOutgoingPaymentsAndMetadata
            ).executeAsList()
            results.addAll(batch)
            if (batch.size >= maxBatchCount) {
                offset += batch.size
            } else {
                done = true
            }
        } while (!done)

        results
    }

    data class CardAmounts(
        val daily: List<Info>,
        val monthly: List<Info>
    ) {
        data class Info(
            val paymentAmount: MilliSatoshi,
            val originalFiat: ExchangeRate.BitcoinPriceRate?
        )

        fun dailyBitcoinAmount() = MilliSatoshi(msat = daily.sumOf { it.paymentAmount.msat })
        fun monthlyBitcoinAmount() = MilliSatoshi(msat = monthly.sumOf { it.paymentAmount.msat })

        fun dailyFiatAmount(
            target: FiatCurrency,
            exchangeRates: List<ExchangeRate>
        ): Double {
            return calculateFiatAmount(daily, target, exchangeRates)
        }

        fun monthlyFiatAmount(
            target: FiatCurrency,
            exchangeRates: List<ExchangeRate>
        ): Double {
            return calculateFiatAmount(monthly, target, exchangeRates)
        }

        companion object {
            fun calculateFiatAmount(
                list: List<Info>,
                targetFiatCurrency: FiatCurrency,
                exchangeRates: List<ExchangeRate>
            ): Double {
                var totalAmt = 0.0
                val currentDstRate = CurrencyManager.exchangeRate(targetFiatCurrency, exchangeRates)

                list.forEach { row ->
                    var rowAmt = 0.0
                    row.originalFiat?.let { originalFiat ->
                        // For this payment, we stored the original fiat value.
                        // (Note that this should ALWAYS be the case.)
                        //
                        // We want to use this original value because
                        // it makes the most sense to the user.

                        if (originalFiat.fiatCurrency == targetFiatCurrency) {
                            // This is the common case.
                            // For example:
                            // - the user's preferred fiatCurrency is set to EUR
                            // - thus the stored originalFiat rates are in EUR
                            // - and their daily/monthly amounts are also in EUR

                            rowAmt = CurrencyManager.convertToFiat(row.paymentAmount, originalFiat)
                        } else {
                            // This is the uncommon case.
                            // E.g.
                            // - the stored originalFiat rate is in USD
                            // - but their daily/monthly amounts are in EUR
                            //
                            // To deal with this situation we're going to use the current exchange
                            // rates between USD & EUR to calculate the (approximate) original
                            // amount in EUR.
                            //
                            // For example:
                            // - paymentAmount = 0.1 BTC
                            // - originalFiat = BitcoinPriceRate(USD, 60_000)
                            // - rates = List<
                            //   BitcoinPriceRate(USD, 100_000),
                            //   BitcoinPriceRate(EUR, 94_738)
                            // >
                            //
                            // originalFiatAmount = 0.1 * 60_000 => 6_000 USD
                            // percent = 94_738 / 100_000 = 0.94738
                            // estimatedFiatAmount = 6_000 * 0.94738 = 5_684 EUR

                            val originalFiatAmount =
                                CurrencyManager.convertToFiat(row.paymentAmount, originalFiat)

                            val currentSrcRate = CurrencyManager.exchangeRate(
                                originalFiat.fiatCurrency,
                                exchangeRates
                            )
                            if (currentSrcRate != null && currentDstRate != null) {
                                val percent = currentDstRate.price / currentSrcRate.price
                                val estimatedFiatAmount = originalFiatAmount * percent

                                rowAmt = estimatedFiatAmount
                            }
                        }
                    }

                    if (rowAmt == 0.0) {
                        // We were unable to calculate `amt` using the `originalFiat` value.
                        // So we'll have to do it using the current exchange rates.
                        if (currentDstRate != null) {
                            rowAmt = CurrencyManager.convertToFiat(row.paymentAmount, currentDstRate)
                        }
                    }

                    totalAmt += rowAmt
                }

                return totalAmt
            }
        }
    }

    fun getCardAmounts(
        payments: CardPayments
    ): CardAmounts {

        val dailyPaymentIds = payments.daily.map { it.id }

        val daily: MutableList<CardAmounts.Info> = mutableListOf()
        val monthly: MutableList<CardAmounts.Info> = mutableListOf()
        payments.monthly.forEach { row ->
            val info = CardAmounts.Info(
                paymentAmount = row.payment.amount,
                originalFiat = row.metadata.originalFiat
            )
            monthly.add(info)
            if (dailyPaymentIds.contains(row.id)) {
                daily.add(info)
            }
        }

        return CardAmounts(
            daily = daily.toList(),
            monthly = monthly.toList()
        )
    }

    companion object {

        @Suppress("UNUSED_PARAMETER")
        private fun mapOutgoingPaymentsAndMetadata(
            data_: OutgoingPayment,
            payment_id: UUID?,
            lnurl_base_type: LnurlBase.TypeVersion?,
            lnurl_base_blob: ByteArray?,
            lnurl_description: String?,
            lnurl_metadata_type: LnurlMetadata.TypeVersion?,
            lnurl_metadata_blob: ByteArray?,
            lnurl_successAction_type: LnurlSuccessAction.TypeVersion?,
            lnurl_successAction_blob: ByteArray?,
            user_description: String?,
            user_notes: String?,
            modified_at: Long?,
            original_fiat_type: String?,
            original_fiat_rate: Double?,
            lightning_address: String?,
            card_id: String?
        ): WalletPaymentInfo {
            return WalletPaymentInfo(
                payment = data_,
                metadata = PaymentsMetadataQueries.mapAll(
                    id = data_.id,
                    lnurl_base_type = lnurl_base_type,
                    lnurl_base_blob = lnurl_base_blob,
                    lnurl_description = lnurl_description,
                    lnurl_metadata_type = lnurl_metadata_type,
                    lnurl_metadata_blob = lnurl_metadata_blob,
                    lnurl_successAction_type = lnurl_successAction_type,
                    lnurl_successAction_blob = lnurl_successAction_blob,
                    user_description = user_description,
                    user_notes = user_notes,
                    modified_at = modified_at,
                    original_fiat_type = original_fiat_type,
                    original_fiat_rate = original_fiat_rate,
                    lightning_address = lightning_address,
                    card_id = card_id
                ),
                contact = null
            )
        }
    }
}