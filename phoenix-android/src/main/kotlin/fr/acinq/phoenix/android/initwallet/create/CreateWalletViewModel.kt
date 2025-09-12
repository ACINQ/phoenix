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

package fr.acinq.phoenix.android.initwallet.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.utils.MnemonicLanguage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CreateWalletViewModel(val application: PhoenixApplication) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun createNewWallet(writeSeed: (List<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when creating new wallet: ", e)
            throw e
        }) {
            log.debug("generating new wallet...")
            val entropy = Lightning.randomBytes(16)
            val mnemonics = MnemonicCode.toMnemonics(
                entropy = entropy,
                wordlist = MnemonicLanguage.English.wordlist()
            )
            writeSeed(mnemonics)
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateWalletViewModel(application) as T
        }
    }
}