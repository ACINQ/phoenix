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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsFeesBinding
import fr.acinq.phoenix.send.SendState
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Integer.min


class FeesSettingsFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsFeesBinding
  private lateinit var model: FeesSettingsViewModel

  private lateinit var feeSettingsLabelList: List<String>

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsFeesBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(FeesSettingsViewModel::class.java)

    appKit.trampolineFeeSettings.value?.let { feeSettingsList ->
      context?.let { ctx ->
        feeSettingsLabelList = feeSettingsList.map {
          ctx.getString(R.string.fees_settings_trampoline_spinner_label,
            Converter.printAmountPretty(it.feeBase, ctx, withUnit = true), String.format("%.2f", it.feePercent))
        }
        ArrayAdapter(ctx, android.R.layout.simple_spinner_item, feeSettingsLabelList).also { adapter ->
          adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
          mBinding.trampolineFeeBox.adapter = adapter
        }
        val maxFeeIndex = Prefs.getTrampolineMaxFeeIndex(ctx)
        mBinding.trampolineFeeBox.setSelection(min(maxFeeIndex, feeSettingsList.size - 1))
      }
    }

    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.trampolineFeeBox.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (context != null && position < appKit.trampolineFeeSettings.value?.size ?: 0) {
          Prefs.saveTrampolineMaxFeeIndex(context!!, position)
        }
      }
    }
  }
}

enum class FeeSettingsState {
  INIT, IN_PROGRESS, DONE, ERROR
}

class FeesSettingsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<FeeSettingsState>()

  init {
    state.value = FeeSettingsState.INIT
  }

}
