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

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/** This datastore persists user's preferences (theme, currencies, ...). */
val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = "userprefs")
/** This datastore persists miscellaneous internal data representing various states of the app. */
val Context.internalData: DataStore<Preferences> by preferencesDataStore(name = "internaldata")

class PhoenixApplication : Application() {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val _business = MutableStateFlow<PhoenixBusiness?>(null)
    val business = _business.asStateFlow()

    lateinit var internalDataRepository: InternalDataRepository
    lateinit var userPrefs: UserPrefsRepository

    override fun onCreate() {
        super.onCreate()
        _business.value = PhoenixBusiness(PlatformContext(applicationContext))
        Logging.setupLogger(applicationContext)
        SystemNotificationHelper.registerNotificationChannels(applicationContext)
        internalDataRepository = InternalDataRepository(applicationContext.internalData)
        userPrefs = UserPrefsRepository(applicationContext.userPrefs)
    }

    fun shutdownBusiness() {
        log.debug("shutting down business={}", business.value)
        business.value?.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
        business.value?.stop()
    }
    fun resetBusiness() {
        _business.value = PhoenixBusiness(PlatformContext(this))
        log.debug("business has been reset to {}", business)
    }

    suspend fun clearPreferences() {
        internalDataRepository.clear()
        userPrefs.clear()
    }
}
