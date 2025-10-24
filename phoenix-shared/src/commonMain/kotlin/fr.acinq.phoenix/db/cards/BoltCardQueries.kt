package fr.acinq.phoenix.db.cards

import app.cash.sqldelight.coroutines.asFlow
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.BoltCardInfo
import fr.acinq.phoenix.data.BoltCardKeySet
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.SpendingLimit
import fr.acinq.phoenix.db.didDeleteCard
import fr.acinq.phoenix.db.didSaveCard
import fr.acinq.phoenix.db.sqldelight.Bolt_cards
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BoltCardQueries(val database: PaymentsDatabase) {

    val queries = database.boltCardsQueries

    fun saveCard(card: BoltCardInfo, notify: Boolean = true) {
        database.transaction {
            val cardExists = queries.existsCard(
                cardId = card.id.toString()
            ).executeAsOne() > 0
            if (cardExists) {
                updateExistingCard(card)
            } else {
                saveNewCard(card)
            }
            if (notify) {
                didSaveCard(card.id, database)
            }
        }
    }

    private fun saveNewCard(card: BoltCardInfo) {
        queries.insertCard(
            id = card.id.toString(),
            name = card.name,
            key0 = card.keys.key0.toByteArray(),
            uid = card.uid.toByteArray(),
            counter = card.lastKnownCounter.toLong(),
            isFrozen = card.isFrozen,
            isArchived = card.isArchived,
            isReset = card.isReset,
            isForeign = card.isForeign,
            dailyLimitCurrency = card.dailyLimit?.currency?.displayCode,
            dailyLimitAmount = card.dailyLimit?.amount,
            monthlyLimitCurrency = card.monthlyLimit?.currency?.displayCode,
            monthlyLimitAmount = card.monthlyLimit?.amount,
            createdAt = card.createdAt.toEpochMilliseconds(),
            updatedAt = null
        )
    }

    fun updateExistingCard(card: BoltCardInfo) {
        queries.updateCard(
            name = card.name,
            counter = card.lastKnownCounter.toLong(),
            isFrozen = card.isFrozen,
            isArchived = card.isArchived,
            isReset = card.isReset,
            dailyLimitCurrency = card.dailyLimit?.currency?.displayCode,
            dailyLimitAmount = card.dailyLimit?.amount,
            monthlyLimitCurrency = card.monthlyLimit?.currency?.displayCode,
            monthlyLimitAmount = card.monthlyLimit?.amount,
            updatedAt = currentTimestampMillis(),
            cardId = card.id.toString()
        )
    }

    fun listCards(): List<BoltCardInfo> {
        return database.transactionWithResult {
            queries.listCards().executeAsList().mapNotNull { row ->
                parseRow(row)
            }
        }
    }

    fun monitorCardsFlow(context: CoroutineContext): Flow<List<BoltCardInfo>> {
        return queries.listCards().asFlow().map {
            withContext(context) {
                listCards()
            }
        }
    }

    fun getCard(cardId: UUID): BoltCardInfo? {
        return database.transactionWithResult {
            queries.getCard(
                cardId = cardId.toString()
            ).executeAsOneOrNull()?.let { row ->
                parseRow(row)
            }
        }
    }

    fun deleteCard(cardId: UUID, notify: Boolean = true) {
        return database.transaction {
            queries.deleteCard(cardId = cardId.toString())
            if (notify) {
                didDeleteCard(cardId, database)
            }
        }
    }

    private fun parseRow(row: Bolt_cards): BoltCardInfo? {
        val id: UUID
        val keys: BoltCardKeySet
        try { // these can throw exceptions if input is incorrect length
            id = UUID.Companion.fromString(row.id)
            keys = BoltCardKeySet(key0 = row.key0.toByteVector())
        } catch (_: Exception) {
            return null
        }

        return BoltCardInfo(
            id = id,
            name = row.name,
            keys = keys,
            uid = row.uid.toByteVector(),
            lastKnownCounter = row.counter.toUInt(),
            isFrozen = row.is_frozen,
            isArchived = row.is_archived,
            isReset = row.is_reset,
            isForeign = row.is_foreign,
            dailyLimit = parseSpendingLimit(row.daily_limit_currency, row.daily_limit_amount),
            monthlyLimit = parseSpendingLimit(row.monthly_limit_currency, row.monthly_limit_amount),
            createdAt = Instant.Companion.fromEpochMilliseconds(row.created_at)
        )
    }

    private fun parseSpendingLimit(currency: String?, amount: Double?): SpendingLimit? {
        if (currency != null && amount != null) {
            val parsedCurrency: CurrencyUnit? =
                FiatCurrency.Companion.valueOfOrNull(currency) ?:
                BitcoinUnit.Companion.valueOfOrNull(currency)

            if (parsedCurrency != null && amount > 0) {
                return SpendingLimit(parsedCurrency, amount)
            }
        }
        return null
    }
}