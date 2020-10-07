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
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
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

class AccessControlFragment : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

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
    model.errorMessage.observe(viewLifecycleOwner, Observer {
      if (!it.isNullOrBlank()) {
        context?.let { ctx -> Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    context?.let { model.updateLockState(it) }
    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
    mBinding.softAuthUnavailable.setOnClickListener { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }

    mBinding.screenLockSwitch.setOnClickListener {
      if (model.isUpdatingState.value != true) {
        model.isUpdatingState.value = true
        AuthHelper.promptSoftAuth(this,
          onSuccess = {
            if (mBinding.screenLockSwitch.isChecked()) {
              context?.let { model.disableScreenLock(it) }
            } else {
              context?.let { model.enableScreenLock(it) }
            }
          }, onFailure = { code ->
            context?.let { model.errorMessage.postValue(AuthHelper.translateAuthState(it, code) ?: it.getString(R.string.accessctrl_error_auth)) }
            model.isUpdatingState.value = false
          }, onCancel = {
            model.isUpdatingState.value = false
          })
      }
    }

    mBinding.fullLockSwitch.setOnClickListener {
      context?.let { ctx ->
        if (model.isUpdatingState.value != true) {
          val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(ctx))
          if (mBinding.fullLockSwitch.isChecked() && encryptedSeed is EncryptedSeed.V2.WithAuth) {
            disableFullLock(ctx, encryptedSeed)
          } else if (!mBinding.fullLockSwitch.isChecked() && encryptedSeed is EncryptedSeed.V2.NoAuth) {
            enableFullLock(ctx, encryptedSeed)
          } else {
            log.info("invalid combination: ${encryptedSeed?.name()} with switch=${mBinding.fullLockSwitch.isChecked()}")
            model.updateLockState(ctx)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    model.state.value?.let { refreshUI(it) }
    context?.let {
      model.canUseSoftAuth.value = AuthHelper.canUseSoftAuth(it)
      model.canUseHardAuth.value = AuthHelper.canUseHardAuth(it)
    }
  }

  override fun onStop() {
    super.onStop()
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    if (key == Prefs.PREFS_MNEMONICS_SEEN_TIMESTAMP) {
      model.state.value?.let { refreshUI(it) }
    }
  }

  private fun enableFullLock(ctx: Context, encryptedSeed: EncryptedSeed.V2.NoAuth) {
    val cipher = try {
      KeystoreHelper.getEncryptionCipher(KeystoreHelper.KEY_WITH_AUTH)
    } catch (e: Exception) {
      log.error("could not get cipher: ", e)
      model.errorMessage.postValue(getString(R.string.accessctrl_error_cipher, e.localizedMessage ?: e.javaClass.simpleName))
      return
    }
    val onConfirm = {
      model.isUpdatingState.value = true
      AuthHelper.promptHardAuth(this,
        cipher = cipher,
        onSuccess = { crypto ->
          lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to encrypt ${encryptedSeed.name()}: ", e)
            model.errorMessage.postValue(getString(R.string.accessctrl_error_auth))
            model.updateLockState(ctx)
          }) {
            encryptedSeed.decrypt().run {
              EncryptedSeed.V2.WithAuth.encrypt(this, crypto!!.cipher!!)
            }.run {
              SeedManager.writeSeedToDisk(Wallet.getDatadir(ctx), this)
              Prefs.getFCMToken(ctx)?.let { app.service?.refreshFCMToken(it) }
              model.updateLockState(ctx)
            }
          }
        }, onFailure = { code ->
          model.errorMessage.postValue(AuthHelper.translateAuthState(ctx, code) ?: ctx.getString(R.string.accessctrl_error_auth))
          model.isUpdatingState.value = false
        }, onCancel = {
          model.isUpdatingState.value = false
        })
    }
    AlertHelper.build(layoutInflater, title = getString(R.string.accessctrl_full_lock_confirm_title), message = getString(R.string.accessctrl_full_lock_confirm_message)).apply {
      setPositiveButton(R.string.btn_confirm) { _, _ -> onConfirm() }
      setNegativeButton(R.string.btn_cancel, null)
      show()
    }
  }

  private fun disableFullLock(context: Context, encryptedSeed: EncryptedSeed.V2.WithAuth) {
    val cipher = try {
      encryptedSeed.getDecryptionCipher()
    } catch (e: Exception) {
      log.error("could not get cipher: ", e)
      model.errorMessage.postValue(getString(R.string.accessctrl_error_cipher, e.localizedMessage ?: e.javaClass.simpleName))
      return
    }
    model.isUpdatingState.value = true
    AuthHelper.promptHardAuth(this,
      cipher = cipher,
      onSuccess = { crypto ->
        lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
          log.error("failed to decrypt ${encryptedSeed.name()}: ", e)
          model.errorMessage.postValue(getString(R.string.accessctrl_error_auth))
          model.updateLockState(context)
        }) {
          encryptedSeed.decrypt(crypto?.cipher).run {
            EncryptedSeed.V2.NoAuth.encrypt(this)
          }.run {
            SeedManager.writeSeedToDisk(Wallet.getDatadir(context), this)
            Prefs.getFCMToken(context)?.let { app.service?.refreshFCMToken(it) }
            model.updateLockState(context)
          }
        }
      }, onFailure = { code ->
        model.errorMessage.postValue(AuthHelper.translateAuthState(context, code) ?: context.getString(R.string.accessctrl_error_auth))
        model.isUpdatingState.value = false
      }, onCancel = {
        model.isUpdatingState.value = false
      })
  }

  private fun refreshUI(state: AccessLockState) {
    log.debug("refresh ui with state=$state")
    context?.let { model.isBackupDone.value = Prefs.isBackupDone(it) }
    mBinding.screenLockSwitch.setChecked(state is AccessLockState.Done.ScreenLock || state is AccessLockState.Done.FullLock)
    mBinding.fullLockSwitch.setChecked(state is AccessLockState.Done.FullLock)
  }

}

sealed class AccessLockState {
  object Init : AccessLockState()
  sealed class Done : AccessLockState() {
    object None : Done()
    object ScreenLock : Done()
    object FullLock : Done()
  }
}

class AccessControlViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)

  val state = MutableLiveData<AccessLockState>(AccessLockState.Init)

  val isBackupDone = MutableLiveData(false)
  val canUseSoftAuth = MutableLiveData(false)
  val canUseHardAuth = MutableLiveData(false)
  val isUpdatingState = MutableLiveData(false)
  val errorMessage = MutableLiveData("")

  fun updateLockState(context: Context) {
    val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))
    state.postValue(when (encryptedSeed) {
      is EncryptedSeed.V2.NoAuth -> if (Prefs.isScreenLocked(context)) AccessLockState.Done.ScreenLock else AccessLockState.Done.None
      is EncryptedSeed.V2.WithAuth -> AccessLockState.Done.FullLock
      else -> AccessLockState.Done.None
    })
  }

  @UiThread
  fun enableScreenLock(context: Context) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("failed to enable screen lock: ", e)
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
      log.error("failed to disable screen lock: ", e)
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
