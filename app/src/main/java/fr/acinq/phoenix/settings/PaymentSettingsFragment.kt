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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsPaymentBinding
import fr.acinq.phoenix.utils.AlertHelper
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.customviews.SwitchView
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PaymentSettingsFragment : BaseFragment(stayIfNotStarted = false) {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsPaymentBinding

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_PAYMENT_DEFAULT_DESCRIPTION || key == Prefs.PREFS_AUTO_ACCEPT_PAY_TO_OPEN) {
      refreshUI()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsPaymentBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onStart() {
    super.onStart()
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(prefsListener)
    refreshUI()
    mBinding.payToOpenAutoSwitch.setSubtitle(getString(R.string.paymentsettings_paytoopen_auto_desc,
      String.format("%.2f", 100 * (appContext()?.swapInSettings?.value?.feePercent ?: Constants.DEFAULT_SWAP_IN_SETTINGS.feePercent))))
    mBinding.defaultDescriptionButton.setOnClickListener {
      context?.let { ctx ->
        AlertHelper.buildWithInput(layoutInflater,
          title = ctx.getString(R.string.paymentsettings_defaultdesc_dialog_title),
          message = ctx.getString(R.string.paymentsettings_defaultdesc_dialog_description),
          callback = { value ->
            context?.let { Prefs.setDefaultPaymentDescription(it, value) }
          },
          defaultValue = Prefs.getDefaultPaymentDescription(ctx))
          .setNegativeButton(getString(R.string.utils_cancel), null)
          .show()
      }
    }
    mBinding.payToOpenAutoSwitch.setOnClickListener {
      context?.let { ctx ->
        if ((it as SwitchView).isChecked()) {
          Prefs.setAutoAcceptPayToOpen(ctx, false)
        } else {
          val swapInFee = String.format("%.2f", 100 * (appContext(ctx).swapInSettings.value?.feePercent ?: Constants.DEFAULT_SWAP_IN_SETTINGS.feePercent))
          AlertHelper.build(layoutInflater,
            title = ctx.getString(R.string.paymentsettings_paytoopen_dialog_title),
            message = ctx.getString(R.string.paymentsettings_paytoopen_dialog_description, swapInFee))
            .setPositiveButton(getString(R.string.utils_ok)) { _, _ -> Prefs.setAutoAcceptPayToOpen(ctx, true) }
            .setNegativeButton(getString(R.string.utils_cancel), null)
            .show()
        }
      }
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
  }

  override fun onStop() {
    super.onStop()
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
  }

  private fun refreshUI() {
    context?.let {
      mBinding.payToOpenAutoSwitch.setChecked(Prefs.getAutoAcceptPayToOpen(it))
      mBinding.defaultDescriptionButton.setSubtitle(it.getString(R.string.paymentsettings_defaultdesc_desc, Prefs.getDefaultPaymentDescription(it)))
    }
  }
}
