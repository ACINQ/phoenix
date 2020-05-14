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

package fr.acinq.phoenix.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.common.base.Strings
import fr.acinq.eclair.`package$`
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.KitState
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsElectrumServerBinding
import fr.acinq.phoenix.utils.BindingHelpers
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Transcriber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.NumberFormat


class ElectrumServerFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsElectrumServerBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsElectrumServerBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    app.state.observe(viewLifecycleOwner, Observer {
      when (it) {
        is KitState.Started -> {
          // -- xpub / feerate from appkit
          mBinding.xpub.text = getString(R.string.electrum_xpub_value, it.xpub.xpub, it.xpub.path)
          val feeRate = `package$`.`MODULE$`.feerateKw2Byte(it.kit.nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(1))
          mBinding.feeRate.visibility = View.VISIBLE
          mBinding.feeRate.text = getString(R.string.electrum_fee_rate, NumberFormat.getInstance().format(feeRate))
        }
        else -> {
          mBinding.feeRate.text = getString(R.string.utils_unknown)
          mBinding.xpub.text = getString(R.string.utils_unknown)
        }
      }
    })
    app.networkInfo.observe(viewLifecycleOwner, Observer {
      context?.let { ctx ->
        val electrumServer = it.electrumServer
        if (electrumServer == null) {
          // -- no connection to electrum server yet
          val prefElectrumAddress = Prefs.getElectrumServer(ctx)
          mBinding.connectionStateValue.text = Converter.html(
            if (app.state.value is KitState.Started) {
              if (Strings.isNullOrEmpty(prefElectrumAddress)) {
                resources.getString(R.string.electrum_connecting)
              } else {
                resources.getString(R.string.electrum_connecting_to_custom, prefElectrumAddress)
              }
            } else {
              if (Strings.isNullOrEmpty(prefElectrumAddress)) {
                resources.getString(R.string.electrum_not_connected)
              } else {
                resources.getString(R.string.electrum_not_connected_to_custom, prefElectrumAddress)
              }
            })
        } else {
          // -- successfully connected to electrum
          mBinding.connectionStateValue.text = Converter.html(resources.getString(R.string.electrum_connected, it.electrumServer.electrumAddress))
          mBinding.tipTime.text = Transcriber.plainTime(it.electrumServer.tipTime * 1000L)
          mBinding.blockHeight.text = NumberFormat.getInstance().format(it.electrumServer.blockHeight)
        }
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.changeServerButton.setOnClickListener {
      context?.let {
        getElectrumDialog(it).show()
      }
    }
  }

  override fun handleKitState(state: KitState) {}

  private fun getElectrumDialog(context: Context): AlertDialog {
    val view = layoutInflater.inflate(R.layout.dialog_electrum, null)
    val useCustomElectrumBox = view.findViewById<CheckBox>(R.id.elec_dialog_checkbox)
    val addressLabel = view.findViewById<TextView>(R.id.elec_dialog_input_label)
    val addressInput = view.findViewById<TextInputEditText>(R.id.elec_dialog_input_value)
    val sslLayout = view.findViewById<View>(R.id.elec_dialog_ssl)
    val sslInfoText = view.findViewById<TextView>(R.id.elec_dialog_ssl_info)
    val sslForceCheckbox = view.findViewById<CheckBox>(R.id.elec_dialog_ssl_force_checkbox)
    val currentPrefsAddress = Prefs.getElectrumServer(context)

    fun updateState(isChecked: Boolean) {
      BindingHelpers.enableOrFade(addressLabel, isChecked)
      BindingHelpers.enableOrFade(addressInput, isChecked)
      BindingHelpers.enableOrFade(sslLayout, isChecked)
    }

    fun updateSSLLayout(input: String) {
      val isOnion = input.endsWith(".onion")
      BindingHelpers.show(sslInfoText, !isOnion)
      BindingHelpers.show(sslForceCheckbox, isOnion)
    }

    useCustomElectrumBox.setOnCheckedChangeListener { _, isChecked -> updateState(isChecked) }
    addressInput.addTextChangedListener { updateSSLLayout(it.toString()) }

    // initial state
    if (currentPrefsAddress.isBlank()) {
      useCustomElectrumBox.isChecked = false
      updateState(false)
    } else {
      useCustomElectrumBox.isChecked = true
      addressInput.setText(currentPrefsAddress)
      updateState(true)
    }
    updateSSLLayout(currentPrefsAddress)
    sslForceCheckbox.isChecked = Prefs.getForceElectrumSSL(context)

    val dialog = AlertDialog.Builder(context, R.style.default_dialogTheme)
      .setView(view)
      .setPositiveButton(R.string.btn_confirm, null)
      .setNegativeButton(R.string.btn_cancel) { _, _ -> }
      .create()

    dialog.setOnShowListener {
      val confirmButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
      confirmButton.setOnClickListener {
        val address = addressInput.text.toString()
        if (useCustomElectrumBox.isChecked && address.isBlank()) {
          Toast.makeText(context, R.string.electrum_empty_custom_address, Toast.LENGTH_SHORT).show()
        } else {
          Prefs.saveElectrumServer(context, if (useCustomElectrumBox.isChecked) address else "")
          if (address.endsWith(".onion")) {
            Prefs.saveForceElectrumSSL(context, sslForceCheckbox.isChecked)
          }
          dialog.dismiss()
          if (app.state.value is KitState.Started) {
            app.shutdown()
            findNavController().navigate(R.id.global_action_any_to_startup)
          } else {
            findNavController().popBackStack()
          }
        }
      }
    }

    return dialog
  }
}
