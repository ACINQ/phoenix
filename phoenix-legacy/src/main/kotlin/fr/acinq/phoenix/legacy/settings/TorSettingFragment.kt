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

package fr.acinq.phoenix.legacy.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.background.KitState
import fr.acinq.phoenix.legacy.databinding.FragmentSettingsTorBinding
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.Prefs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class TorSettingFragment : BaseFragment(stayIfNotStarted = true), SharedPreferences.OnSharedPreferenceChangeListener {

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
    app.networkInfo.observe(viewLifecycleOwner, Observer {
      context?.let { refreshUIState(it) }
    })
  }

  override fun onStart() {
    super.onStart()
    context?.let { refreshUIState(it) }
    context?.let { PreferenceManager.getDefaultSharedPreferences(it).registerOnSharedPreferenceChangeListener(this) }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.torSwitch.setOnClickListener {
      val isChecked = mBinding.torSwitch.isChecked()
      AlertHelper.build(layoutInflater, getString(R.string.tor_settings_title),
        getString(if (isChecked) R.string.tor_settings_confirm_disable_title else R.string.tor_settings_confirm_enable_title))
        .setPositiveButton(R.string.utils_proceed) { _, _ ->
          context?.let {
            Prefs.saveTorEnabled(it, !isChecked)
            if (app.state.value is KitState.Started) {
              app.service?.shutdown()
              findNavController().navigate(R.id.global_action_any_to_startup)
            }
          }
        }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }
  }

  override fun onStop() {
    super.onStop()
    context?.let { PreferenceManager.getDefaultSharedPreferences(it).unregisterOnSharedPreferenceChangeListener(this) }
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
    if (app.state.value is KitState.Started) {
      if (isTorEnabled) {
        getInfo()
        mBinding.getinfoScroll.visibility = View.VISIBLE
        mBinding.getinfoSep.visibility = View.VISIBLE
      } else {
        mBinding.getinfoScroll.visibility = View.GONE
        mBinding.getinfoSep.visibility = View.GONE
      }
    }
  }

  @SuppressLint("SetTextI18n")
  private fun getInfo() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("failed to send getInfo to Tor: ", exception)
      mBinding.getinfoValue.text = "error: failed to get info from Tor\n[ ${exception.localizedMessage} ]"
    }) {
      mBinding.getinfoValue.text = """
version=${app.requireService.getTorInfo("version")}
network=${app.requireService.getTorInfo("network-liveness")}

---
or connections
---
${app.requireService.getTorInfo("orconn-status")}

---
circuits
---
${app.requireService.getTorInfo("circuit-status")}
      """.trimMargin()
    }
  }
}

class TorSettingViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
}
