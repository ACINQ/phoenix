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

package fr.acinq.phoenix.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletNotice
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.ConnectionsManager
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class Notice() {
    abstract val priority: Int
    sealed class ShowInHome(override val priority: Int) : Notice()

    data class RemoteMessage(val notice: WalletNotice) : ShowInHome(1)
    data object CriticalUpdateAvailable : ShowInHome(2)
    data object TorDisconnected : ShowInHome(3)
    data object SwapInCloseToTimeout : ShowInHome(4)
    data object BackupSeedReminder : ShowInHome(5)
    data object MempoolFull : ShowInHome(10)
    data object UpdateAvailable : ShowInHome(20)
    data object NotificationPermission : ShowInHome(30)

    // less important notices
    sealed class DoNotShowInHome(override val priority: Int = 999) : Notice()
    data object WatchTowerLate : DoNotShowInHome()
}

class NoticesViewModel(
    val application: PhoenixApplication,
    val appConfigurationManager: AppConfigurationManager,
    val peerManager: PeerManager,
    private val connectionsManager: ConnectionsManager,
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val notices = mutableStateListOf<Notice>()
    var isPowerSaverModeOn by mutableStateOf(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isPowerSaverModeOn = powerManager.isPowerSaveMode
        }
    }

    init {
        viewModelScope.launch { monitorWalletContext() }
        viewModelScope.launch { monitorSwapInCloseToTimeout() }
        viewModelScope.launch { monitorWalletNotice() }

        val powerManager = application.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        isPowerSaverModeOn = powerManager.isPowerSaveMode
        application.applicationContext.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))

        viewModelScope.launch { monitorTorConnection() }
        viewModelScope.launch { monitorSeedBackupPref() }
        viewModelScope.launch { monitorChannelsWatcherOutcome() }
    }

    override fun onCleared() {
        super.onCleared()
        log.info("cleared notices-view-model")
        application.applicationContext.unregisterReceiver(receiver)
    }

    fun addNotice(notice: Notice) {
        if (notices.none { it == notice }) {
            notices.add(notice)
        }
    }

    inline fun <reified N : Notice> removeNotice() {
        notices.filterIsInstance<N>().forEach { notices.remove(it) }
    }

    private suspend fun monitorWalletContext() {
        appConfigurationManager.walletContext.collect {
            log.debug("collecting wallet-context={}", it)
            val isMempoolFull = it?.isMempoolFull ?: false
            val isUpdateAvailable = it?.androidLatestVersion?.let { it > BuildConfig.VERSION_CODE } ?: false
            val isCriticalUpdateAvailable = it?.androidLatestCriticalVersion?.let { it > BuildConfig.VERSION_CODE } ?: false

            if (isMempoolFull) {
                addNotice(Notice.MempoolFull)
            } else {
                removeNotice<Notice.MempoolFull>()
            }

            if (isCriticalUpdateAvailable) {
                addNotice(Notice.CriticalUpdateAvailable)
                removeNotice<Notice.UpdateAvailable>()
            } else if (isUpdateAvailable) {
                addNotice(Notice.UpdateAvailable)
                removeNotice<Notice.CriticalUpdateAvailable>()
            } else {
                removeNotice<Notice.UpdateAvailable>()
                removeNotice<Notice.CriticalUpdateAvailable>()
            }
        }
    }

    private suspend fun monitorTorConnection() {
        combine(
            appConfigurationManager.isTorEnabled.filterNotNull(),
            connectionsManager.connections
        ) { isTorEnabled, connections ->
            isTorEnabled && connections.peer !is Connection.ESTABLISHED
        }.collect { hasIssue ->
            if (hasIssue) {
                delay(3_000) // this pause avoids displaying the notification too eagerly
                addNotice(Notice.TorDisconnected)
            } else {
                removeNotice<Notice.TorDisconnected>()
            }
        }
    }

    private suspend fun monitorWalletNotice() {
        combine(appConfigurationManager.walletNotice, application.internalDataRepository.getLastReadWalletNoticeIndex) { notice, lastReadIndex ->
            notice to lastReadIndex
        }.collect { (notice, lastReadIndex) ->
            log.debug("collecting wallet-notice={}", notice)
            if (notice != null && notice.index > lastReadIndex) {
                addNotice(Notice.RemoteMessage(notice))
            } else {
                removeNotice<Notice.RemoteMessage>()
            }
        }
    }

    private suspend fun monitorSwapInCloseToTimeout() {
        peerManager.swapInNextTimeout.filterNotNull().collect { (_, nextTimeoutRemainingBlocks) ->
            when {
                nextTimeoutRemainingBlocks < 144 * 30 -> addNotice(Notice.SwapInCloseToTimeout)
                else -> removeNotice<Notice.SwapInCloseToTimeout>()
            }
        }
    }

    private suspend fun monitorSeedBackupPref() {
        application.internalDataRepository.showSeedBackupNotice.collect {
            if (it) {
                addNotice(Notice.BackupSeedReminder)
            } else {
                removeNotice<Notice.BackupSeedReminder>()
            }
        }
    }

    private suspend fun monitorChannelsWatcherOutcome() {
        application.internalDataRepository.getChannelsWatcherOutcome.filterNotNull().collect {
            if (currentTimestampMillis() - it.timestamp > 6 * DateUtils.DAY_IN_MILLIS) {
                addNotice(Notice.WatchTowerLate)
            } else {
                removeNotice<Notice.WatchTowerLate>()
            }
        }
    }

    class Factory(
        private val appConfigurationManager: AppConfigurationManager,
        private val peerManager: PeerManager,
        private val connectionsManager: ConnectionsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return NoticesViewModel(
                application, appConfigurationManager, peerManager, connectionsManager,
            ) as T
        }
    }
}