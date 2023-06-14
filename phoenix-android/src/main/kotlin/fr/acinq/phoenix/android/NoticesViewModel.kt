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

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.managers.AppConfigurationManager
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class Notice {
    sealed class ShowInHome: Notice()
    sealed class DoNotShowInHome: Notice()

    object NotificationPermission : ShowInHome()
    object BackupSeedReminder : ShowInHome()
    object UpdateAvailable : ShowInHome()
    object CriticalUpdateAvailable : ShowInHome()
    object MempoolFull : ShowInHome()

    // less important notices
    object WatchTowerLate : DoNotShowInHome()
}

class NoticesViewModel(val appConfigurationManager: AppConfigurationManager) : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)

    val notices = mutableStateMapOf<Notice, Notice>()

    init {
        viewModelScope.launch { monitorWalletContext() }
    }

    fun addNotice(notice: Notice) {
        notices[notice] = notice
    }

    fun removeNotice(notice: Notice) {
        notices.remove(notice)
    }

    private suspend fun monitorWalletContext() {
        appConfigurationManager.chainContext.collect {
            val isMempoolFull = it?.mempool?.v1?.highUsage ?: false
            val isUpdateAvailable = it?.version?.let { it > BuildConfig.VERSION_CODE } ?: false
            val isCriticalUpdateAvailable = it?.latestCriticalVersion?.let { it > BuildConfig.VERSION_CODE } ?: false

            if (isMempoolFull) {
                addNotice(Notice.MempoolFull)
            } else {
                removeNotice(Notice.MempoolFull)
            }

            if (isCriticalUpdateAvailable) {
                addNotice(Notice.CriticalUpdateAvailable)
                removeNotice(Notice.UpdateAvailable)
            } else if (isUpdateAvailable) {
                addNotice(Notice.UpdateAvailable)
                removeNotice(Notice.CriticalUpdateAvailable)
            } else {
                removeNotice(Notice.UpdateAvailable)
                removeNotice(Notice.CriticalUpdateAvailable)
            }
        }
    }

    class Factory(
        private val appConfigurationManager: AppConfigurationManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NoticesViewModel(appConfigurationManager) as T
        }
    }
}