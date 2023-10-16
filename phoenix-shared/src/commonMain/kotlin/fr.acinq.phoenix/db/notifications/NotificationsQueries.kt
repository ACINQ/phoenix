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
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class NotificationsQueries(val database: AppDatabase) {
    private val queries = database.notificationsQueries

    fun get(id: UUID): Notification? {
        return queries.get(id.toString()).executeAsOneOrNull()?.let { row ->
            mapToNotification(row.id, row.type_version, row.data_json, row.created_at, row.read_at)
        }
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
     * Returns a list of unread notifications, grouped by type (i.e. [Notification]), in order to avoid spamming the UI
     * with duplicates.
     *
     * The set of UUIDs linked to a [Notification] can be used to execute an action on all the actual relevant data in the
     * database (for example, to mark those notifications as read).
     */
    fun listUnread(): Flow<List<Pair<Set<UUID>, Notification>>> {
        return queries.listUnread().asFlow().mapToList().map {
            val notifs = it.mapNotNull { row ->
                val ids = row.grouped_ids.split(";").map { UUID.fromString(it) }.toSet()
                val notif = mapToNotification(row.id, row.type_version, row.data_json, row.max ?: 0, null)
                if (notif != null) {
                    ids to notif
                } else {
                    // invalid notifications are marked as read so that they are filtered by the SQL query next time
                    markAsRead(ids)
                    null
                }
            }

            val (pendingSwaps, others) = notifs.partition {
                val notif = it.second
                notif is Notification.PaymentRejected && notif.source == LiquidityEvents.Source.OnChainWallet
            }

            // group swap notification by amount, and flatten the list
            val pendingSwapsGroupedByAmount = pendingSwaps.mapNotNull {
                val notif = it.second
                if (notif is Notification.PaymentRejected) notif.amount to it else null
            }.groupBy { it.first }.map {
                val sameNotificationGroups = it.value.map { it.second }
                val uuids = sameNotificationGroups.map { it.first }.flatten().toSet()
                uuids to sameNotificationGroups.first().second
            }

            (pendingSwapsGroupedByAmount + others).sortedByDescending { it.second.createdAt }
        }
    }

    companion object {
        /** Map columns to a [Notification] object. If the [data_json] column is unreadable, return null. */
        internal fun mapToNotification(
            id: String,
            type_version: NotificationTypeVersion,
            data_json: ByteArray,
            created_at: Long,
            read_at: Long?,
        ): Notification? {
            return when (val data = NotificationData.deserialize(type_version, data_json)) {
                is NotificationData.PaymentRejected.OverAbsoluteFee.V0 -> {
                    Notification.OverAbsoluteFee(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount,
                        source = data.source,
                        fee = data.fee,
                        maxAbsoluteFee = data.maxAbsoluteFee
                    )
                }
                is NotificationData.PaymentRejected.OverRelativeFee.V0 -> {
                    Notification.OverRelativeFee(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount,
                        source = data.source,
                        fee = data.fee,
                        maxRelativeFeeBasisPoints = data.maxRelativeFeeBasisPoints
                    )
                }
                is NotificationData.PaymentRejected.Disabled.V0 -> {
                    Notification.FeePolicyDisabled(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount,
                        source = data.source,
                    )
                }
                is NotificationData.PaymentRejected.ChannelsInitializing.V0 -> {
                    Notification.ChannelsInitializing(
                        id = UUID.fromString(id),
                        createdAt = created_at,
                        readAt = read_at,
                        amount = data.amount,
                        source = data.source,
                    )
                }
                is NotificationData.WatchTowerOutcome -> null
                null -> null
            }
        }
    }
}
