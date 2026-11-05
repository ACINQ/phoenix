/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.startup

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeystoreHelper
import fr.acinq.phoenix.android.security.PinManager
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nodeIdHash
import fr.acinq.phoenix.utils.extensions.phoenixName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class StartupRecoveryState {
    data object Init: StartupRecoveryState()
    data object CheckingSeed: StartupRecoveryState()
    data object ConfirmReset: StartupRecoveryState()
    data object Success: StartupRecoveryState()
    sealed class Error: StartupRecoveryState() {
        data class Other(val cause: Throwable): Error()
        data object SeedDoesNotMatch: Error()
        data class KeyStoreFailure(val cause: Throwable): Error()
    }
}

class StartupRecoveryViewModel(
    val application: PhoenixApplication,
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<StartupRecoveryState>(StartupRecoveryState.Init)

    /**
     * Checks that the provided mnemonics match a channels database on this device, then recovers that wallet.
     *
     * If the seed file can actually be loaded, the restored wallet is added to it, and nothing else changes.
     *
     * Otherwise, the seed file is reset to contain only that recovered wallet, and other wallets are deleted. Since this
     * is a destructive operation, the user must confirm. If the user had setup PIN codes for the restored wallet, they
     * are removed.
     *
     * @param confirmReset true if the user confirms resetting the existing seed file.
     */
    fun recoverSeed(words: List<String>, confirmReset: Boolean = false, onRecoveryDone: (WalletId) -> Unit) {
        if (state.value is StartupRecoveryState.CheckingSeed) return
        state.value = StartupRecoveryState.CheckingSeed

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed fallback against existing data: ", e)
            state.value = StartupRecoveryState.Error.Other(e)
        }) {
            val seed = MnemonicCode.toSeed(mnemonics = words.joinToString(" "), passphrase = "").byteVector()
            val localKeyManager = LocalKeyManager(seed = seed, chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
            val nodeIdHash = localKeyManager.nodeIdHash()

            val channelsDbFile = application.applicationContext.getDatabasePath("channels-${NodeParamsManager.chain.phoenixName}-$nodeIdHash.sqlite")
            if (!channelsDbFile.exists()) {
                state.value = StartupRecoveryState.Error.SeedDoesNotMatch
                return@launch
            }

            val walletId = WalletId(nodeIdHash)
            // trying to read the seed file a few times just in case we could avoid a reset
            when (val result = loadSeedFileWithRetries()) {
                is DecryptSeedResult.Success -> {
                    addToSeedFile(result, walletId, words)
                    finishRecovery(walletId, onRecoveryDone)
                }
                is DecryptSeedResult.Failure -> {
                    log.info("seed file still cannot be loaded ($result)")
                    if (confirmReset) {
                        log.info("user confirmed reset to recover wallet=$walletId")
                        resetSeedFileForRecovery(walletId, words, onRecoveryDone)
                    } else {
                        log.info("prompting the user for confirmation")
                        state.value = StartupRecoveryState.ConfirmReset
                    }
                }
            }
        }
    }

    /** Loads the seed file, retrying a few times. The seed file must not be replaced because of a transient keystore failure. */
    private suspend fun loadSeedFileWithRetries(): DecryptSeedResult {
        var result = SeedManager.loadAndDecrypt(application.applicationContext)
        repeat(2) {
            if (result is DecryptSeedResult.Success) return result
            delay(1000) // should not be too long, as we are blocking the recovery operation
            result = SeedManager.loadAndDecrypt(application.applicationContext)
        }
        return result
    }

    private fun addToSeedFile(existing: DecryptSeedResult.Success, walletId: WalletId, words: List<String>) {
        if (existing.userWalletsMap.containsKey(walletId)) {
            log.info("seed file can be loaded and already contains wallet=$walletId, nothing to write")
        } else {
            log.info("seed file can be loaded, adding wallet=$walletId to it")
            val seedMap = existing.userWalletsMap.mapValues { it.value.words } + (walletId to words)
            SeedManager.writeSeedToDisk(application.applicationContext, EncryptedSeed.V2.encrypt(seedMap), overwrite = true)
        }
    }

    /**
     * Deletes the current encryption key and replaces the seed file with one that contains only the recovered wallet.
     * Pin access settings for this wallet are reset to default. Other wallets and their preferences are deleted.
     */
    private suspend fun resetSeedFileForRecovery(walletId: WalletId, words: List<String>, onRecoveryDone: (WalletId) -> Unit) {
        // First delete the key. Writing the seed to file should be done last, once everything is clean, in case a crash happen in between
        KeystoreHelper.deleteKey(KeystoreHelper.KEY_NO_AUTH)

        log.info("resetting pin settings for wallet=$walletId")
        val context = application.applicationContext
        DataStoreManager.loadUserPrefsForWallet(context, walletId).run {
            saveIsScreenLockPinEnabled(false)
            saveLockPinCodeSuccess()
            saveIsSpendLockPinEnabled(false)
            saveSpendingPinCodeSuccess()
        }
        log.info("deleting prefs for other known wallets")
        val otherWallets = application.globalPrefs.getAvailableWalletsMeta.first().keys - walletId
        otherWallets.forEach { DataStoreManager.deleteWalletPrefs(context, it) }
        log.info("deleting all pin files")
        PinManager.deletePinFiles(context)

        log.info("replacing seed file with wallet=$walletId using a new encryption key")
        val encrypted = EncryptedSeed.V2.encrypt(mapOf(walletId to words))
        SeedManager.writeSeedToDisk(context, encrypted, overwrite = true)
        log.info("finished resetting seed and prefs following recovery of wallet=$walletId")
        finishRecovery(walletId, onRecoveryDone)
    }

    private suspend fun finishRecovery(walletId: WalletId, onRecoveryDone: (WalletId) -> Unit) {
        state.value = StartupRecoveryState.Success
        delay(1500)
        viewModelScope.launch(Dispatchers.Main) {
            onRecoveryDone(walletId)
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StartupRecoveryViewModel(application) as T
        }
    }
}