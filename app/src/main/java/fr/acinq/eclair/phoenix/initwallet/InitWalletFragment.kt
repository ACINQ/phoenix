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

package fr.acinq.eclair.phoenix.initwallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentInitWalletBinding


class InitWalletFragment : InitWalletBaseFragment() {

  private lateinit var mBinding: FragmentInitWalletBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletBinding.inflate(inflater, container, false)
    return mBinding.root
  }

  override fun onStart() {
    super.onStart()
    seedModel.reset()
    mBinding.createSeed.setOnClickListener { findNavController().navigate(R.id.action_init_wallet_to_auto_create) }
    mBinding.restoreSeed.setOnClickListener { findNavController().navigate(R.id.action_init_wallet_to_restore) }
  }
}
