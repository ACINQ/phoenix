/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.db.notifications

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.db.AppDatabase
import kotlinx.coroutines.flow.Flow

class NotificationsQueries(val database: AppDatabase) {
    private val queries = database.notificationsQueries

    fun get(id: UUID): Notification? {
        return queries.get(id.toString(), mapper = ::mapToNotification).executeAsOneOrNull()
    }

    fun save(notification: Notification) {
        val (typeVersion, blob) = notification.mapToDb()
        queries.insert(
            id = notification.id.toString(),
            type_version = typeVersion,
            data_json = blob,
            created_at = currentTimestampMillis()
        )
    }

    /** Marks a list of notifications as read. */
    fun markAsRead(ids: Set<UUID>) {
        queries.markAsRead(read_at = currentTimestampMillis(), id = ids.map { it.toString() })
    }

    /** Marks all unread notifications as read. */
    fun markAllAsRead() {
        queries.markAllAsRead(currentTimestampMillis())
    }

    /**
     * Returns a list of unread notifications, grouped by content in order to avoid duplicates/spamming the UU. The set of UUIDs can be
     * used to execute an action on all the actual relevant data in the database (for example, to mark those notifications as read).
     */
    fun listUnread(): Flow<List<Pair<Set<UUID>, Notification>>> {
        return queries.listUnread(mapper = { id, group_concat, type_version, data_json, max ->
            group_concat.split(";").map { UUID.fromString(it) }.toSet() to mapToNotification(id, type_version, data_json, max ?: 0, null)
        }).asFlow().mapToList()
    }

    companion object {
        fun mapToNotification(
            id: String,
            type_version: NotificationTypeVersion,
            data_json: ByteArray,
            created_at: Long,
            read_at: Long?,
        ): Notification {
            return when (val data = NotificationData.deserialize(type_version, data_json)) {
                is NotificationData.PaymentRejected.TooExpensive.V0 -> {
                    Notification.FeeTooExpensive(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount,
                        expectedFee = data.expectedFee,
                        maxAllowedFee = data.maxAllowedFee
                    )
                }
                is NotificationData.PaymentRejected.Disabled.V0 -> {
                    Notification.FeePolicyDisabled(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount
                    )
                }
                is NotificationData.PaymentRejected.ByUser.V0 -> {
                    Notification.RejectedManually(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount
                    )
                }
                is NotificationData.PaymentRejected.ChannelsInitializing.V0 -> {
                    Notification.ChannelsInitializing(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount
                    )
                }
            }
        }
    }
}
