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

package fr.acinq.phoenix.startup

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.StartupState
import fr.acinq.phoenix.databinding.FragmentStartupBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.send.ReadInputFragmentDirections
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.KeystoreHelper
import fr.acinq.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StartupFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentStartupBinding
  private var mPinDialog: PinDialog? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentStartupBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    mBinding.appKitModel = appKit
    appKit.startupState.observe(viewLifecycleOwner, Observer {
      log.debug("startup in state $it")
      if (it == StartupState.ERROR) {
        Handler().postDelayed({ appKit.startupState.value = StartupState.OFF }, 3200)
      }
      if (it == StartupState.OFF) {
        startNodeIfNeeded()
      }
    })
  }

  override fun onStart() {
    super.onStart()
    if (mPinDialog == null) {
      mPinDialog = getPinDialog(object : PinDialog.PinDialogCallback {
        override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
          if (context != null) {
            appKit.startAppKit(context!!, pinCode)
          }
          dialog.dismiss()
        }

        override fun onPinCancel(dialog: PinDialog) {}
      }, cancelable = false)
    }
  }

  override fun onResume() {
    super.onResume()
    startNodeIfNeeded()
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  override fun appCheckup() {
    if (appKit.isKitReady()) {
      log.debug("kit is ready, redirecting to main page")
      if (appKit.currentURIIntent.value != null) {
        findNavController().navigate(ReadInputFragmentDirections.globalActionAnyToReadInput(appKit.currentURIIntent.value!!))
        appKit.currentURIIntent.value = null
      } else {
        findNavController().navigate(R.id.action_startup_to_main)
      }
    } else if (context != null && !appKit.hasWalletBeenSetup(context!!)) {
      log.debug("kit is not ready and wallet is not setup, redirecting to init wallet")
      findNavController().navigate(R.id.global_action_any_to_init_wallet)
    } else {
      log.debug("kit is not ready, let's start it if needed!")
      startNodeIfNeeded()
    }
  }

  private fun startNodeIfNeeded() {
    context?.let { ctx ->
      if (appKit.startupState.value == StartupState.OFF && !appKit.isKitReady() && appKit.hasWalletBeenSetup(ctx)) {
        when {
          Prefs.useBiometrics(ctx) && BiometricManager.from(ctx).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS ->
            getBiometricAuth(negativeCallback = {
              mPinDialog?.reset()
              mPinDialog?.show()
            }, successCallback = {
              try {
                val pin = KeystoreHelper.decryptPin(ctx)?.toString(Charsets.UTF_8)
                appKit.startAppKit(ctx, pin!!)
              } catch (e: Exception) {
                log.error("could not decrypt pin: ", e)
                appKit.startupErrorMessage.value = getString(R.string.startup_error_auth)
                appKit.startupState.value = StartupState.ERROR
              }
            })
          Prefs.getIsSeedEncrypted(ctx) -> {
            mPinDialog?.reset()
            mPinDialog?.show()
          }
          else -> appKit.startAppKit(ctx, Constants.DEFAULT_PIN)
        }
      } else {
        log.info("kit on standby [ state=${appKit.startupState.value}, kit=${appKit.kit.value}, init=${appKit.hasWalletBeenSetup(ctx)} ]")
      }
    } ?: log.warn("cannot start node with null context")
  }
}
