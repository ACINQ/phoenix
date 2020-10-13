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

package fr.acinq.phoenix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.CltvExpiryDelta
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.background.BalanceEvent
import fr.acinq.phoenix.main.InAppNotifications
import fr.acinq.phoenix.utils.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class AppContext : Application(), DefaultLifecycleObserver {

  val log = LoggerFactory.getLogger(this::class.java)

  @Volatile
  var isAppVisible = false
    private set

  override fun onCreate() {
    super<Application>.onCreate()
    init()
    log.info("app created")
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    isAppVisible = true
  }

  override fun onStop(owner: LifecycleOwner) {
    isAppVisible = false
  }

  override fun onDestroy(owner: LifecycleOwner) {
    super.onDestroy(owner)
    EventBus.getDefault().unregister(this)
  }

  private fun init() {
    ThemeHelper.applyTheme(Prefs.getTheme(applicationContext))
    Logging.setupLogger(applicationContext)
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    Converter.refreshCoinPattern(applicationContext)
    updateWalletContext()

    // poll exchange rate api every 120 minutes
    kotlin.concurrent.timer(name = "exchange_rate_timer", daemon = false, initialDelay = 0L, period = 120 * 60 * 1000) {
      Wallet.httpClient.newCall(Request.Builder().url(Constants.PRICE_RATE_API).build()).enqueue(getExchangeRateHandler(applicationContext))
      Wallet.httpClient.newCall(Request.Builder().url(Constants.MXN_PRICE_RATE_API).build()).enqueue(getMXNRateHandler(applicationContext))
    }

    // notification channels (android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(
        NotificationChannel(Constants.NOTIF_CHANNEL_ID__CHANNELS_WATCHER, getString(R.string.notification_channels_watcher_title), NotificationManager.IMPORTANCE_HIGH).apply {
          description = getString(R.string.notification_channels_watcher_desc)
        },
        NotificationChannel(Constants.NOTIF_CHANNEL_ID__PAY_TO_OPEN, getString(R.string.notification_pay_to_open_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = getString(R.string.notification_pay_to_open_desc)
        },
        NotificationChannel(Constants.NOTIF_CHANNEL_ID__HEADLESS, getString(R.string.notification_headless_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = getString(R.string.notification_headless_desc)
        }
      ))
    }
  }

  companion object {
    fun getInstance(context: Context): AppContext {
      return context.applicationContext as AppContext
    }
  }

  /** Settings for the fees allocated to a trampoline node. */
  val trampolineFeeSettings = MutableLiveData(Constants.DEFAULT_TRAMPOLINE_SETTINGS)

  /** List of in-app notifications. */
  val notifications = MutableLiveData(HashSet<InAppNotifications>())

  /** Fee settings for swap-in (on-chain -> LN). */
  val swapInSettings = MutableLiveData(Constants.DEFAULT_SWAP_IN_SETTINGS)

  /** Current balance of the node. */
  val balance = MutableLiveData<MilliSatoshi>(MilliSatoshi(0))

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: BalanceEvent) {
    balance.postValue(event.balance)
  }

  // ========================================================================== //
  //                             EXTERNAL API CALLS                             //
  //                        TODO: move this to a service                        //
  // ========================================================================== //

  private fun updateWalletContext() {
    Wallet.httpClient.newCall(Request.Builder().url(Constants.WALLET_CONTEXT_URL).build()).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve wallet context from remote: ", e)
      }

      override fun onResponse(call: Call, response: Response) {
        val body = response.body()
        try {
          if (response.isSuccessful && body != null) {
            val json = JSONObject(body.string()).getJSONObject(BuildConfig.CHAIN)
            log.debug("wallet context responded with {}", json.toString(2))
            // -- version context
            val installedVersion = BuildConfig.VERSION_CODE
            val latestVersion = json.getInt("version")
            val latestCriticalVersion = json.getInt("latest_critical_version")
            notifications.value?.run {
              if (installedVersion < latestCriticalVersion) {
                log.info("a critical update (v$latestCriticalVersion) is deemed available")
                add(InAppNotifications.UPGRADE_WALLET_CRITICAL)
              } else if (latestVersion - installedVersion >= 2) {
                add(InAppNotifications.UPGRADE_WALLET)
              } else {
                remove(InAppNotifications.UPGRADE_WALLET_CRITICAL)
                remove(InAppNotifications.UPGRADE_WALLET)
              }
              notifications.postValue(this)
            }

            // -- trampoline settings
            val trampolineArray = json.getJSONObject("trampoline").getJSONObject("v2").getJSONArray("attempts")
            val trampolineSettingsList = ArrayList<TrampolineFeeSetting>()
            for (i in 0 until trampolineArray.length()) {
              val setting: JSONObject = trampolineArray.get(i) as JSONObject
              trampolineSettingsList += TrampolineFeeSetting(
                feeBase = Converter.any2Msat(Satoshi(setting.getLong("fee_base_sat"))),
                feePercent = setting.getDouble("fee_percent"),
                cltvExpiry = CltvExpiryDelta(setting.getInt("cltv_expiry")))
            }

            trampolineSettingsList.sortedWith(compareBy({ it.feePercent }, { it.feeBase }))
            trampolineFeeSettings.postValue(trampolineSettingsList)
            log.info("trampoline settings set to $trampolineSettingsList")

            // -- swap-in settings
            val remoteSwapInSettings = SwapInSettings(feePercent = json.getJSONObject("swap_in").getJSONObject("v1").getDouble("fee_percent"))
            swapInSettings.postValue(remoteSwapInSettings)
            log.info("swap_in settings set to $remoteSwapInSettings")
          } else {
            log.warn("could not retrieve wallet context from remote, code=${response.code()}")
          }
        } catch (e: Exception) {
          log.error("error when reading wallet context body: ", e)
        } finally {
          body?.close()
        }
      }
    })
  }

  private fun getExchangeRateHandler(context: Context): Callback {
    return object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve exchange rates: ${e.localizedMessage}")
      }

      override fun onResponse(call: Call, response: Response) {
        if (!response.isSuccessful) {
          log.warn("could not retrieve exchange rates, api responds with ${response.code()}")
        } else {
          response.body()?.let {
            try {
              val json = JSONObject(it.string())
              saveRate(context, "AUD") { json.getJSONObject("AUD").getDouble("last").toFloat() }
              saveRate(context, "BRL") { json.getJSONObject("BRL").getDouble("last").toFloat() }
              saveRate(context, "CAD") { json.getJSONObject("CAD").getDouble("last").toFloat() }
              saveRate(context, "CHF") { json.getJSONObject("CHF").getDouble("last").toFloat() }
              saveRate(context, "CLP") { json.getJSONObject("CLP").getDouble("last").toFloat() }
              saveRate(context, "CNY") { json.getJSONObject("CNY").getDouble("last").toFloat() }
              saveRate(context, "DKK") { json.getJSONObject("DKK").getDouble("last").toFloat() }
              saveRate(context, "EUR") { json.getJSONObject("EUR").getDouble("last").toFloat() }
              saveRate(context, "GBP") { json.getJSONObject("GBP").getDouble("last").toFloat() }
              saveRate(context, "HKD") { json.getJSONObject("HKD").getDouble("last").toFloat() }
              saveRate(context, "INR") { json.getJSONObject("INR").getDouble("last").toFloat() }
              saveRate(context, "ISK") { json.getJSONObject("ISK").getDouble("last").toFloat() }
              saveRate(context, "JPY") { json.getJSONObject("JPY").getDouble("last").toFloat() }
              saveRate(context, "KRW") { json.getJSONObject("KRW").getDouble("last").toFloat() }
              saveRate(context, "NZD") { json.getJSONObject("NZD").getDouble("last").toFloat() }
              saveRate(context, "PLN") { json.getJSONObject("PLN").getDouble("last").toFloat() }
              saveRate(context, "RUB") { json.getJSONObject("RUB").getDouble("last").toFloat() }
              saveRate(context, "SEK") { json.getJSONObject("SEK").getDouble("last").toFloat() }
              saveRate(context, "SGD") { json.getJSONObject("SGD").getDouble("last").toFloat() }
              saveRate(context, "THB") { json.getJSONObject("THB").getDouble("last").toFloat() }
              saveRate(context, "TWD") { json.getJSONObject("TWD").getDouble("last").toFloat() }
              saveRate(context, "USD") { json.getJSONObject("USD").getDouble("last").toFloat() }
              Prefs.setExchangeRateTimestamp(context, System.currentTimeMillis())
            } catch (e: Exception) {
              log.error("invalid body for price rate api")
            } finally {
              it.close()
            }
          } ?: log.warn("exchange rate body is null")
        }
      }
    }
  }

  private fun getMXNRateHandler(context: Context): Callback {
    return object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        log.warn("could not retrieve MXN rates: ${e.localizedMessage}")
      }

      override fun onResponse(call: Call, response: Response) {
        if (!response.isSuccessful) {
          log.warn("could not retrieve MXN rates, api responds with ${response.code()}")
        } else {
          response.body()?.let { body ->
            saveRate(context, "MXN") { JSONObject(body.string()).getJSONObject("payload").getDouble("last").toFloat() }
            body.close()
          } ?: log.warn("MXN rate body is null")
        }
      }
    }
  }

  private fun saveRate(context: Context, code: String, rateBlock: () -> Float) {
    val rate = try {
      rateBlock.invoke()
    } catch (e: Exception) {
      log.error("failed to read rate for $code: ", e)
      -1.0f
    }
    Prefs.setExchangeRate(context, code, rate)
  }
}

data class TrampolineFeeSetting(val feeBase: MilliSatoshi, val feePercent: Double, val cltvExpiry: CltvExpiryDelta)
data class SwapInSettings(val feePercent: Double)
