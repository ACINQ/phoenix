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