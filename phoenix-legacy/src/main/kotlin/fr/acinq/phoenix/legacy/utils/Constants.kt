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

package fr.acinq.phoenix.legacy.utils

import android.text.format.DateUtils
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.eclair.CltvExpiryDelta
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.legacy.*
import fr.acinq.phoenix.legacy.send.FeerateEstimationPerKb
import fr.acinq.phoenix.legacy.utils.Wallet
import okhttp3.MediaType
import fr.acinq.phoenix.legacy.BuildConfig

/**
 * Created by DPA on 02/12/19.
 */
object Constants {

  // -- apis
  val JSON: MediaType = MediaType.get("application/json; charset=utf-8")
  const val WALLET_CONTEXT_URL = "https://acinq.co/phoenix/walletcontext.json"
  const val WALLET_CONTEXT_URL_FALLBACK = "https://s3.eu-west-1.amazonaws.com/acinq.co/phoenix/walletcontext.json"
  const val BLOCKCHAININFO_TICKER = "https://blockchain.info/ticker"
  const val BITSO_MXN_TICKER = "https://api.bitso.com/v3/ticker/?book=btc_mxn"
  const val COINDESK_CZK_TICKER = "https://api.coindesk.com/v1/bpi/currentprice/CZK.json"
  val ONEML_URL = if (Wallet.isMainnet()) "https://1ml.com" else "https://1ml.com/testnet"
  val MEMPOOLSPACE_EXPLORER_URL = if (Wallet.isMainnet()) "https://mempool.space" else "https://mempool.space/testnet"
  val BLOCKSTREAM_EXPLORER_URL = if (Wallet.isMainnet()) "https://blockstream.info" else "https://blockstream.info/testnet"
  val BLOCKSTREAM_EXPLORER_API = "$BLOCKSTREAM_EXPLORER_URL/api"

  // -- default values
  internal const val DEFAULT_PIN = "111111"

  // -- intents
  const val INTENT_CAMERA_PERMISSION_REQUEST = 1
  const val INTENT_PICK_IMAGE_FILE = 2

  // -- android notifications
  const val DELAY_BEFORE_BACKGROUND_WARNING = DateUtils.DAY_IN_MILLIS * 5
  const val NOTIF_CHANNEL_ID__CHANNELS_WATCHER = "${BuildConfig.LIBRARY_PACKAGE_NAME}.WATCHER_NOTIF_ID"
  const val NOTIF_ID__CHANNELS_WATCHER = 37921816
  const val NOTIF_CHANNEL_ID__MISSED_PAY_TO_OPEN = "${BuildConfig.LIBRARY_PACKAGE_NAME}.MISSED_PAY_TO_OPEN_NOTIF"
  const val NOTIF_ID__MISSED_PAY_TO_OPEN = 81320
  const val NOTIF_CHANNEL_ID__HEADLESS = "${BuildConfig.LIBRARY_PACKAGE_NAME}.FCM_NOTIF"
  const val NOTIF_ID__HEADLESS = 81321

  // -- default wallet values
  val DEFAULT_FEERATE = FeerateEstimationPerKb(rate20min = 12, rate60min = 6, rate12hours = 3)
  val DEFAULT_NETWORK_INFO = NetworkInfo(electrumServer = null, lightningConnected = false, torConnections = HashMap())

  // these default values will be overridden by fee settings from remote, with up-to-date values
  val DEFAULT_TRAMPOLINE_SETTINGS = listOf(
    TrampolineFeeSetting(Satoshi(0), 0, CltvExpiryDelta(576)), // 0 sat + 0.0 %
    TrampolineFeeSetting(Satoshi(1), 100, CltvExpiryDelta(576)), // 1 sat + 0.01 %
    TrampolineFeeSetting(Satoshi(3), 100, CltvExpiryDelta(576)), // 3 sat + 0.01 %
    TrampolineFeeSetting(Satoshi(5), 500, CltvExpiryDelta(576)), // 5 sat + 0.05 %
    TrampolineFeeSetting(Satoshi(7), 1000, CltvExpiryDelta(576)), // 7 sat + 0.1 %
    TrampolineFeeSetting(Satoshi(10), 1200, CltvExpiryDelta(576)), // 10 sat + 0.12 %
    TrampolineFeeSetting(Satoshi(12), 3000, CltvExpiryDelta(576))
  ) // 12 sat + 0.3 %
  val DEFAULT_SWAP_OUT_SETTINGS = SwapOutSettings(
    minFeerateSatByte = 5,
    minAmount = Satoshi(10_000),
    maxAmount = Satoshi(2_000_000), // 0.02 BTC
    status = ServiceStatus.Active)
  val DEFAULT_MEMPOOL_CONTEXT = MempoolContext(false)
  val DEFAULT_BALANCE = Balance(0, MilliSatoshi(0), MilliSatoshi(0))

  const val LATEST_PAYMENTS_COUNT = 14
}
