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
import fr.acinq.phoenix.android.utils.LegacyMigrationHelper
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.Notifications
import fr.acinq.phoenix.legacy.AppContext
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.*

class PhoenixApplication : AppContext() {
    val business by lazy { PhoenixBusiness(PlatformContext(this)) }

    override fun onCreate() {
        super.onCreate()
        Logging.setupLogger(applicationContext)
        Notifications.registerNotificationChannels(applicationContext)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onLegacyFinish() {
        log.info("onLegacyFinish.enter")
        GlobalScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error in legacy payments db migration: ", e)
        }) {
            log.info("starting legacy db migration ")
            LegacyMigrationHelper.migrateLegacyPayments(
                context = applicationContext,
                business = business
            )
        }
        applicationContext.startActivity(Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}
