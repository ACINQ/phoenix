/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.android.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.utils.logger.LogHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Composable
fun logger(walletId: WalletId?, tag: String): Logger {
    val context = LocalContext.current
    return remember(walletId, tag) {
        if (walletId == null) {
            LoggerFactory.getLogger(tag)
        } else {
            LogHelper.getLogger(context, walletId.toString(), tag)
        }
    }
}

fun LogHelper.getLogger(context: Context, walletId: WalletId, o: Any): Logger {
    return LogHelper.getLogger(context, walletId.nodeIdHash, o::class.java.name)
}