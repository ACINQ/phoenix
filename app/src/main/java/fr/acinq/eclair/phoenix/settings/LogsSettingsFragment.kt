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

package fr.acinq.eclair.phoenix.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.BuildConfig
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsLogsBinding
import fr.acinq.eclair.phoenix.utils.Logging

class LogsSettingsFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSettingsLogsBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsLogsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onStart() {
    super.onStart()
    mBinding.shareButton.setOnClickListener {
      context?.let {
        try {
          val logFile = Logging.getLastLogFile(it)
          val uri = FileProvider.getUriForFile(it, BuildConfig.APPLICATION_ID + ".provider", logFile)
          val shareIntent = Intent(Intent.ACTION_SEND)
          shareIntent.type = "text/plain"
          shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
          shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.logs_share_subject))
          startActivity(Intent.createChooser(shareIntent, getString(R.string.logs_share_title)))
        } catch (e: Exception) {
          log.error("could not share log file: ", e)
        }
      }
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
  }
}
