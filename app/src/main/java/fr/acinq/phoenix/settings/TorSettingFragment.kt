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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.databinding.FragmentSettingsTorBinding
import fr.acinq.phoenix.utils.Prefs
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
  }

  override fun onStart() {
    super.onStart()
    context?.let { mBinding.torSwitch.setChecked(Prefs.isTorEnabled(it)) }
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    mBinding.torSwitch.setOnClickListener {
      val isChecked = mBinding.torSwitch.isChecked()
      context?.let {
        Prefs.saveTorEnabled(it, !isChecked)
        app.shutdown()
      }
    }
  }

  override fun onStop() {
    super.onStop()
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    when (key) {
      Prefs.PREFS_TOR_ENABLED -> context?.let { mBinding.torSwitch.setChecked(Prefs.isTorEnabled(it)) }
    }
  }
}

class TorSettingViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
//  val torEnabled = MutableLiveData<Boolean>()
//
//  init {
//    torEnabled.value = false
//  }
}
