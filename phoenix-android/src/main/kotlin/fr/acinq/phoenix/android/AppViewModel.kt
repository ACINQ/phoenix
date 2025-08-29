/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android


import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.components.wallet.WalletAvatars
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.datastore.InternalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Duration


sealed class ListWalletState {
    data object Init: ListWalletState()
    data object Success: ListWalletState()
    sealed class Error: ListWalletState() {
        data class Generic(val cause: Throwable?): Error()

        sealed class DecryptionError : Error() {
            data class GeneralException(val cause: Throwable): DecryptionError()
            data class KeystoreFailure(val cause: Throwable): DecryptionError()
        }
    }
}

sealed class BaseWalletId
data object EmptyWalletId: BaseWalletId()
/** Wraps a nodeIdHash (hash160 of a nodeId). Easier to maintain and upgrade than a plain String. */
@Serializable
data class WalletId(val nodeIdHash: String): BaseWalletId() {
    constructor(nodeId: PublicKey) : this(
        nodeIdHash = nodeId.hash160().byteVector().toHex()
    )
    override fun toString() = nodeIdHash
    override fun hashCode() = nodeIdHash.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletId) return false
        return nodeIdHash == other.nodeIdHash
    }
}

data class UserWallet(
    val walletId: WalletId,
    val words: List<String>,
) {
    override fun toString(): String = "UserWallet[ wallet_id=$walletId, words=*** ]"
}

data class ActiveWallet(
    val id: WalletId,
    val business: PhoenixBusiness?,
    val userPrefs: UserPrefs,
    val internalPrefs: InternalPrefs,
)

class AppViewModel(
    private val application: PhoenixApplication
) : ViewModel() {

    private val log = LoggerFactory.getLogger(AppViewModel::class.java)

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable: Runnable = Runnable { resetActiveWallet() }

    val listWalletState = mutableStateOf<ListWalletState>(ListWalletState.Init)

    private val _availableWallets = MutableStateFlow<Map<WalletId, UserWallet>>(emptyMap())
    val availableWallets = _availableWallets.asStateFlow()

    private val _desiredWalletId = MutableStateFlow<WalletId?>(null)
    val desiredWalletId = _desiredWalletId.asStateFlow()
    val startDefaultImmediately = MutableStateFlow(true)

    private val _activeWalletInUI = MutableStateFlow<ActiveWallet?>(null)
    val activeWalletInUI = _activeWalletInUI.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val exchangeRates = activeWalletInUI.filterNotNull().mapLatest { it.business }.filterNotNull().flatMapLatest { it.currencyManager.ratesFlow }
        .stateIn(viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val fiatRatesMap = exchangeRates.mapLatest { rates ->
        val usdPriceRate = rates.filterIsInstance<ExchangeRate.BitcoinPriceRate>().firstOrNull { it.fiatCurrency == FiatCurrency.USD }
        if (usdPriceRate != null) {
            rates.associate { rate ->
                rate.fiatCurrency to when (rate) {
                    is ExchangeRate.BitcoinPriceRate -> rate
                    is ExchangeRate.UsdPriceRate -> ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = rate.fiatCurrency,
                        price = rate.price * usdPriceRate.price,
                        source = rate.source,
                        timestampMillis = rate.timestampMillis
                    )
                }
            }
        } else {
            emptyMap()
        }
    }.stateIn(viewModelScope, started = SharingStarted.Lazily, initialValue = emptyMap())

    init {
        listAvailableWallets()
    }

    fun setActiveWallet(id: WalletId, business: PhoenixBusiness) {
        val userPrefs = DataStoreManager.loadUserPrefsForWallet(application.applicationContext, walletId = id)
        val internalPrefs = DataStoreManager.loadInternalPrefsForWallet(application.applicationContext, walletId = id)
        _activeWalletInUI.value = ActiveWallet(id = id, business = business, userPrefs = userPrefs, internalPrefs = internalPrefs)
        scheduleAutoLock()
    }

    fun listAvailableWallets() {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when initialising startup-view: ", e)
            listWalletState.value = ListWalletState.Error.Generic(e)
        }) {
            when (val result = SeedManager.loadAndDecrypt(context = application.applicationContext)) {
                is DecryptSeedResult.Failure.DecryptionError -> {
                    log.error("cannot decrypt seed file: ", result.cause)
                    listWalletState.value = ListWalletState.Error.DecryptionError.GeneralException(result.cause)
                }
                is DecryptSeedResult.Failure.KeyStoreFailure -> {
                    log.error("key store failure: ", result.cause)
                    listWalletState.value = ListWalletState.Error.DecryptionError.KeystoreFailure(result.cause)
                }
                is DecryptSeedResult.Failure.SeedFileUnreadable -> {
                    log.error("aborting, unreadable seed file")
                    listWalletState.value = ListWalletState.Error.Generic(null)
                }
                is DecryptSeedResult.Failure.SeedInvalid -> {
                    log.error("aborting, seed is invalid")
                    listWalletState.value = ListWalletState.Error.Generic(null)
                }

                is DecryptSeedResult.Failure.SeedFileNotFound -> {
                    listWalletState.value = ListWalletState.Success
                    _availableWallets.value = emptyMap()
                }

                is DecryptSeedResult.Success -> {
                    val metadataMap = application.globalPrefs.getAvailableWalletsMeta.first()
                    _availableWallets.value = result.mnemonicsMap.map { (walletId, words) ->
                        if (metadataMap[walletId] == null) {
                            application.globalPrefs.saveAvailableWalletMeta(walletId, name = null, avatar = WalletAvatars.list.random())
                        }
                        walletId to UserWallet(walletId = walletId, words = words)
                    }.toMap()
                    listWalletState.value = ListWalletState.Success
                }
            }
        }
    }

    fun scheduleAutoLock() {
        viewModelScope.launch {
            autoLockHandler.removeCallbacksAndMessages(null)
            val activeUserPrefs = activeWalletInUI.first()?.userPrefs ?: return@launch

            val biometricLockEnabled = activeUserPrefs.getIsScreenLockBiometricsEnabled.first()
            val customPinLockEnabled = activeUserPrefs.getIsScreenLockPinEnabled.first()
            val autoLockDelay = activeUserPrefs.getAutoLockDelay.first()

            if ((biometricLockEnabled || customPinLockEnabled) && autoLockDelay != Duration.INFINITE) {
                autoLockHandler.postDelayed(autoLockRunnable, autoLockDelay.inWholeMilliseconds)
            }
        }
    }

    /** Will signal the startup screen that the user wishes to use the given [walletId]. */
    fun switchToWallet(walletId: WalletId) {
        _desiredWalletId.value = walletId
    }

    /** Calling this method will clear the active wallet and redirect the UI to the startup screen with the wallets list prompt. */
    fun resetActiveWallet() {
        _desiredWalletId.value = null
        _activeWalletInUI.value = null
    }

    override fun onCleared() {
        super.onCleared()
        log.info("AppViewModel cleared")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY] as? PhoenixApplication)
                return AppViewModel(application) as T
            }
        }
    }
}