/*
 * Copyright 2020 ACINQ SAS
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

import android.content.Intent
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.legacy.AppContext
import fr.acinq.phoenix.legacy.internalData
import fr.acinq.phoenix.legacy.userPrefs
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class PhoenixApplication : AppContext() {

    private val _business = MutableStateFlow<PhoenixBusiness?>(null)
    val business = _business.asStateFlow()

    lateinit var internalDataRepository: InternalDataRepository
    lateinit var userPrefs: UserPrefsRepository

    abstract val mainActivityClass: Class<out MainActivity>
    abstract val nodeServiceClass: Class<out NodeService>

    override fun onCreate() {
        super.onCreate()
        _business.value = PhoenixBusiness(PlatformContext(applicationContext))
        Logging.setupLogger(applicationContext)
        SystemNotificationHelper.registerNotificationChannels(applicationContext)
        internalDataRepository = InternalDataRepository(applicationContext.internalData)
        userPrefs = UserPrefsRepository(applicationContext.userPrefs)
    }

    override fun onLegacyFinish() {
        applicationContext.startActivity(Intent(applicationContext, mainActivityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    fun shutdownBusiness() {
        log.info("business=${business.value}")
        business.value?.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
        business.value?.stop()
    }

    fun resetBusiness() {
        _business.value = PhoenixBusiness(PlatformContext(this))
        log.info("business=$business")
    }

    suspend fun clearPreferences() {
        internalDataRepository.clear()
        userPrefs.clear()
        LegacyPrefsDatastore.clear(applicationContext)
    }
}
