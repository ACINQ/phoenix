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

package fr.acinq.eclair.phoenix

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentFailed
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.eclair.phoenix.databinding.ActivityMainBinding
import fr.acinq.eclair.phoenix.paymentdetails.PaymentDetailsFragment
import fr.acinq.eclair.phoenix.receive.ReceiveWithOpenDialogFragmentDirections
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

  val log = LoggerFactory.getLogger(MainActivity::class.java)
  private lateinit var mBinding: ActivityMainBinding
  private lateinit var appKit: AppKitModel

  private val connectivitySnackbar: Snackbar by lazy {
    Snackbar.make(mBinding.root, R.string.main_connectivity_issue, Snackbar.LENGTH_INDEFINITE)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.setTheme(Prefs.getTheme(applicationContext))
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
    appKit = ViewModelProvider(this).get(AppKitModel::class.java)
    appKit.networkAvailable.observe(this, Observer {
      if (it) {
        connectivitySnackbar.dismiss()
      } else {
        connectivitySnackbar.show()
      }
    })
    appKit.navigationEvent.observe(this, Observer {
      when (it) {
        is PayToOpenRequestEvent -> {
          val action = ReceiveWithOpenDialogFragmentDirections.globalActionAnyToReceiveWithOpen(
            amountMsat = it.payToOpenRequest().amountMsat().toLong(),
            fundingSat = it.payToOpenRequest().fundingSatoshis().toLong(),
            feeSat = it.payToOpenRequest().feeSatoshis().toLong(),
            paymentHash = it.payToOpenRequest().paymentHash().toString())
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
  }

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      super.onAvailable(network)
      log.info("network available")
      appKit.networkAvailable.postValue(true)
    }

    override fun onLost(network: Network) {
      super.onLost(network)
      log.info("network lost")
      appKit.networkAvailable.postValue(false)
    }
  }

  override fun onStart() {
    super.onStart()
    val connectivityManager: ConnectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
  }

  override fun onResume() {
    super.onResume()
    appKit.reconnect()
  }

  override fun onStop() {
    super.onStop()
    val connectivityManager: ConnectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    connectivityManager.unregisterNetworkCallback(networkCallback)
  }

  override fun onDestroy() {
    super.onDestroy()
    log.info("main activity destroyed")
  }

  fun getActivityThis(): Context {
    return this@MainActivity
  }
}
