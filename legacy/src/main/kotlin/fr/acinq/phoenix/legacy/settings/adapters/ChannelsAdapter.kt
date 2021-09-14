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

package fr.acinq.phoenix.legacy.settings.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.channel.RES_GETINFO
import fr.acinq.phoenix.legacy.R

class ChannelsAdapter(private var channels: MutableList<RES_GETINFO>) : RecyclerView.Adapter<ChannelHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_channel, parent, false)
    return ChannelHolder(view)
  }

  override fun onBindViewHolder(holder: ChannelHolder, position: Int) {
    val channel = this.channels.elementAt(position)
    holder.bindItem(channel, position)
  }

  override fun getItemCount(): Int {
    return this.channels.size
  }

  fun update(channels: MutableList<RES_GETINFO>) {
    if (this.channels != channels) {
      this.channels.clear()
      this.channels.addAll(channels)
    }
    notifyDataSetChanged()
  }

}
