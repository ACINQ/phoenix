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
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.`JsonSerializers$`
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsListChannelsBinding
import fr.acinq.eclair.phoenix.settings.adapters.ChannelsAdapter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import upickle.`default$`


class ListChannelsFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsListChannelsBinding
  private lateinit var model: ListChannelsViewModel

  private lateinit var channelsAdapter: ChannelsAdapter
  private lateinit var channelsManager: RecyclerView.LayoutManager

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsListChannelsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this

    channelsAdapter = ChannelsAdapter(ArrayList())
    channelsManager = LinearLayoutManager(context)
    mBinding.channelsList.apply {
      setHasFixedSize(true)
      adapter = channelsAdapter
      layoutManager = channelsManager
    }

    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(ListChannelsViewModel::class.java)
    model.channels.observe(viewLifecycleOwner, Observer {
      val channelsCount = it.count()
      when {
        channelsCount > 0 -> mBinding.channelsFoundHeader.text = resources.getQuantityString(R.plurals.listallchannels_channels_header, channelsCount, channelsCount)
        else -> mBinding.channelsFoundHeader.text = getString(R.string.listallchannels_no_channels)
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    mBinding.shareButton.setOnClickListener {
      model.channels.value?.let { list ->
        shareChannelsData(list)
      }
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

  private fun getChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving list of channels: ", exception)
      model.state.value = ListChannelsState.ERROR
    }) {
      model.state.value = ListChannelsState.IN_PROGRESS
      val channels = appKit.getChannels(null).toMutableList()
      channelsAdapter.update(channels)
      model.channels.value = channels
      model.state.value = ListChannelsState.DONE
    }
  }
}

enum class ListChannelsState {
  IN_PROGRESS, DONE, ERROR
}

class ListChannelsViewModel : ViewModel() {
  val state = MutableLiveData(ListChannelsState.IN_PROGRESS)
  val channels = MutableLiveData<MutableList<RES_GETINFO>>(ArrayList())
}
