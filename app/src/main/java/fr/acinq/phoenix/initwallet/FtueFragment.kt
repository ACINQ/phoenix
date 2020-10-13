/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.initwallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentFtueBackupBinding
import fr.acinq.phoenix.databinding.FragmentFtueBinding
import fr.acinq.phoenix.databinding.FragmentFtueLightningBinding
import fr.acinq.phoenix.databinding.FragmentFtueWelcomeBinding
import fr.acinq.phoenix.utils.BindingHelpers
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class FtueFragment : BaseFragment(stayIfNotStarted = true) {

  private lateinit var mBinding: FragmentFtueBinding
  private lateinit var adapter: FtueViewAdapter

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentFtueBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    adapter = FtueViewAdapter(this).apply {
      mBinding.viewPager.adapter = this
      mBinding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
          super.onPageSelected(position)
          BindingHelpers.show(mBinding.nextButton, position == 0 || position == 1)
          BindingHelpers.show(mBinding.skipButton, position == 0 || position == 1)
          mBinding.indicators.text = getString(when (position) {
            0 -> R.string.ftue__bullet_1
            1 -> R.string.ftue__bullet_2
            else -> R.string.ftue__bullet_3
          })
        }
      })
    }
    return mBinding.root
  }

  override fun onStart() {
    super.onStart()
    activity?.onBackPressedDispatcher?.addCallback(this) {
      if (mBinding.viewPager.currentItem == 0) {
        log.debug("back button disabled")
      } else {
        mBinding.viewPager.currentItem = mBinding.viewPager.currentItem - 1
      }
    }
    mBinding.nextButton.setOnClickListener {
      when (mBinding.viewPager.currentItem) {
        0 -> mBinding.viewPager.currentItem = 1
        1 -> mBinding.viewPager.currentItem = 2
        else -> leave()
      }
    }
    mBinding.skipButton.setOnClickListener { leave() }
  }

  fun leave() {
    context?.let {
      Prefs.setShowFTUE(it, false)
      findNavController().navigate(R.id.action_ftue_to_initwallet)
    }
  }
}

private class FtueViewAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
  override fun getItemCount() = 3

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> FtueWelcomeFragment()
      1 -> FtueLightningFragment()
      2 -> FtueBackupFragment()
      else -> FtueWelcomeFragment()
    }
  }
}

class FtueWelcomeFragment : Fragment() {
  private lateinit var mBinding: FragmentFtueWelcomeBinding
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentFtueWelcomeBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }
}

class FtueLightningFragment : Fragment() {
  private lateinit var mBinding: FragmentFtueLightningBinding
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentFtueLightningBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val swapInFee = 100 * ((parentFragment as FtueFragment).appContext()?.swapInSettings?.value?.feePercent ?: Constants.DEFAULT_SWAP_IN_SETTINGS.feePercent)
    mBinding.body.text = getString(R.string.ftue__pay_to_open__body, String.format("%.2f", swapInFee))
  }
}

class FtueBackupFragment : Fragment() {
  private lateinit var mBinding: FragmentFtueBackupBinding
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentFtueBackupBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mBinding.finishButton.setOnClickListener {
      (parentFragment as FtueFragment).leave()
    }
  }
}
