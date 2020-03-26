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

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentFailed
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.phoenix.databinding.ActivityMainBinding
import fr.acinq.phoenix.paymentdetails.PaymentDetailsFragment
import fr.acinq.phoenix.receive.ReceiveWithOpenDialogFragmentDirections
import fr.acinq.phoenix.send.ReadInputFragmentDirections
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.ThemeHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

  val log: Logger = LoggerFactory.getLogger(MainActivity::class.java)
  private lateinit var mBinding: ActivityMainBinding
  private lateinit var app: AppViewModel

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      super.onAvailable(network)
      log.info("network available")
      if (Prefs.isTorEnabled(applicationContext)) {
        app.reconnectTor()
      }
      app.networkInfo.postValue(app.networkInfo.value?.run { networkConnected = true ; this })
      app.reconnectToPeer()
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
      super.onLosing(network, maxMsToLive)
      log.info("losing network....")
    }

    override fun onUnavailable() {
      super.onUnavailable()
      log.info("network unavailable")
    }

    override fun onLost(network: Network) {
      super.onLost(network)
      log.info("network lost")
      app.networkInfo.postValue(app.networkInfo.value?.run { networkConnected = false ; this })
    }
  }

  private val navigationCallback = NavController.OnDestinationChangedListener { _, destination, args ->
    log.debug("destination=${destination.id}, args=$args")
    app.currentNav.postValue(destination.id)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThemeHelper.applyTheme(Prefs.getTheme(applicationContext))
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
    app = ViewModelProvider(this).get(AppViewModel::class.java)
    app.navigationEvent.observe(this, Observer {
      when (it) {
        is PayToOpenRequestEvent -> {
          val action = ReceiveWithOpenDialogFragmentDirections.globalActionAnyToReceiveWithOpen(
            amountMsat = it.payToOpenRequest().amountMsat().toLong(),
            fundingSat = it.payToOpenRequest().fundingSatoshis().toLong(),
            feeSat = it.payToOpenRequest().feeSatoshis().toLong(),
            paymentHash = it.payToOpenRequest().paymentHash().toString(),
            expireAt = it.payToOpenRequest().expireAt())
          findNavController(R.id.nav_host_main).navigate(action)
        }
        is PaymentSent -> {
          val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(PaymentDetailsFragment.OUTGOING, it.id().toString(), fromEvent = true)
          findNavController(R.id.nav_host_main).navigate(action)
        }
        is PaymentFailed -> {
          val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(PaymentDetailsFragment.OUTGOING, it.id().toString(), fromEvent = true)
          findNavController(R.id.nav_host_main).navigate(action)
        }
        is PaymentReceived -> {
          val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(PaymentDetailsFragment.INCOMING, it.paymentHash().toString(), fromEvent = true)
          findNavController(R.id.nav_host_main).navigate(action)
        }
        else -> log.info("unhandled navigation event $it")
      }
    })
    app.state.observe(this, Observer {
      handleUriIntent()
    })
    app.currentURIIntent.observe(this, Observer {
      handleUriIntent()
    })
    // app may be started with a payment request intent
    intent?.let { saveURIIntent(intent) }
  }

  override fun onStart() {
    super.onStart()
    findNavController(R.id.nav_host_main).addOnDestinationChangedListener(navigationCallback)
    val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    saveURIIntent(intent)
  }

  private fun saveURIIntent(intent: Intent) {
    val data = intent.data
    log.debug("reading URI intent=$intent with data=$data")
    if (data != null && data.scheme != null) {
      when (data.scheme) {
        "bitcoin", "lightning" -> {
          app.currentURIIntent.value = data.toString()
        }
        else -> log.info("unhandled payment scheme $data")
      }
    }
  }

  private fun handleUriIntent() {
    log.debug("handle intent=${app.currentURIIntent.value} in state=${app.state.value}")
    if (app.state.value is KitState.Started && app.currentURIIntent.value != null) {
      findNavController(R.id.nav_host_main).navigate(ReadInputFragmentDirections.globalActionAnyToReadInput(app.currentURIIntent.value!!))
      app.currentURIIntent.value = null
    }
  }

  override fun onStop() {
    super.onStop()
    findNavController(R.id.nav_host_main).removeOnDestinationChangedListener(navigationCallback)
    val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    connectivityManager?.unregisterNetworkCallback(networkCallback)
  }

  override fun onDestroy() {
    super.onDestroy()
    log.info("main activity destroyed")
  }

  fun getActivityThis(): Context {
    return this@MainActivity
  }
}
