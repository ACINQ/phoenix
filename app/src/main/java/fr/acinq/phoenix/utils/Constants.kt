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

package fr.acinq.phoenix.utils

import android.text.format.DateUtils
import fr.acinq.eclair.CltvExpiryDelta
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.*
import okhttp3.MediaType

/**
 * Created by DPA on 02/12/19.
 */
object Constants {

  // -- apis
  val JSON: MediaType = MediaType.get("application/json; charset=utf-8")
  const val WALLET_CONTEXT_URL = "https://acinq.co/phoenix/walletcontext.json"
  const val PRICE_RATE_API = "https://blockchain.info/ticker"

  // -- default values
  internal const val DEFAULT_PIN = "111111"

  // -- intents
  const val INTENT_CAMERA_PERMISSION_REQUEST = 1

  // -- android notifications
  const val DELAY_BEFORE_BACKGROUND_WARNING = DateUtils.DAY_IN_MILLIS * 5
  const val WATCHER_NOTIFICATION_CHANNEL_ID = "WATCHER_NOTIF_ID"
  const val WATCHER_REQUEST_CODE = 37921816

  // -- default wallet values
  val DEFAULT_NETWORK_INFO = NetworkInfo(networkConnected = true, electrumServer = null, lightningConnected = true, torConnections = HashMap())
  // these default values will be overridden by fee settings from remote, with up-to-date values
  val DEFAULT_TRAMPOLINE_SETTINGS = listOf(
    TrampolineFeeSetting(MilliSatoshi(1000), 0.0001, CltvExpiryDelta(576)), // 1 sat + 0.01 %
    TrampolineFeeSetting(MilliSatoshi(3000), 0.0001, CltvExpiryDelta(576)), // 3 sat + 0.01 %
    TrampolineFeeSetting(MilliSatoshi(5000), 0.0005, CltvExpiryDelta(576)), // 5 sat + 0.05 %
    TrampolineFeeSetting(MilliSatoshi(5000), 0.001, CltvExpiryDelta(576)), // 5 sat + 0.1 %
    TrampolineFeeSetting(MilliSatoshi(5000), 0.0012, CltvExpiryDelta(576))) // 5 sat + 0.12 %
  val DEFAULT_SWAP_IN_SETTINGS = SwapInSettings(0.001)
}
