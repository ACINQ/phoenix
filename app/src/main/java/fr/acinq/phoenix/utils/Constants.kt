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

  const val MNEMONICS_REMINDER_INTERVAL = DateUtils.DAY_IN_MILLIS * 30
  const val DELAY_BEFORE_BACKGROUND_WARNING = DateUtils.DAY_IN_MILLIS * 5

  // -- android notifications
  const val WATCHER_NOTIFICATION_CHANNEL_ID = "WATCHER_NOTIF_ID"
  const val WATCHER_REQUEST_CODE = 37921816

  // -- default wallet values
  val DEFAULT_NETWORK_INFO = NetworkInfo(networkConnected = true, electrumServer = null, lightningConnected = true)
  val DEFAULT_TRAMPOLINE_SETTINGS = TrampolineSettings(MilliSatoshi(5000), 0.001, 5, 144)
  val DEFAULT_SWAP_IN_SETTINGS = SwapInSettings(0.005)
}
