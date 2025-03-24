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

package fr.acinq.phoenix.managers

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.BoltCardInfo
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.SqliteAppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class CardsManager(
    private val loggerFactory: LoggerFactory,
    private val appDb: SqliteAppDb,
    private val databaseManager: DatabaseManager,
    private val paymentsManager: PaymentsManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
        databaseManager = business.databaseManager,
        paymentsManager = business.paymentsManager
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _cardsList = MutableStateFlow<List<BoltCardInfo>>(emptyList())
    val cardsList = _cardsList.asStateFlow()

    private val _cardsMap = MutableStateFlow<Map<UUID, BoltCardInfo>>(emptyMap())
    val cardsMap = _cardsMap.asStateFlow()

    init {
        launch {
            appDb.monitorCardsFlow().collect { list ->
                val newMap = list.associateBy { it.id }
                _cardsList.value = list
                _cardsMap.value = newMap
            }
        }
    }

    /**
     * This method will insert or update the card in the database
     * (depending on whether it already exists).
     */
    suspend fun saveCard(card: BoltCardInfo) {
        appDb.saveCard(card)
    }

    suspend fun deleteCard(cardId: UUID) {
        appDb.deleteCard(cardId)
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
            year = nowLDT.year, month = nowLDT.month, dayOfMonth = 1,
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
    ): List<WalletPaymentInfo> {
        val paymentsDb = databaseManager.paymentsDb()

        var done = false
        val maxBatchCount = 50
        var offset = 0
        var results = mutableListOf<WalletPaymentInfo>()
        do {
            val batch = paymentsDb.listRecentCardPayments(
                count = maxBatchCount.toLong(),
                skip = offset.toLong(),
                cardId = cardId,
                sinceDate = minInstant.toEpochMilliseconds()
            )
            results.addAll(batch)
            if (batch.size >= maxBatchCount) {
                offset += batch.size
            } else {
                done = true
            }

        } while (!done)

        return results
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
}
