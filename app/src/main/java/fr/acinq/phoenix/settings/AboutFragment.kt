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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.BuildConfig
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsAboutBinding
import fr.acinq.phoenix.utils.Converter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AboutFragment : BaseFragment(stayIfNotStarted = true) {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsAboutBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsAboutBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    mBinding.general.text = Converter.html(getString(R.string.about_general))
    mBinding.general.movementMethod = LinkMovementMethod.getInstance()
    mBinding.fiatRates.text = Converter.html(getString(R.string.about_fiat_rates))
    mBinding.fiatRates.movementMethod = LinkMovementMethod.getInstance()
    mBinding.actionBar.setSubtitle(getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
    mBinding.terms.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://phoenix.acinq.co/terms"))) }
    mBinding.privacy.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://phoenix.acinq.co/privacy"))) }
  }

}
