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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.phoenix.AppKitModel
import fr.acinq.eclair.phoenix.databinding.FragmentReceiveWithOpenBinding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class ReceiveWithOpenDialogFragment : DialogFragment() {

  val log: Logger = LoggerFactory.getLogger(ReceiveWithOpenDialogFragment::class.java)
  private val args: ReceiveWithOpenDialogFragmentArgs by navArgs()

  lateinit var mBinding: FragmentReceiveWithOpenBinding
  private lateinit var appKit: AppKitModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentReceiveWithOpenBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    appKit = activity?.run {
      ViewModelProviders.of(activity!!).get(AppKitModel::class.java)
    } ?: throw Exception("Invalid Activity")

    requireActivity().onBackPressedDispatcher.addCallback(this) {
      log.info("back pressed should be disabled here")
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mBinding.fundingValue.setAmount(Satoshi(args.fundingSat))
    mBinding.feeValue.setAmount(Satoshi(args.feeSat))
  }

  override fun onStart() {
    super.onStart()
    mBinding.acceptButton.setOnClickListener {
      log.info("accepting pay-to-open with fee=${args.feeSat}")
      appKit.acceptPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
      dismiss()
    }
    mBinding.declineButton.setOnClickListener {
      log.info("pay to open with payment_hash=${args.paymentHash} has been declined")
      appKit.rejectPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
      dismiss()
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
  }
}
