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

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.background.KitState
import fr.acinq.phoenix.utils.Wallet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class BaseFragment(private val stayIfNotStarted: Boolean = false) : Fragment() {

  open val log: Logger = LoggerFactory.getLogger(BaseFragment::class.java)
  protected lateinit var app: AppViewModel

  fun appContext(): AppContext? = context?.run { AppContext.getInstance(this) }
  fun appContext(context: Context): AppContext = AppContext.getInstance(context)

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      app = ViewModelProvider(it).get(AppViewModel::class.java)
      app.state.observe(viewLifecycleOwner, { state ->
        handleAppState(state)
      })
    } ?: run {
      log.error("missing activity from fragment!")
    }
  }

  /**
   * Checks up the app state (wallet init, app kit is started) and navigate to appropriate page if needed.
   *
   * Does nothing if the fragment has set [stayIfNotStarted] to true.
   */
  open fun handleAppState(state: KitState) {
    if (!stayIfNotStarted && state !is KitState.Started) {
      context?.let {
        if (!Wallet.hasWalletBeenSetup(it)) {
          log.info("wallet has not been initialized, moving to init")
          findNavController().navigate(R.id.global_action_any_to_init_wallet)
        } else {
          log.info("kit is not ready, moving to startup")
          findNavController().navigate(R.id.global_action_any_to_startup)
        }
      }
    }
  }
}
