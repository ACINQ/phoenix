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

package fr.acinq.phoenix.legacy.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.eclair.`JsonSerializers$`
import fr.acinq.phoenix.legacy.AppViewModel
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentMainSunsetBinding
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import upickle.`default$`
import java.io.IOException
import java.text.NumberFormat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MainFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentMainSunsetBinding
  private lateinit var model: MainSunsetViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentMainSunsetBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(MainSunsetViewModel::class.java)
    mBinding.model = model
    model.getFinalWalletBalance(app)
  }

  override fun onStart() {
    super.onStart()

    mBinding.settingsButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_settings) }
    mBinding.upgradeButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_migration) }
    mBinding.copyDebugButton.setOnClickListener {
      lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
        log.error("error when retrieving list of channels: ", exception)
        Toast.makeText(context, "Could not copy channel data", Toast.LENGTH_SHORT).show()
      }) {
        val finalAddress = app.requireService.state.value?.kit()?.wallet()?.receiveAddress?.value()?.get()?.get()
        val channels = app.requireService.getChannels(null).toMutableList()
        val nodeId = app.state.value?.getNodeId()?.toString() ?: getString(R.string.legacy_utils_unknown)
        val data = channels.joinToString("\n\n", "", "", -1) { `default$`.`MODULE$`.write(it, 1, `JsonSerializers$`.`MODULE$`.cmdResGetinfoReadWriter()) }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Legacy channels data", """
          legacy_node_id=$nodeId
          final_address=$finalAddress
          channels_data=${data.takeIf { it.isNotBlank() } ?: "none"}
        """.trimIndent()))
        Toast.makeText(context, "Data copied!", Toast.LENGTH_SHORT).show()
      }
    }
  }
}

class MainSunsetViewModel : ViewModel() {
  val log = LoggerFactory.getLogger(this::class.java)
  val finalWalletBalance = MutableLiveData<Satoshi?>(null)

  fun getFinalWalletBalance(appViewModel: AppViewModel) {
    viewModelScope.launch {
      fetchFinalWalletBalance(appViewModel)
    }
  }

  private suspend fun fetchFinalWalletBalance(appViewModel: AppViewModel) {
    val address = appViewModel.service?.state?.value?.kit()?.wallet()?.receiveAddress?.value()?.get()?.get()
    if (!address.isNullOrBlank()) {
      Wallet.httpClient.newCall(Request.Builder().url("${Constants.MEMPOOLSPACE_EXPLORER_URL}/api/address/$address").build()).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          log.warn("could not retrieve address details from mempool.space: ${e.localizedMessage}")
          finalWalletBalance.postValue(null)
        }

        override fun onResponse(call: Call, response: Response) {
          if (!response.isSuccessful) {
            log.warn("mempool.space returned error=${response.code()}")
            finalWalletBalance.postValue(null)
          } else {
            response.body()?.let {
              try {
                val json = JSONObject(it.string())
                val (funded, spent) = json.getJSONObject("chain_stats").run {
                  getLong("funded_txo_sum") to getLong("spent_txo_sum")
                }
                val (pendingFunded, pendingSpent) = json.getJSONObject("mempool_stats").run {
                  getLong("funded_txo_sum") to getLong("spent_txo_sum")
                }
                val available = (funded - spent) + (pendingFunded - pendingSpent)
                finalWalletBalance.postValue(Satoshi(available))
              } catch (e: Exception) {
                log.error("could not parse address data from mempool.space: ${e.localizedMessage}", e)
                finalWalletBalance.postValue(null)
              }
            }
          }
        }
      })
      delay(10.minutes)
    } else {
      delay(7.seconds)
    }
    fetchFinalWalletBalance(appViewModel)
  }

  val finalWalletBalanceDisplay : LiveData<String> = finalWalletBalance.map {
    when (it) {
      null -> "scanning address..."
      else -> "${NumberFormat.getInstance().format(it.toLong())} sat"
    }
  }
}
