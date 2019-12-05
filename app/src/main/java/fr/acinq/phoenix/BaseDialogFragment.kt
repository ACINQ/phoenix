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

package fr.acinq.phoenix

import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class BaseDialogFragment : DialogFragment() {

  val log: Logger = LoggerFactory.getLogger(BaseDialogFragment::class.java)
  protected lateinit var appKit: AppKitModel

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    if (activity == null) {
      dismiss()
    } else {
      appKit = ViewModelProvider(this).get(AppKitModel::class.java)
      appKit.kit.observe(activity!!, Observer {
        handleKit()
      })
      requireActivity().onBackPressedDispatcher.addCallback(this) {
        log.info("back pressed should be disabled here")
      }
    }
  }

  open fun handleKit() {
  }

}
