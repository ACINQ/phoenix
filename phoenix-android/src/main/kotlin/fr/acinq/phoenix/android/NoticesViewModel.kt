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

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.data.WalletNotice
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class Notice() {
    abstract val priority: Int
    sealed class ShowInHome(override val priority: Int) : Notice()

    object MigrationFromLegacy : ShowInHome(1)
    data class RemoteMessage(val notice: WalletNotice) : ShowInHome(1)
    object CriticalUpdateAvailable : ShowInHome(2)
    object SwapInCloseToTimeout : ShowInHome(3)
    object BackupSeedReminder : ShowInHome(5)
    object MempoolFull : ShowInHome(10)
    object UpdateAvailable : ShowInHome(20)
    object NotificationPermission : ShowInHome(30)

    // less important notices
    sealed class DoNotShowInHome(override val priority: Int = 999) : Notice()
    object WatchTowerLate : DoNotShowInHome()
}

class NoticesViewModel(val appConfigurationManager: AppConfigurationManager, val peerManager: PeerManager, val internalDataRepository: InternalDataRepository) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val notices = mutableStateListOf<Notice>()

    init {
        viewModelScope.launch { monitorWalletContext() }
        viewModelScope.launch { monitorSwapInCloseToTimeout() }
        viewModelScope.launch { monitorWalletNotice() }
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
            log.debug("collecting wallet-context=$it")
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

    private suspend fun monitorWalletNotice() {
        combine(appConfigurationManager.walletNotice, internalDataRepository.getLastReadWalletNoticeIndex) { notice, lastReadIndex ->
            notice to lastReadIndex
        }.collect { (notice, lastReadIndex) ->
            log.debug("collecting wallet-notice=$notice")
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

    class Factory(
        private val appConfigurationManager: AppConfigurationManager,
        private val peerManager: PeerManager,
        private val internalDataRepository: InternalDataRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NoticesViewModel(appConfigurationManager, peerManager, internalDataRepository) as T
        }
    }
}