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
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.SystemNotificationHelper
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.legacy.AppContext
import fr.acinq.phoenix.legacy.internalData
import fr.acinq.phoenix.utils.PlatformContext

class PhoenixApplication : AppContext() {
    val business by lazy { PhoenixBusiness(PlatformContext(this)) }
    lateinit var internalDataRepository: InternalDataRepository

    override fun onCreate() {
        super.onCreate()
        Logging.setupLogger(applicationContext)
        SystemNotificationHelper.registerNotificationChannels(applicationContext)
        internalDataRepository = InternalDataRepository(applicationContext.internalData)
    }

    override fun onLegacyFinish() {
        applicationContext.startActivity(Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}
