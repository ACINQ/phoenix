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

package fr.acinq.phoenix.android.initwallet.restore

import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.initwallet.InitViewModel
import fr.acinq.phoenix.android.security.EncryptedData
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileOutputStream


sealed class RestoreWalletState {
    data object Disclaimer : RestoreWalletState()
    sealed class SeedInput : RestoreWalletState() {
        data object Pending: SeedInput()
        data object Valid: SeedInput()
        data object Invalid: SeedInput()
    }
}

sealed class RestorePaymentsDbState {
    data object Init : RestorePaymentsDbState()
    data object Importing : RestorePaymentsDbState()
    data class Success(val fileName: String, val decryptedDatabase: ByteVector) : RestorePaymentsDbState()

    sealed class Failure : RestorePaymentsDbState() {
        data class Error(val e: Throwable) : Failure()
        data object InvalidSeed : Failure()
        data object UnresolvedDatabaseFile : Failure()
        data object CannotDecryptDatabase : Failure()
        data object CannotWriteDatabaseFile : Failure()
    }
}

class RestoreWalletViewModel(override val application: PhoenixApplication) : InitViewModel() {

    var state by mutableStateOf<RestoreWalletState>(RestoreWalletState.Disclaimer)
    var restorePaymentsDbState by mutableStateOf<RestorePaymentsDbState>(RestorePaymentsDbState.Init)
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

    fun checkSeedAndWrite(onSeedWritten: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed and db files: ${e.message}")
            state = RestoreWalletState.SeedInput.Invalid
        }) {
            val words = mnemonics.filterNot { it.isNullOrBlank() }.filterNotNull()
            if (words.size != 12) throw RuntimeException("invalid mnemonics size=${words.size}")
            MnemonicCode.validate(words, MnemonicLanguage.English.wordlist())

            val restoreDbState = restorePaymentsDbState
            if (restoreDbState is RestorePaymentsDbState.Success) {
                log.info("restoring payments-db file=${restoreDbState.fileName}")
                try {
                    val seed = MnemonicCode.toSeed(words, "")
                    val keyManager = LocalKeyManager(seed = seed.byteVector(), chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey

                    restoreDbFile(DatabaseManager.paymentsDbName(NodeParamsManager.chain, nodeId), restoreDbState.decryptedDatabase.toByteArray(), canOverwrite = true)
                    delay(200)
                    log.info("payments-db has been restored")
                } catch (e: Exception) {
                    log.error("cannot write payments-db file to database directory: ", e)
                    restorePaymentsDbState = RestorePaymentsDbState.Failure.CannotWriteDatabaseFile
                    return@launch
                }
            }

            writeSeed(mnemonics = words, isNewWallet = false, onSeedWritten = onSeedWritten)
        }
    }

    /** Restore a database file to the app's database folder. If restoring a channels database, [canOverwrite] should ALWAYS be false. */
    private fun restoreDbFile(fileName: String, fileData: ByteArray, canOverwrite: Boolean) {
        val dbFile = application.applicationContext.getDatabasePath(fileName)
        if (dbFile.exists() && !canOverwrite) {
            throw RuntimeException("cannot overwrite file=$fileName")
        } else {
            FileOutputStream(dbFile, false).use { fos ->
                fos.write(fileData)
                fos.flush()
            }
        }
    }

    fun loadPaymentsDb(uri: Uri) {
        if (restorePaymentsDbState is RestorePaymentsDbState.Importing) return

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when importing payments-db: ", e)
            restorePaymentsDbState = RestorePaymentsDbState.Failure.Error(e)
        }) {
            restorePaymentsDbState = RestorePaymentsDbState.Importing
            val words = mnemonics.filterNot { it.isNullOrBlank() }.filterNotNull()
            val seed = try {
                MnemonicCode.toSeed(words, "")
            } catch (e: Exception) {
                log.info("invalid seed, aborting wallet restore")
                restorePaymentsDbState = RestorePaymentsDbState.Failure.InvalidSeed
                return@launch
            }

            when (val result = resolveUriContent(uri)) {
                null -> {
                    delay(500)
                    log.info("payments-db file could not be resolved for uri=$uri")
                    restorePaymentsDbState = RestorePaymentsDbState.Failure.UnresolvedDatabaseFile
                    return@launch
                }
                else -> {
                    log.info("decrypting payments database files")
                    try {
                        delay(1000)
                        val keyManager = LocalKeyManager(seed = seed.byteVector(), chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
                        val decryptedData = EncryptedData.read(result.second).decrypt(keyManager)
                        restorePaymentsDbState = RestorePaymentsDbState.Success(result.first, decryptedData)
                    } catch (e: Exception) {
                        log.error("cannot decrypt payments-db file: ", e)
                        restorePaymentsDbState = RestorePaymentsDbState.Failure.CannotDecryptDatabase
                        return@launch
                    }
                }
            }
        }
    }

    private fun resolveUriContent(uri: Uri): Pair<String, ByteArray>? {
        val resolver = application.applicationContext.contentResolver
        val fileName = resolver.query(uri,
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME,),
            null, null, null, null
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                cursor.getString(nameColumn)
            } else null
        } ?: return null

        val data = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        return Pair(fileName, data)
    }


    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RestoreWalletViewModel(application) as T
        }
    }
}