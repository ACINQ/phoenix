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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.common.base.Strings
import fr.acinq.eclair.`package$`
import fr.acinq.phoenix.BaseFragment
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
  private lateinit var model: ElectrumServerViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsElectrumServerBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(ElectrumServerViewModel::class.java)
    appKit.kit.observe(viewLifecycleOwner, Observer {
      // -- xpub / feerate from appkit
      if (appKit.kit.value == null) {
        mBinding.feeRate.text = getString(R.string.utils_unknown)
        mBinding.xpub.text = getString(R.string.utils_unknown)
      } else {
        mBinding.xpub.text = getString(R.string.electrum_xpub_value, it.xpub.xpub, it.xpub.path)
        val feeRate = `package$`.`MODULE$`.feerateKw2Byte(appKit.kit.value!!.kit.nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(1))
        mBinding.feeRate.visibility = View.VISIBLE
        mBinding.feeRate.text = getString(R.string.electrum_fee_rate, NumberFormat.getInstance().format(feeRate))
      }
    })
    appKit.networkInfo.observe(viewLifecycleOwner, Observer {
      context?.let { ctx ->
        val electrumServer = it.electrumServer
        if (electrumServer == null) {
          // -- no connection to electrum server yet
          val prefElectrumAddress = Prefs.getElectrumServer(ctx)
          mBinding.connectionStateValue.text = Converter.html(if (Strings.isNullOrEmpty(prefElectrumAddress)) {
            resources.getString(R.string.electrum_connecting)
          } else {
            resources.getString(R.string.electrum_connecting_to_custom, prefElectrumAddress)
          })
        } else {
          // -- successfully connected to electrum
          mBinding.connectionStateValue.text = Converter.html(resources.getString(R.string.electrum_connected, it.electrumServer.electrumAddress))
          mBinding.tipTime.text = Transcriber.plainTime(it.electrumServer.tipTime * 1000L)
          mBinding.blockHeight.text = NumberFormat.getInstance().format(it.electrumServer.blockHeight)
        }
      }
    })
    mBinding.model = model
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

  private fun getElectrumDialog(context: Context): AlertDialog {
    val view = layoutInflater.inflate(R.layout.dialog_electrum, null)
    val sslWarning = view.findViewById<TextView>(R.id.elec_dialog_ssl)
    val checkbox = view.findViewById<CheckBox>(R.id.elec_dialog_checkbox)
    val inputLabel = view.findViewById<TextView>(R.id.elec_dialog_input_label)
    val inputValue = view.findViewById<TextInputEditText>(R.id.elec_dialog_input_value)
    val currentPrefsAddress = Prefs.getElectrumServer(context)

    fun updateState(isChecked: Boolean) {
      BindingHelpers.enableOrFade(inputLabel, isChecked)
      BindingHelpers.enableOrFade(inputValue, isChecked)
      BindingHelpers.enableOrFade(sslWarning, isChecked)
    }

    checkbox.setOnCheckedChangeListener { v, isChecked -> updateState(isChecked) }

    if (Strings.isNullOrEmpty(currentPrefsAddress)) {
      checkbox.isChecked = false
      updateState(false)
    } else {
      checkbox.isChecked = true
      inputValue.setText(currentPrefsAddress)
      updateState(true)
    }

    val dialog = AlertDialog.Builder(context, R.style.default_dialogTheme)
      .setView(view)
      .setPositiveButton(R.string.btn_confirm, null)
      .setNegativeButton(R.string.btn_cancel) { _, _ -> }
      .create()

    dialog.setOnShowListener {
      val confirmButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
      confirmButton.setOnClickListener {
        val address = inputValue.text.toString()
        if (checkbox.isChecked && Strings.isNullOrEmpty(address)) {
          Toast.makeText(context, R.string.electrum_empty_custom_address, Toast.LENGTH_SHORT).show()
        } else {
          Prefs.saveElectrumServer(context, if (checkbox.isChecked) inputValue.text.toString() else "")
          dialog.dismiss()
          appKit.shutdown()
        }
      }
    }

    return dialog
  }
}

enum class ElectrumServerState {
  INIT, IN_PROGRESS, DONE, ERROR
}

class ElectrumServerViewModel : ViewModel() {

  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<ElectrumServerState>()

  init {
    state.value = ElectrumServerState.INIT
  }

}
