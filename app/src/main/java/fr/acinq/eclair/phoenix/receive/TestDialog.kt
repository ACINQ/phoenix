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

package fr.acinq.eclair.phoenix.receive

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.phoenix.AppKitModel
import fr.acinq.eclair.phoenix.databinding.FragmentReceiveWithOpenBinding
import fr.acinq.eclair.phoenix.databinding.FragmentTestDialogBinding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestDialog : DialogFragment() {

  val log: Logger = LoggerFactory.getLogger(TestDialog::class.java)

  lateinit var mBinding: FragmentTestDialogBinding
  protected lateinit var appKit: AppKitModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentTestDialogBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    appKit = ViewModelProvider(this).get(AppKitModel::class.java)

    requireActivity().onBackPressedDispatcher.addCallback(this) {
      log.info("back pressed is disabled here")
    }
  }

  override fun onStart() {
    super.onStart()
    log.info("testdialog.onstart")
  }

  override fun onStop() {
    super.onStop()
    log.info("testdialog.onstop")
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    log.info("testdialog.dismiss")
  }
}
