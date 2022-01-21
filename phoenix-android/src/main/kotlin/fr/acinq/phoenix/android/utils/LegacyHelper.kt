/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.content.Context
import fr.acinq.phoenix.android.BuildConfig
import java.io.File

object LegacyHelper {

    fun hasLegacyChannels(context: Context): Boolean {
        val datadir = File(File(context.filesDir, "node-data"), BuildConfig.CHAIN) // e.g. /data/.../node-data/mainnet
        val legacyDbFile = File(datadir, "eclair.sqlite")
        return legacyDbFile.exists() && legacyDbFile.isFile && legacyDbFile.canRead()
    }

}