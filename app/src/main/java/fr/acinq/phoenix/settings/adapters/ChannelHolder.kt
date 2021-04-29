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

package fr.acinq.phoenix.settings.adapters

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.channel.DATA_PHOENIX_WAIT_REMOTE_CHANNEL_REESTABLISH
import fr.acinq.eclair.channel.HasCommitments
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.phoenix.R
import fr.acinq.phoenix.settings.ListChannelsFragmentDirections
import fr.acinq.phoenix.utils.Transcriber
import fr.acinq.phoenix.utils.customviews.CoinView
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ChannelHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  fun bindItem(channel: RES_GETINFO, position: Int) {

    val icon = itemView.findViewById<ImageView>(R.id.channel_icon)
    val state = itemView.findViewById<TextView>(R.id.channel_state)
    val channelBalanceLayout = itemView.findViewById<View>(R.id.channel_balance_layout)
    val balanceValue = itemView.findViewById<CoinView>(R.id.channel_balance_value)
    val capacityValue = itemView.findViewById<CoinView>(R.id.channel_capacity_value)

    state.text = Transcriber.readableState(itemView.context, channel.state())
    icon.imageTintList = ColorStateList.valueOf(Transcriber.colorForState(itemView.context, channel.state()))

    when (val data = channel.data()) {
      is HasCommitments -> {
        balanceValue.setAmount(data.commitments().availableBalanceForSend())
        capacityValue.setAmount(data.commitments().localCommit().spec().totalFunds())
        channelBalanceLayout.visibility = View.VISIBLE
      }
      is DATA_PHOENIX_WAIT_REMOTE_CHANNEL_REESTABLISH -> {
        data.data().commitments().apply {
          balanceValue.setAmount(data.commitments().availableBalanceForSend())
          capacityValue.setAmount(data.commitments().localCommit().spec().totalFunds())
        }
        channelBalanceLayout.visibility = View.VISIBLE
      }
      else -> {
        channelBalanceLayout.visibility = View.GONE
      }
    }

    itemView.setOnClickListener {
      val action = ListChannelsFragmentDirections.actionListChannelsToChannelDetails(channel.channelId().toString())
      itemView.findNavController().navigate(action)
    }
  }
}
