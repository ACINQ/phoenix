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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.phoenix.databinding.ActivityMainBinding
import fr.acinq.eclair.phoenix.receive.ReceiveWithOpenDialogFragmentDirections
import fr.acinq.eclair.phoenix.send.SendFragmentDirections
import fr.acinq.eclair.phoenix.utils.IntentCodes
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

  val log = LoggerFactory.getLogger(MainActivity::class.java)
  private lateinit var mBinding: ActivityMainBinding
  private lateinit var appKit: AppKitModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
    appKit = ViewModelProvider(this).get(AppKitModel::class.java)
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
        else -> log.info("unhandled navigation event $it")
      }
    })
  }

  override fun onDestroy() {
    super.onDestroy()
    log.info("main activity destroyed")
  }

  fun getActivityThis(): Context {
    return this@MainActivity
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == IntentCodes.SCAN_PAYMENT_REQUEST_RESULT) {
      if (resultCode == Activity.RESULT_OK) {
        val invoice = data!!.getStringExtra(IntentCodes.SEND_PAYMENT_INVOICE_EXTRA)
        val action = SendFragmentDirections.globalActionAnyToSend(invoice)
        findNavController(R.id.nav_host_main).navigate(action)
      }
    }
  }
}
