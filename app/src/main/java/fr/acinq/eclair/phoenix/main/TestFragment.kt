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

package fr.acinq.eclair.phoenix.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.TestFragmentBinding
import androidx.navigation.fragment.findNavController

class TestFragment : Fragment() {

  companion object {
    fun newInstance() = TestFragment()
  }

  private lateinit var viewModel: MainViewModel
  private lateinit var mBinding: TestFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = TestFragmentBinding.inflate(inflater, container, false)
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
  }

  override fun onStart() {
    super.onStart()
    mBinding.backButton.setOnClickListener { findNavController().navigate(R.id.action_testFragment_to_mainFragment, null) }
  }

}
