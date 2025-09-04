/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.settings.reset

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.messaging.FirebaseMessaging
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.utils.extensions.phoenixName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class ResetWalletStep {
    data object Init : ResetWalletStep()
    data object Confirm : ResetWalletStep()
    sealed class Deleting : ResetWalletStep() {
        data object Init: Deleting()
        data object Databases: Deleting()
        data object Prefs: Deleting()
        data object Seed: Deleting()
    }
    sealed class Result : ResetWalletStep() {
        data object Success : Result()
        sealed class Failure : Result() {
            data class Error(val e: Throwable) : Failure()
            data object SeedFileAccess : Failure()
            data object WriteNewSeed : Failure()
        }
    }
}

class ResetWalletViewModel(val application: PhoenixApplication, val walletId: WalletId) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<ResetWalletStep>(ResetWalletStep.Init)

    fun deleteWalletData() {
        if (state.value != ResetWalletStep.Confirm) return
        state.value = ResetWalletStep.Deleting.Init

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to reset wallet data: ", e)
            state.value = ResetWalletStep.Result.Failure.Error(e)
        }) {
            log.info("resetting wallet with wallet=$walletId")
            delay(350)

            val context = application.applicationContext
            val userWallets = when (val seedFileResult = SeedManager.loadAndDecrypt(context)) {
                is DecryptSeedResult.Failure -> {
                    log.error("could not decrypt seed file: {}", seedFileResult)
                    state.value = ResetWalletStep.Result.Failure.SeedFileAccess
                    return@launch
                }
                is DecryptSeedResult.Success -> seedFileResult.userWalletsMap
            }

            delay(250)

            state.value = ResetWalletStep.Deleting.Databases
            val chain = NodeParamsManager.chain
            context.deleteDatabase("payments-${chain.phoenixName}-${walletId.nodeIdHash}.sqlite")
            context.deleteDatabase("channels-${chain.phoenixName}-${walletId.nodeIdHash}.sqlite")
            delay(500)

            state.value = ResetWalletStep.Deleting.Prefs
            DataStoreManager.deleteNodeUserPrefs(application.applicationContext, walletId)
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                if (task.isSuccessful) BusinessManager.refreshFcmToken()
            }

            delay(400)

            state.value = ResetWalletStep.Deleting.Seed
            val newSeedMap = (userWallets - walletId).map { it.key to it.value.words }.toMap()
            val newEncryptedSeed = EncryptedSeed.V2.encrypt(newSeedMap)
            try {
                SeedManager.writeSeedToDisk(context, newEncryptedSeed, overwrite = true)
            } catch (e: Exception) {
                log.error("could not write new seed file: ", e)
                state.value = ResetWalletStep.Result.Failure.WriteNewSeed
                return@launch
            }

            delay(300)
            log.info("successfully deleted wallet=$walletId")

            state.value = ResetWalletStep.Result.Success
        }
    }

    class Factory(val application: PhoenixApplication, val walletId: WalletId) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return ResetWalletViewModel(application, walletId = walletId) as T
        }
    }
}