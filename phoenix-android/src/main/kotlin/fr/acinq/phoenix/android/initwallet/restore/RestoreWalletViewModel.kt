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

package fr.acinq.phoenix.android.initwallet.restore

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.initwallet.InitWalletViewModel
import fr.acinq.phoenix.android.utils.backup.EncryptedBackup
import fr.acinq.phoenix.android.utils.backup.LocalBackupHelper
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class RestoreWalletState {
    data object Disclaimer : RestoreWalletState()
    data object InputSeed : RestoreWalletState()
    data class RestoreBackup(val words: List<String>) : RestoreWalletState()
}

sealed class RestoreBackupState {
    data object Init : RestoreBackupState()

    sealed class Checking : RestoreBackupState() {
        data object LookingForFile : Checking()
        data object Decrypting : Checking()
    }

    sealed class Done : RestoreBackupState() {
        data object BackupRestored : Done()

        sealed class Failure : Done() {
            data object UnresolvedContent : Failure()
            data class Error(val e: Throwable) : Failure()
            data class CannotDecrypt(val encryptedBackup: EncryptedBackup) : Failure()
            data object ContentDoesNotMatch : Failure()
            data object CannotWriteFiles : Failure()
        }
    }
}

class RestoreWalletViewModel: InitWalletViewModel() {

    var state by mutableStateOf<RestoreWalletState>(RestoreWalletState.Disclaimer)

    var restoreBackupState by mutableStateOf<RestoreBackupState>(RestoreBackupState.Init)

    var mnemonics by mutableStateOf(arrayOfNulls<String>(12))
        private set

    private val language = MnemonicLanguage.English

    fun filterWordsMatching(predicate: String): List<String> {
        return when {
            predicate.length > 1 -> language.matches(predicate)
            else -> emptyList()
        }
    }

    fun appendWordToMnemonic(word: String) {
        val index = mnemonics.indexOfFirst { it == null }
        if (index in 0..11) {
            mnemonics = mnemonics.copyOf().also { it[index] = word }
        }
    }

    fun removeWordsFromMnemonic(from: Int) {
        if (from in 0..11) {
            mnemonics = mnemonics.copyOf().also { it.fill(null, from) }
        }
    }

    fun checkSeedAndLocalFiles(context: Context, onSeedWritten: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed and db files: ${e.message}")
        }) {
            val words = mnemonics.filterNot { it.isNullOrBlank() }.filterNotNull()
            if (words.size != 12) throw RuntimeException("invalid mnemonics size=${words.size}")
            MnemonicCode.validate(words, MnemonicLanguage.English.wordlist())

            val seed = MnemonicCode.toSeed(words, "")
            val keyManager = LocalKeyManager(seed = seed.byteVector(), chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
            val nodeId = keyManager.nodeKeys.nodeKey.publicKey
            val paymentsDb = context.getDatabasePath(DatabaseManager.paymentsDbName(NodeParamsManager.chain, nodeId))

            if (paymentsDb.exists()) {
                log.info("found payments database, skipping backup step")
                writeSeed(context, words, isNewWallet = false, onSeedWritten = onSeedWritten)
            } else {
                log.info("no database found for this wallet, moving to backup restore step")
                state = RestoreWalletState.RestoreBackup(words)
            }
        }
    }

    fun restorePaymentsBackup(context: Context, words: List<String>, uri: Uri, onBackupRestoreDone: () -> Unit) {
        if (restoreBackupState is RestoreBackupState.Checking.Decrypting || restoreBackupState is RestoreBackupState.Done.BackupRestored) return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when restoring backup: ", e)
            restoreBackupState = RestoreBackupState.Done.Failure.Error(e)
        }) {

            MnemonicCode.validate(words, MnemonicLanguage.English.wordlist())
            val seed = MnemonicCode.toSeed(words, "")
            val keyManager = LocalKeyManager(seed = seed.byteVector(), chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)

            when (val encryptedBackup = LocalBackupHelper.resolveUriContent(context, uri)) {
                null -> {
                    delay(500)
                    log.info("content could not be resolved for uri=$uri")
                    restoreBackupState = RestoreBackupState.Done.Failure.UnresolvedContent
                    return@launch
                }
                else -> {
                    log.info("found backup, decrypting file...")
                    restoreBackupState = RestoreBackupState.Checking.Decrypting
                    delay(700)
                    val data = try {
                        encryptedBackup.decrypt(keyManager)
                    } catch (e: Exception) {
                        log.error("cannot decrypt backup file: ", e)
                        restoreBackupState = RestoreBackupState.Done.Failure.CannotDecrypt(encryptedBackup)
                        return@launch
                    }

                    log.info("unzipping backup file...")
                    val files = LocalBackupHelper.unzipData(data)
                    delay(300)
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey
                    val paymentsDbEntry = files.filterKeys { it == DatabaseManager.paymentsDbName(NodeParamsManager.chain, nodeId) }.entries.firstOrNull()

                    if (paymentsDbEntry == null) {
                        log.error("missing payments database file in zip backup")
                        restoreBackupState = RestoreBackupState.Done.Failure.ContentDoesNotMatch
                        return@launch
                    } else {
                        log.info("restoring payments database files")
                        try {
                            LocalBackupHelper.restoreDbFile(context, paymentsDbEntry.key, paymentsDbEntry.value)
                            log.info("payments db has been restored")
                            restoreBackupState = RestoreBackupState.Done.BackupRestored
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                LocalBackupHelper.cleanUpOldBackupFile(context, keyManager, encryptedBackup, uri)
                                log.debug("old backup file cleaned up")
                            }
                            delay(1000)
                            viewModelScope.launch(Dispatchers.Main) {
                                onBackupRestoreDone()
                            }
                        } catch (e: Exception) {
                            log.error("cannot write backup files to database directory: ", e)
                            restoreBackupState = RestoreBackupState.Done.Failure.CannotWriteFiles
                        }
                    }
                }
            }
        }
    }
}