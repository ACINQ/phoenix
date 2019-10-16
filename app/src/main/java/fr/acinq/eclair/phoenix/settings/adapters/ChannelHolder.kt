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

package fr.acinq.eclair.phoenix.settings.adapters

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.channel.HasCommitments
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.settings.ListChannelsFragmentDirections
import fr.acinq.eclair.phoenix.utils.Transcriber
import fr.acinq.eclair.phoenix.utils.customviews.CoinView
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ChannelHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  fun bindItem(channel: RES_GETINFO, position: Int) {

    val icon = itemView.findViewById<ImageView>(R.id.channel_icon)
    val state = itemView.findViewById<TextView>(R.id.channel_state)
    val balance = itemView.findViewById<CoinView>(R.id.channel_balance_value)
    val capacity = itemView.findViewById<CoinView>(R.id.channel_capacity_value)

    state.text = Transcriber.readableState(itemView.context, channel.state())
    icon.imageTintList = ColorStateList.valueOf(Transcriber.colorForState(itemView.context, channel.state()))

    val data = channel.data()
    if (data is HasCommitments) {
      balance.setAmount(data.commitments().availableBalanceForSend())
      capacity.setAmount(data.commitments().localCommit().spec().totalFunds())
      itemView.setOnClickListener {
        try {
          val action = ListChannelsFragmentDirections.actionListChannelsToChannelDetails(data.channelId().toString())
          itemView.findNavController().navigate(action)
        } catch (e: Exception) {
          log.error("could not serialize channel: ", e)
          Toast.makeText(itemView.context, itemView.context.getString(R.string.listallchannels_serialization_error), Toast.LENGTH_SHORT).show()
        }
      }
    }
  }
}
