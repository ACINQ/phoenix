package fr.acinq.phoenix.utils.extensions

import com.squareup.sqldelight.Query
import com.squareup.sqldelight.db.use

enum class QueryExecution {
    Continue,
    Stop
}

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