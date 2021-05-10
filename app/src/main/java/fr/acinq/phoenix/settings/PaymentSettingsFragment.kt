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
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsPaymentBinding
import fr.acinq.phoenix.utils.AlertHelper
import fr.acinq.phoenix.utils.BindingHelpers
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PaymentSettingsFragment : BaseFragment(stayIfNotStarted = false) {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsPaymentBinding

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_PAYMENT_DEFAULT_DESCRIPTION
      || key == Prefs.PREFS_CUSTOM_MAX_BASE_TRAMPOLINE_FEE || key == Prefs.PREFS_CUSTOM_MAX_PROPORTIONAL_TRAMPOLINE_FEE) {
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
    mBinding.defaultDescriptionButton.setOnClickListener {
      context?.let { ctx ->
        AlertHelper.buildWithInput(layoutInflater,
          title = ctx.getString(R.string.paymentsettings_defaultdesc_dialog_title),
          message = ctx.getString(R.string.paymentsettings_defaultdesc_dialog_description),
          callback = { value -> context?.let { Prefs.setDefaultPaymentDescription(it, value) } },
          defaultValue = Prefs.getDefaultPaymentDescription(ctx),
          hint = ctx.getString(R.string.paymentsettings_defaultdesc_dialog_hint))
          .setNegativeButton(getString(R.string.utils_cancel), null)
          .show()
      }
    }
    mBinding.trampolineFeesButton.setOnClickListener { context?.let { getMaxTrampolineFeeDialog(it)?.show() } }
    mBinding.payToOpenFeesButton.setOnClickListener {
      AlertHelper.build(layoutInflater, title = getString(R.string.paymentsettings_paytoopen_fees_dialog_title),
        message = Converter.html(getString(R.string.paymentsettings_paytoopen_fees_dialog_message)))
        .show()
    }
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
  }

  override fun onStop() {
    super.onStop()
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
  }

  private fun refreshUI() {
    context?.let { ctx ->
      mBinding.defaultDescriptionButton.setSubtitle(Prefs.getDefaultPaymentDescription(ctx).takeIf { it.isNotBlank() } ?: getString(R.string.paymentsettings_defaultdesc_none))

      val trampolineFeeSetting = Prefs.getMaxTrampolineCustomFee(ctx) ?: appContext(ctx).trampolineFeeSettings.value?.last()
      val payToOpenSettings = appContext(ctx).payToOpenSettings.value

      if (trampolineFeeSetting != null) {
        mBinding.trampolineFeesButton.setSubtitle(getString(R.string.paymentsettings_trampoline_fees_desc,
          Converter.printAmountPretty(trampolineFeeSetting.feeBase, ctx, withUnit = true), trampolineFeeSetting.printFeeProportional()))
      } else {
        mBinding.trampolineFeesButton.setSubtitle(getString(R.string.utils_unavailable))
      }

      if (payToOpenSettings != null) {
        mBinding.payToOpenFeesButton.setSubtitle(getString(R.string.paymentsettings_paytoopen_fees_desc,
          String.format("%.2f", 100 * (payToOpenSettings.feePercent)),
          Converter.printAmountPretty(payToOpenSettings.minFee, ctx, withUnit = true)))
      } else {
        mBinding.payToOpenFeesButton.setSubtitle(getString(R.string.utils_unavailable))
      }
    }
  }

  private fun getMaxTrampolineFeeDialog(context: Context): AlertDialog? {
    val view = layoutInflater.inflate(R.layout.dialog_max_trampoline_fee, null)
    val overrideDefaultCheckbox = view.findViewById<CheckBox>(R.id.trampoline_fee_override_default_checkbox)
    val baseFeeLabel = view.findViewById<TextView>(R.id.trampoline_fee_max_base_fee_label)
    val baseFeeInput = view.findViewById<EditText>(R.id.trampoline_fee_max_base_fee_value)
    val proportionalFeeLabel = view.findViewById<TextView>(R.id.trampoline_fee_max_proportional_fee_label)
    val proportionalFeeInput = view.findViewById<EditText>(R.id.trampoline_fee_max_proportional_fee_value)
    val prefsFeeSetting = Prefs.getMaxTrampolineCustomFee(context)
    val defaultFeeSetting = appContext(context).trampolineFeeSettings.value?.last() ?: return null

    fun updateState() {
      val isChecked = overrideDefaultCheckbox.isChecked
      BindingHelpers.enableOrFade(baseFeeLabel, isChecked)
      BindingHelpers.enableOrFade(baseFeeInput, isChecked)
      BindingHelpers.enableOrFade(proportionalFeeLabel, isChecked)
      BindingHelpers.enableOrFade(proportionalFeeInput, isChecked)
    }

    overrideDefaultCheckbox.text = getString(R.string.paymentsettings_trampoline_fees_dialog_override_default_checkbox)
    overrideDefaultCheckbox.setOnCheckedChangeListener { _, _ -> updateState() }

    if (prefsFeeSetting != null) {
      overrideDefaultCheckbox.isChecked = true
      baseFeeInput.setText(Converter.printAmountRawForceUnit(prefsFeeSetting.feeBase, `SatUnit$`.`MODULE$`))
      proportionalFeeInput.setText(prefsFeeSetting.printFeeProportional())
      updateState()
    } else {
      overrideDefaultCheckbox.isChecked = false
      baseFeeInput.setText(Converter.printAmountRawForceUnit(defaultFeeSetting.feeBase, `SatUnit$`.`MODULE$`))
      proportionalFeeInput.setText(defaultFeeSetting.printFeeProportional())
      updateState()
    }

    val dialog = AlertDialog.Builder(context, R.style.default_dialogTheme)
      .setView(view)
      .setPositiveButton(R.string.btn_confirm, null) // overridden below
      .setNegativeButton(R.string.btn_cancel) { _, _ -> }
      .create()

    dialog.setOnShowListener {
      val confirmButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
      confirmButton.setOnClickListener {
        if (overrideDefaultCheckbox.isChecked) {
          val baseFee = try {
            Satoshi(baseFeeInput.text.toString().toLong())
          } catch (e: Exception) {
            log.debug("invalid base fee: ", e)
            null
          }
          val proportionalFee = try {
            Converter.percentageToPerMillionths(proportionalFeeInput.text.toString())
          } catch (e: Exception) {
            log.debug("invalid proportional fee: ", e)
            null
          }
          if (baseFee == null || proportionalFee == null) {
            Toast.makeText(context, "Enter a valid maximum fee setting", Toast.LENGTH_SHORT).show()
          } else {
            log.info("update max trampoline fee to base=$baseFee proportional=$proportionalFee")
            Prefs.setMaxTrampolineCustomFee(context, baseFee, proportionalFee)
            dialog.dismiss()
          }
        } else {
          if (Prefs.removeMaxTrampolineCustomFee(context)) {
            dialog.dismiss()
          }
        }
      }
    }

    return dialog
  }
}
