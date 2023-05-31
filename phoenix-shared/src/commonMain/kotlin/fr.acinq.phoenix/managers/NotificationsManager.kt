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

package fr.acinq.phoenix.managers

import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.WatchTowerOutcome
import fr.acinq.phoenix.db.SqliteAppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class NotificationsManager(
    private val loggerFactory: LoggerFactory,
    private val appDb: SqliteAppDb,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
    )

    private val log = newLogger(loggerFactory)

    private val _notifications = MutableStateFlow<List<Pair<Set<UUID>, Notification>>>(emptyList())
    val notifications = _notifications.asStateFlow()

    init {
        launch { monitorNotifications() }
    }

    private suspend fun monitorNotifications() {
        appDb.listUnreadNotification().collect {
            _notifications.value = it
        }
    }

    suspend fun getNotificationDetails(id: UUID): Notification? {
        return appDb.getNotification(id)
    }

    suspend fun saveWatchTowerOutcome(outcome: WatchTowerOutcome) {
        log.debug { "persisting watch-tower-outcome=$outcome" }
        appDb.saveNotification(outcome)
    }

    internal suspend fun saveLiquidityEventNotification(event: LiquidityEvents) {
        log.debug { "persisting to db liquidity_event=$event" }
        when (event) {
            is LiquidityEvents.Rejected -> {
                val notification = when (val reason = event.reason) {
                    is LiquidityEvents.Rejected.Reason.TooExpensive -> Notification.FeeTooExpensive(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                        expectedFee = reason.actual,
                        maxAllowedFee = reason.maxAllowed
                    )
                    is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> Notification.RejectedManually(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                    is LiquidityEvents.Rejected.Reason.RejectedByUser -> Notification.RejectedManually(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                    is LiquidityEvents.Rejected.Reason.ChannelInitializing -> Notification.ChannelsInitializing(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                }
                appDb.saveNotification(notification)
            }
            else -> {}
        }
    }

    fun dismissNotifications(ids: Set<UUID>) {
        launch { appDb.dismissNotifications(ids) }
    }

    fun dismissAllNotifications() {
        launch { appDb.dimissAllNotifications() }
    }
}