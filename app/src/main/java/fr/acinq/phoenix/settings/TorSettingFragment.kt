/*
 * Copyright 2020 ACINQ SAS
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsTorBinding
import fr.acinq.phoenix.utils.AlertHelper
import fr.acinq.phoenix.utils.Prefs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class TorSettingFragment : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsTorBinding
  private lateinit var model: TorSettingViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsTorBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(TorSettingViewModel::class.java)
    mBinding.model = model

    app.networkInfo.observe(viewLifecycleOwner, Observer { context?.let { refreshUIState(it) } })
  }

  override fun onStart() {
    super.onStart()
    context?.let { refreshUIState(it) }
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.torSwitch.setOnClickListener {
      val isChecked = mBinding.torSwitch.isChecked()
      AlertHelper.build(layoutInflater, getString(R.string.tor_settings_title),
        getString(if (isChecked) R.string.tor_settings_confirm_disable_title else R.string.tor_settings_confirm_enable_title))
        .setPositiveButton(R.string.utils_proceed) { _, _ ->
          context?.let {
            Prefs.saveTorEnabled(it, !isChecked)
            app.shutdown()
          }
        }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }
  }

  override fun onStop() {
    super.onStop()
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    when (key) {
      Prefs.PREFS_TOR_ENABLED -> context?.let { refreshUIState(it) }
    }
  }

  private fun refreshUIState(context: Context) {
    val isTorEnabled = Prefs.isTorEnabled(context)
    mBinding.torSwitch.setChecked(isTorEnabled)
    mBinding.torSwitch.setText(context.getString(if (isTorEnabled) R.string.tor_settings_enabled else R.string.tor_settings_disabled))
    if (isTorEnabled) {
      getInfo()
      mBinding.getinfoScroll.visibility = View.VISIBLE
      mBinding.getinfoSep.visibility = View.VISIBLE
    } else {
      mBinding.getinfoScroll.visibility = View.GONE
      mBinding.getinfoSep.visibility = View.GONE
    }
  }

  @SuppressLint("SetTextI18n")
  private fun getInfo() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("failed to send getInfo to TOR: ", exception)
      mBinding.getinfoValue.text = "error: failed to get info from tor\n[ ${exception.localizedMessage} ]"
    }) {
      mBinding.getinfoValue.text = """
version=${app.getTorInfo("version")}
network=${app.getTorInfo("network-liveness")}

---
or connections
---
${app.getTorInfo("orconn-status")}

---
circuits
---
${app.getTorInfo("circuit-status")}
      """.trimMargin()
    }
  }
}

class TorSettingViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
}
