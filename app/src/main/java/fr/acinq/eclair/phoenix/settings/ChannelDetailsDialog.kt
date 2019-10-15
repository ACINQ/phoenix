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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.`JsonSerializers$`
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.eclair.phoenix.AppKitModel
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsChannelDetailsBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import upickle.`default$`

class ChannelDetailsDialog : DialogFragment() {

  val log: Logger = LoggerFactory.getLogger(ChannelDetailsDialog::class.java)

  lateinit var mBinding: FragmentSettingsChannelDetailsBinding
  private lateinit var appKit: AppKitModel
  private lateinit var model: ChannelDetailsViewModel

  private val args: ChannelDetailsDialogArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentSettingsChannelDetailsBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      appKit = ViewModelProvider(it).get(AppKitModel::class.java)
      model = ViewModelProvider(this).get(ChannelDetailsViewModel::class.java)
      mBinding.model = model
    } ?: dismiss()
    model.rawData.observe(viewLifecycleOwner, Observer { json ->
      json?.run { mBinding.rawData.text = this } // direct assignment has better perfs than binding
    })
  }

  override fun onStart() {
    super.onStart()
    if (model.state.value == ChannelDetailsState.INIT) {
      getChannel()
    }

    mBinding.copyButton.setOnClickListener {
      context?.let { ctx ->
        try {
          val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.primaryClip = ClipData.newPlainText("Channel data", model.rawData.value)
          Toast.makeText(ctx, getString(R.string.utils_copied), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          log.error("failed to copy raw channel data: ${e.localizedMessage}")
        }
      }
    }

    mBinding.shareButton.setOnClickListener {
      model.rawData.value?.let {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.listallchannels_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, it)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.listallchannels_share_title)))
      }
    }

    mBinding.closeButton.setOnClickListener {
      dismiss()
    }
  }

  private fun shareChannelsData(list: MutableList<RES_GETINFO>) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when serializing channels: ", exception)
      context?.run { Toast.makeText(this, R.string.listallchannels_serialization_error, Toast.LENGTH_SHORT).show() }
    }) {
      withContext(this.coroutineContext + Dispatchers.Default) {
        val data = list.joinToString("\n\n", "", "", -1) { `default$`.`MODULE$`.write(it, 1, `JsonSerializers$`.`MODULE$`.cmdResGetinfoReadWriter()) }
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.listallchannels_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, data)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.listallchannels_share_title)))
      }
    }
  }


  private fun getChannel() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving channel: ", exception)
      model.state.value = ChannelDetailsState.ERROR
    }) {
      model.state.value = ChannelDetailsState.IN_PROGRESS
      withContext(this.coroutineContext + Dispatchers.Default) {
        val channel = appKit.getChannel(ByteVector32.fromValidHex(args.channelId))
        val json = `default$`.`MODULE$`.write(channel, 1, `JsonSerializers$`.`MODULE$`.cmdResGetinfoReadWriter())
        model.rawData.postValue(json)
        model.state.postValue(ChannelDetailsState.DONE)
      }
    }
  }
}

enum class ChannelDetailsState {
  INIT, IN_PROGRESS, DONE, ERROR
}

class ChannelDetailsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(ChannelDetailsState::class.java)

  val state = MutableLiveData(ChannelDetailsState.INIT)
  val rawData = MutableLiveData("")
}
