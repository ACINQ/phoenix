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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.GlobalPrefs
import fr.acinq.phoenix.utils.PlatformContext
import org.slf4j.LoggerFactory


/** This datastore persists preferences across node ids. */
val Context.globalPrefs: DataStore<Preferences> by preferencesDataStore(name = "globalprefs")

class PhoenixApplication : Application() {

    private val log = LoggerFactory.getLogger(this::class.java)

    lateinit var globalPrefs: GlobalPrefs
    lateinit var phoenixGlobal: PhoenixGlobal

    override fun onCreate() {
        super.onCreate()

        phoenixGlobal = PhoenixGlobal(PlatformContext(applicationContext))
        globalPrefs = GlobalPrefs(applicationContext.globalPrefs)
        BusinessManager.initialize(applicationContext)

        Logging.setupLogger(applicationContext)
        log.info("creating app")
        SystemNotificationHelper.registerNotificationChannels(applicationContext)
    }
}
