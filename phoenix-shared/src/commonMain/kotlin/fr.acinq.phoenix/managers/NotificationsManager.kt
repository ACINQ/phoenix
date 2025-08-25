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
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.WatchTowerOutcome
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.lightning.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationsManager(
    private val loggerFactory: LoggerFactory,
    private val appDb: SqliteAppDb,
    private val walletManager: WalletManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
        walletManager = business.walletManager
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _notifications = MutableStateFlow<List<Pair<Set<UUID>, Notification>>>(emptyList())
    /**
     * List of notifications grouped by notification type. The <Set<UUID>> can be used
     * to execute an action on all the underlying notification data in the database.
     */
    val notifications = _notifications.asStateFlow()

    init {
        launch { monitorNotifications() }
    }

    private suspend fun getNodeIdHash(): String {
        return walletManager.keyManager.filterNotNull().first().nodeIdHash()
    }

    private suspend fun monitorNotifications() {
        val nodeIdHash = getNodeIdHash()
        appDb.initializeNodeIdHashColumn(nodeIdHash)
        appDb.listUnreadNotification(nodeIdHash).collect {
            _notifications.value = it
        }
    }

    suspend fun getNotificationDetails(id: UUID): Notification? {
        return appDb.getNotification(id)
    }

    suspend fun saveWatchTowerOutcome(outcome: WatchTowerOutcome) {
        log.debug { "persisting watch-tower-outcome=$outcome" }
        val nodeIdHash = getNodeIdHash()
        appDb.saveNotification(outcome, nodeIdHash)
    }

    internal suspend fun saveLiquidityEventNotification(event: LiquidityEvents) {
        log.debug { "persisting liquidity_event=$event" }
        val nodeIdHash = getNodeIdHash()
        when (event) {
            is LiquidityEvents.Rejected -> {
                val notification = when (val reason = event.reason) {
                    is LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee -> Notification.OverAbsoluteFee(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        fee = event.fee,
                        source = event.source,
                        maxAbsoluteFee = reason.maxAbsoluteFee
                    )
                    is LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee -> Notification.OverRelativeFee(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        fee = event.fee,
                        source = event.source,
                        maxRelativeFeeBasisPoints = reason.maxRelativeFeeBasisPoints
                    )
                    is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> Notification.FeePolicyDisabled(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                    is LiquidityEvents.Rejected.Reason.MissingOffChainAmountTooLow -> Notification.MissingOffChainAmountTooLow(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                    is LiquidityEvents.Rejected.Reason.ChannelFundingInProgress,
                    is LiquidityEvents.Rejected.Reason.NoMatchingFundingRate,
                    is LiquidityEvents.Rejected.Reason.TooManyParts -> Notification.GenericError(
                        id = UUID.randomUUID(),
                        createdAt = currentTimestampMillis(),
                        readAt = null,
                        amount = event.amount,
                        source = event.source,
                    )
                }
                appDb.saveNotification(notification, nodeIdHash)
            }
            else -> {}
        }
    }

    fun dismissNotifications(ids: Set<UUID>) {
        launch { appDb.dismissNotifications(ids) }
    }

    fun dismissAllNotifications() {
        launch { appDb.dismissAllNotifications() }
    }
}