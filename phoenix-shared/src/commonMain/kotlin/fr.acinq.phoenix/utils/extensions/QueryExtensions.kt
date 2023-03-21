package fr.acinq.phoenix.utils.extensions

import com.squareup.sqldelight.Query
import com.squareup.sqldelight.db.use

enum class QueryExecution {
    Continue,
    Stop
}

/**
 * Simplified way to step thru query results, and STOP when you've found what you're looking for.
 * For example:
 * ```
 * val query = queries.listNewChannel(mapper = ::mapListNewChannel)
 * var match: ListNewChannelRow? = null
 * query.execute { row ->
 *   row.received?.receivedWith?.firstOrNull() {
 *     it is IncomingPayment.ReceivedWith.NewChannel && it.channelId == channelId
 *   }?.let {
 *     match = row
 *     QueryExecution.Stop
 *   } ?: QueryExecution.Continue
 * }
 * ```
 */
fun <T : Any> Query<T>.execute(iterator: (T) -> QueryExecution) {
    this.execute().use { cursor ->
        while (cursor.next()) {
            val row = this.mapper(cursor)
            if (iterator(row) == QueryExecution.Stop) {
                break
            }
        }
    }
}