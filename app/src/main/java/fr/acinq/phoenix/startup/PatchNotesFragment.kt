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

package fr.acinq.phoenix.startup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.BuildConfig
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentPatchNotesBinding
import fr.acinq.phoenix.utils.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PatchNotesFragment : BaseFragment(stayIfNotStarted = true) {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentPatchNotesBinding
  private val args: PatchNotesFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentPatchNotesBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    log.info("displaying patch notes from version=${args.version} to version=${BuildConfig.VERSION_CODE}")



    val notes = Migration.listNotableChangesSince(args.version.toInt()).map {
      log.debug("adding patch note for version=$it")
      val swapInFee = 100 * (appContext()?.swapInSettings?.value?.feePercent ?: Constants.DEFAULT_SWAP_IN_SETTINGS.feePercent)
      getString(R.string.patchnotes_v15, String.format("%.2f", swapInFee))
    }.joinToString("<br />")
    log.info("notes=$notes")
    mBinding.content.text = Converter.html(notes)
  }

  override fun onStart() {
    super.onStart()
    mBinding.closeButton.setOnClickListener {
      context?.let {
        log.info("patch notes has been read for version=${args.version}, redirecting to startup")
        Prefs.setMigratedFrom(it, 0)
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    }
  }
}

