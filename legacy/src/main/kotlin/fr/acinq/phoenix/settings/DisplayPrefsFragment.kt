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
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsPrefsDisplayBinding
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.ThemeHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DisplayPrefsFragment : BaseFragment(stayIfNotStarted = true), SharedPreferences.OnSharedPreferenceChangeListener {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsPrefsDisplayBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsPrefsDisplayBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.run {
      this.supportFragmentManager
        .beginTransaction()
        .replace(R.id.prefs_display_fragment_container, InnerPreferencesFragment())
        .commit()
    }
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
  }

  override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
    activity?.run {
      when (key) {
        Prefs.PREFS_THEME -> ThemeHelper.applyTheme(Prefs.getTheme(applicationContext))
        Prefs.PREFS_COIN_UNIT -> Converter.refreshCoinPattern(applicationContext)
      }
    }
  }

  class InnerPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
      addPreferencesFromResource(R.xml.prefs_display)
    }
  }
}


