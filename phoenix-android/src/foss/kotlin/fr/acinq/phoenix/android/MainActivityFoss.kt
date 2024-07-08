/*
 * Copyright 2024 ACINQ SAS
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

import android.content.Context
import android.content.Intent
import fr.acinq.phoenix.android.services.HeadlessActions
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.android.services.NodeServiceFoss

class MainActivityFoss : MainActivity() {

    override fun bindService() {
        Intent(this, NodeServiceFoss::class.java).let { intent ->
            applicationContext.bindService(intent, appViewModel.serviceConnection, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)
        }
    }

    fun headlessServiceAction(action: HeadlessActions) {
        val intent = Intent(applicationContext, NodeServiceFoss::class.java).apply {
            this.action = action.name
            putExtra(NodeService.EXTRA_ORIGIN, NodeService.ORIGIN_HEADLESS)
        }
        applicationContext.startForegroundService(intent)
    }
}