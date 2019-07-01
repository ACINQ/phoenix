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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import org.slf4j.LoggerFactory

enum class ClosingChannelsState {
  READY, IN_PROGRESS, DONE, ERROR
}

class CloseAllChannelsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(CloseAllChannelsViewModel::class.java)

  val state = MutableLiveData<ClosingChannelsState>(ClosingChannelsState.READY)

}
