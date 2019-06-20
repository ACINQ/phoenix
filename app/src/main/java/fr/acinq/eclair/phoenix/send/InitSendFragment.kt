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

package fr.acinq.eclair.phoenix.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseDialogFragment
import fr.acinq.eclair.phoenix.databinding.FragmentSendInitBinding
import fr.acinq.eclair.phoenix.utils.Clipboard

class InitSendFragment : BaseDialogFragment() {

  private lateinit var mBinding: FragmentSendInitBinding

  private lateinit var model: InitSendViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSendInitBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(InitSendViewModel::class.java)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.pasteButton.setOnClickListener {
      context?.let {
        val pr = Clipboard.read(context!!)
        try {
          PaymentRequest.read(pr)
          val action = InitSendFragmentDirections.actionInitSendToSend(Clipboard.read(context!!))
          findNavController().navigate(action)
//          dismiss()
        } catch (e: Exception) {
          log.error("invalid payment request: ", e)
          Toast.makeText(context, "payment request not valid: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }
}
