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

package fr.acinq.phoenix.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsAccessControlBinding
import fr.acinq.phoenix.utils.AlertHelper
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.crypto.AuthHelper
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.crypto.KeystoreHelper
import fr.acinq.phoenix.utils.crypto.SeedManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AccessControlFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsAccessControlBinding
  private lateinit var model: AccessControlViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsAccessControlBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(AccessControlViewModel::class.java)
    model.state.observe(viewLifecycleOwner, Observer {
      refreshUI(it)
      model.isUpdatingState.value = false
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    context?.let { model.updateLockState(it) }

    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.authUnavailable.setOnClickListener { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }

    mBinding.screenLockSwitch.setOnClickListener {
      if (model.isUIFrozen.value != true) {
        if (AuthHelper.isDeviceSecure(context)) {
          model.isUpdatingState.value = true
          AuthHelper.promptSoftAuth(this,
            onSuccess = {
              if (mBinding.screenLockSwitch.isChecked()) {
                context?.let { model.disableScreenLock(it) }
              } else {
                context?.let { model.enableScreenLock(it) }
              }
            }, onFailure = { code, _ ->
              showMessage(code?.let { getString(R.string.accessctrl_auth_error_with_code, it) } ?: getString(R.string.accessctrl_auth_error) )
            }, onCancel = {
              model.isUpdatingState.value = false
            })
        } else {
          showMessage(getString(R.string.accessctrl_auth_unavailable_short))
          context?.let { model.updateLockState(it) }
        }
      }
    }

    mBinding.fullLockSwitch.setOnClickListener {
      context?.let { ctx ->
        if (model.isUIFrozen.value != true && AuthHelper.isDeviceSecure(context)) {
          val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(ctx))

          // -- disable full lock: V2.WithAuth -> V2.NoAuth
          if (mBinding.fullLockSwitch.isChecked() && encryptedSeed is EncryptedSeed.V2.WithAuth) {
            model.isUpdatingState.value = true
            AuthHelper.promptHardAuth(this,
              cipher = encryptedSeed.getDecryptionCipher(),
              onSuccess = { crypto ->
                lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                  log.info("failed to decrypt ${encryptedSeed.javaClass.canonicalName}: ", e)
                  showMessage(getString(R.string.accessctrl_error_generic))
                  model.updateLockState(ctx)
                }) {
                  encryptedSeed.decrypt(crypto?.cipher).run {
                    EncryptedSeed.V2.NoAuth.encrypt(this)
                  }.run {
                    SeedManager.writeSeedToDisk(Wallet.getDatadir(ctx), this)
                    model.updateLockState(ctx)
                  }
                }
              }, onFailure = { code, _ ->
                showMessage(code?.let { getString(R.string.accessctrl_auth_error_with_code, it) } ?: getString(R.string.accessctrl_auth_error) )
              }, onCancel = {
                model.isUpdatingState.value = false
              })

          // -- enable full lock: V2.NoAuth -> V2.WithAuth
          } else if (!mBinding.fullLockSwitch.isChecked() && encryptedSeed is EncryptedSeed.V2.NoAuth) {
            val onConfirm = {
              model.isUpdatingState.value = true
              AuthHelper.promptHardAuth(this,
                cipher = KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_WITH_AUTH),
                onSuccess = { crypto ->
                  lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                    log.info("failed to decrypt ${encryptedSeed.javaClass.canonicalName}: ", e)
                    showMessage(getString(R.string.accessctrl_error_generic))
                    model.updateLockState(ctx)
                  }) {
                    encryptedSeed.decrypt().run {
                      EncryptedSeed.V2.WithAuth.encrypt(this, crypto!!.cipher!!)
                    }.run {
                      SeedManager.writeSeedToDisk(Wallet.getDatadir(ctx), this)
                      model.updateLockState(ctx)
                    }
                  }
                }, onFailure = { code, _ ->
                  showMessage(code?.let { getString(R.string.accessctrl_auth_error_with_code, it) } ?: getString(R.string.accessctrl_auth_error))
                }, onCancel = {
                  model.isUpdatingState.value = false
                })
            }
            AlertHelper.build(layoutInflater,
              title = getString(R.string.accessctrl_full_lock_confirm_title),
              message = getString(R.string.accessctrl_full_lock_confirm_message)).apply {
              setPositiveButton(R.string.btn_confirm) { _, _ -> onConfirm() }
              setNegativeButton(R.string.btn_cancel, null)
              show()
            }
            Unit
          } else {
            log.info("invalid combination: ${encryptedSeed?.javaClass?.canonicalName} with switch=${mBinding.fullLockSwitch.isChecked()}")
            model.updateLockState(ctx)
          }
        } else if (!AuthHelper.isDeviceSecure(context)) {
          showMessage(getString(R.string.accessctrl_auth_unavailable_short))
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    model.state.value?.let { refreshUI(it) }
  }

  private fun refreshUI(state: AccessLockState) {
    log.info("refresh ui with state=$state")
    mBinding.screenLockSwitch.setChecked(state is AccessLockState.Done.ScreenLock || state is AccessLockState.Done.FullLock)
    mBinding.fullLockSwitch.setChecked(state is AccessLockState.Done.FullLock)
  }

  private fun showMessage(message: String) {
    context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
  }
}

sealed class AccessLockState {
  object Init : AccessLockState()
  sealed class Done : AccessLockState() {
    object None : Done()
    object ScreenLock : Done()
    object FullLock : Done()
  }

  object Unavailable : AccessLockState()
}

class AccessControlViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)

  val state = MutableLiveData<AccessLockState>(AccessLockState.Init)
  val isUpdatingState = MutableLiveData(false)
  val isUIFrozen: MediatorLiveData<Boolean> = MediatorLiveData()

  init {
    isUIFrozen.addSource(state) { updateUIFrozen() }
    isUIFrozen.addSource(isUpdatingState) { updateUIFrozen() }
  }

  private fun updateUIFrozen() {
    isUIFrozen.value = state.value is AccessLockState.Unavailable || (isUpdatingState.value ?: false)
  }

  fun updateLockState(context: Context) {
    if (AuthHelper.isDeviceSecure(context)) {
      val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))
      state.postValue(when (encryptedSeed) {
        is EncryptedSeed.V2.NoAuth -> if (Prefs.isScreenLocked(context)) AccessLockState.Done.ScreenLock else AccessLockState.Done.None
        is EncryptedSeed.V2.WithAuth -> AccessLockState.Done.FullLock
        else -> AccessLockState.Init
      })
    } else {
      state.postValue(AccessLockState.Unavailable)
    }
  }

  @UiThread
  fun enableScreenLock(context: Context) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.info("failed to enable Screen Lock: ", e)
      updateLockState(context)
    }) {
      val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))
      if (encryptedSeed is EncryptedSeed.V2.NoAuth) {
        encryptedSeed.decrypt()
        Prefs.saveScreenLocked(context, true)
        log.info("enabled screen lock")
      }
      updateLockState(context)
    }
  }

  @UiThread
  fun disableScreenLock(context: Context) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.info("failed to disable Screen Lock: ", e)
      updateLockState(context)
    }) {
      val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))
      if (encryptedSeed is EncryptedSeed.V2.NoAuth) {
        encryptedSeed.decrypt()
        Prefs.saveScreenLocked(context, false)
        log.info("disabled screen lock")
      }
      updateLockState(context)
    }
  }
}
