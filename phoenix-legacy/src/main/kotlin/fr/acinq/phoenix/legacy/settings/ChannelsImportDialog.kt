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

package fr.acinq.phoenix.legacy.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import fr.acinq.phoenix.legacy.AppViewModel
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentSettingsChannelsImportBinding
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scodec.bits.`ByteVector$`

class ChannelsImportDialog : DialogFragment() {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  lateinit var mBinding: FragmentSettingsChannelsImportBinding
  private lateinit var app: AppViewModel
  private lateinit var model: ChannelsImportViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsChannelsImportBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    isCancelable = false
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      app = ViewModelProvider(it).get(AppViewModel::class.java)
      model = ViewModelProvider(this).get(ChannelsImportViewModel::class.java)
      mBinding.model = model

      model.state.observe(viewLifecycleOwner) { state ->
        when (state) {
          is ChannelsImportState.Error.Default -> mBinding.errorMessage.text = getString(R.string.legacy_channels_import_failure, state.message)
          is ChannelsImportState.Error.InvalidInput -> mBinding.errorMessage.text = getString(R.string.legacy_channels_import_failure, getString(R.string.legacy_channels_import_failure_invalid))
          is ChannelsImportState.Error.DecryptionFailure -> mBinding.errorMessage.text = getString(R.string.legacy_channels_import_failure, getString(R.string.legacy_channels_import_failure_decryption))
          is ChannelsImportState.Error.DbFailure -> mBinding.errorMessage.text = getString(R.string.legacy_channels_import_failure, getString(R.string.legacy_channels_import_failure_db))
          is ChannelsImportState.Success -> mBinding.successMessage.text = getString(R.string.legacy_channels_import_success, state.channelsCount)
          else -> {}
        }
      }

    } ?: dismiss()
  }

  override fun onStart() {
    super.onStart()
    mBinding.dismissButton.setOnClickListener { dismiss() }
    mBinding.importButton.setOnClickListener { importChannelsData() }
    mBinding.successButton.setOnClickListener { app.service?.shutdown() }
  }

  private fun importChannelsData() {
    val service = app.service
    if (service == null || model.state.value != ChannelsImportState.Init) {
      log.debug("unable to import data with service=$service and state=${model.state.value}")
      return
    }

    model.state.value = ChannelsImportState.Importing.Reading

    lifecycleScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, e ->
      when (e) {
        is ImportException -> model.state.postValue(e.error)
        else -> {
          log.error("failed to import channels: ", e)
          model.state.postValue(ChannelsImportState.Error.Default(e.localizedMessage ?: e.javaClass.simpleName))
        }
      }
    }) {
      delay(200)
      val input = mBinding.input.text.toString().split(";").toSet()
      if (input.isEmpty()) {
        throw ImportException(ChannelsImportState.Error.InvalidInput)
      } else {
        model.state.value = ChannelsImportState.Importing.Decrypting
        delay(400)
        log.info("decrypting data for ${input.size} channel(s)")
        val commitments = try {
          input.map { service.decryptChannelData(`ByteVector$`.`MODULE$`.apply(Hex.decode(it))) }
        } catch (e: Exception) {
          log.error("failed to decrypt channels data: ", e)
          throw ImportException(ChannelsImportState.Error.DecryptionFailure)
        }

        model.state.value = ChannelsImportState.Importing.Writing
        delay(400)
        log.info("writing ${commitments.size} channel(s) to database")
        try {
          service.addChannels(commitments)
          model.state.postValue(ChannelsImportState.Success(commitments.size))
        } catch (e: Exception) {
          log.error("failed to write channels data to database: ", e)
          throw ImportException(ChannelsImportState.Error.DbFailure)
        }
      }
    }
  }
}

private class ImportException(val error: ChannelsImportState.Error): RuntimeException()

sealed class ChannelsImportState {
  object Init : ChannelsImportState()
  sealed class Importing : ChannelsImportState() {
    object Reading: Importing()
    object Decrypting: Importing()
    object Writing: Importing()
  }
  data class Success(val channelsCount: Int) : ChannelsImportState()
  sealed class Error : ChannelsImportState() {
    data class Default(val message: String) : Error()
    object InvalidInput : Error()
    object DecryptionFailure : Error()
    object DbFailure : Error()
  }
}

class ChannelsImportViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<ChannelsImportState>(ChannelsImportState.Init)
}
