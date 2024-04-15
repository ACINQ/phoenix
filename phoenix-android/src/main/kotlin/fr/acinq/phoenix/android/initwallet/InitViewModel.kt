/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.initwallet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class WritingSeedState {
    data object Init : WritingSeedState()
    data class Writing(val mnemonics: List<String>) : WritingSeedState()
    data class WrittenToDisk(val encryptedSeed: EncryptedSeed) : WritingSeedState()
    data class Error(val e: Throwable) : WritingSeedState()
}

abstract class InitWalletViewModel: ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)

    /** State to monitor the writing of a seed to the disk, used by the restore view and the create view thru [writeSeed]. */
    var writingState by mutableStateOf<WritingSeedState>(WritingSeedState.Init)
        private set

    /**
     * Attempts to write a seed on disk and updates the view model state. If a seed already
     * exists on disk, this method will not fail but it will not overwrite the existing file.
     *
     * @param isNewWallet when false, we will need to start the legacy app because this seed
     *          may be attached to a legacy wallet.
     */
    fun writeSeed(
        context: Context,
        mnemonics: List<String>,
        isNewWallet: Boolean,
        onSeedWritten: () -> Unit
    ) {
        if (writingState !is WritingSeedState.Init) return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to write seed to disk: ", e)
            writingState = WritingSeedState.Error(e)
        }) {
            writingState = WritingSeedState.Writing(mnemonics)
            log.debug("writing mnemonics to disk...")
            MnemonicCode.validate(mnemonics, MnemonicLanguage.English.wordlist())
            val existing = SeedManager.loadSeedFromDisk(context)
            if (existing == null) {
                val encrypted = EncryptedSeed.V2.NoAuth.encrypt(EncryptedSeed.fromMnemonics(mnemonics))
                SeedManager.writeSeedToDisk(context, encrypted)
                writingState = WritingSeedState.WrittenToDisk(encrypted)
                LegacyPrefsDatastore.saveStartLegacyApp(context, if (isNewWallet) LegacyAppStatus.NotRequired else LegacyAppStatus.Unknown)
                if (isNewWallet) {
                    log.info("new seed successfully created and written to disk")
                } else {
                    log.info("wallet successfully restored from mnemonics and written to disk")
                }
            } else {
                log.warn("cannot overwrite existing seed=${existing.name()}")
                writingState = WritingSeedState.WrittenToDisk(existing)
            }
            viewModelScope.launch(Dispatchers.Main) {
                delay(1000)
                onSeedWritten()
            }
        }
    }
}


//    /** State of the restore wallet view */
//    var restoreWalletState by mutableStateOf<RestoreWalletViewState>(RestoreWalletViewState.Disclaimer)
//
//    var checkBackupState by mutableStateOf<CheckBackupState>(CheckBackupState.Init)
//







//    fun attemptBackupRestore(context: Context, seed: ByteArray, onRestoreDone: () -> Unit) {
//        if (checkBackupState is CheckBackupState.Checking) return
//        log.info("checking for backup files")
//
////        fun onRestoreDoneMain() {
////            viewModelScope.launch(Dispatchers.Main) {
////                onRestoreDone()
////            }
////        }
//
//        checkBackupState = CheckBackupState.Checking.LookingForFile
//        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
//            log.error("error when checking backup: ", e)
//            checkBackupState = CheckBackupState.Done.Failed.Error(e)
//            // onRestoreDoneMain()
//        }) {
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//                log.info("unsupported android version=${Build.VERSION.SDK_INT}")
//                checkBackupState = CheckBackupState.Done.Failed.UnsupportedAndroidVersion
//                //onRestoreDoneMain()
//                return@launch
//            }
//            val keyManager = LocalKeyManager(seed = seed.byteVector(), chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
//            when (val encryptedBackup = LocalBackupHelper.getBackupData(context, keyManager)) {
//                null -> {
//                    log.info("no backup found")
//                    checkBackupState = CheckBackupState.Done.NoBackupFound
//                        //onRestoreDoneMain()
//                    return@launch
//                }
//                else -> {
//                    log.info("found backup, decrypting file...")
//                    checkBackupState = CheckBackupState.Checking.Decrypting
//                    val data = try {
//                        encryptedBackup.decrypt(keyManager)
//                    } catch (e: Exception) {
//                        log.error("cannot decrypt backup file: ", e)
//                        checkBackupState = CheckBackupState.Done.Failed.CannotDecrypt(encryptedBackup)
//                        //onRestoreDoneMain()
//                        return@launch
//                    }
//
//                    log.info("unzipping backup...")
//                    val files = LocalBackupHelper.unzipData(data)
//                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey
//                    val channelsDbEntry = files.filterKeys { it == DatabaseManager.channelsDbName(NodeParamsManager.chain, nodeId) }.entries.firstOrNull()
//                    val paymentsDbEntry = files.filterKeys { it == DatabaseManager.paymentsDbName(NodeParamsManager.chain, nodeId) }.entries.firstOrNull()
//
//                    if (channelsDbEntry == null || paymentsDbEntry == null) {
//                        log.error("missing channels or payments database file in zip backup")
//                        checkBackupState = CheckBackupState.Done.Failed.InvalidName
//                        //onRestoreDoneMain()
//                        return@launch
//                    } else {
//                        log.info("restoring channels and payments database files")
//                        checkBackupState = try {
//                            // LocalBackupHelper.restoreFilesToPrivateDir(context, channelsDbEntry.key, channelsDbEntry.value, paymentsDbEntry.key, paymentsDbEntry.value)
//                            log.info("backup files have been restored")
//                            CheckBackupState.Done.BackupRestored
//                        } catch (e: Exception) {
//                            log.error("channels or payments files already exist in database directory")
//                            CheckBackupState.Done.Failed.AlreadyExist
//                        }
//                        // onRestoreDoneMain()
//                    }
//                }
//            }
//        }
//    }
//
//    class Factory(
//        private val controllerFactory: ControllerFactory,
//        private val getController: ControllerFactory.() -> InitializationController
//    ) : ViewModelProvider.Factory {
//        override fun <T : ViewModel> create(modelClass: Class<T>): T {
//            @Suppress("UNCHECKED_CAST")
//            return InitViewModel(controllerFactory.getController()) as T
//        }
//    }
//}