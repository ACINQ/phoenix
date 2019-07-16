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
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.`JsonSerializers$`
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsListChannelsBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import upickle.`default$`


class ListChannelsFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSettingsListChannelsBinding

  private lateinit var model: ListChannelsViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsListChannelsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    mBinding.channelsResultSerialized.setHorizontallyScrolling(true)
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(ListChannelsViewModel::class.java)
    model.channels.observe(viewLifecycleOwner, Observer {
      when {
        it.count() > 0 -> mBinding.channelsFoundHeader.text = getString(R.string.listallchannels_channels_header, it.count())
        else -> mBinding.channelsFoundHeader.text = getString(R.string.listallchannels_no_channels)
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    mBinding.copyButton.setOnClickListener {
      try {
        val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("Channels data", model.serializedChannels.value)
        Toast.makeText(activity!!.applicationContext, getString(R.string.utils_copied), Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        log.error("failed to copy raw channel data: ${e.localizedMessage}")
      }
    }
    mBinding.shareButton.setOnClickListener {
      model.serializedChannels.value?.let {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.listallchannels_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, it)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.listallchannels_share_title)))
      }
    }
  }

  private fun getChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving list of channels: ", exception)
      model.state.value = ListChannelsState.ERROR
    }) {
      model.state.value = ListChannelsState.IN_PROGRESS
      val result = appKit.getChannels(null)
      model.serializedChannels.value = result.joinToString("\n\n", "", "", -1) { `default$`.`MODULE$`.write(it, 1, `JsonSerializers$`.`MODULE$`.cmdResGetinfoReadWriter()) }
      model.channels.value = result
      model.state.value = ListChannelsState.DONE
    }
  }
}

enum class ListChannelsState {
  IN_PROGRESS, DONE, ERROR
}

class ListChannelsViewModel : ViewModel() {
  val state = MutableLiveData(ListChannelsState.IN_PROGRESS)
  val channels = MutableLiveData<Iterable<RES_GETINFO>>(ArrayList())
  val serializedChannels = MutableLiveData("")
}
