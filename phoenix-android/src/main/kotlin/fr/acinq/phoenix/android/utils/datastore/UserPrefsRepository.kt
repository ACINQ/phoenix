/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.utils.datastore

import android.text.format.DateUtils
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import fr.acinq.phoenix.managers.NodeParamsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class UserPrefsRepository(private val data: DataStore<Preferences>) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true } // some prefs are json-serialized

    /** Retrieve preferences from [data], with a fallback to empty prefs if the data file can't be read. */
    private val safeData: Flow<Preferences> = data.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    suspend fun clear() = data.edit { it.clear() }

    private companion object {
        // display
        private val BITCOIN_UNIT = stringPreferencesKey("BITCOIN_UNIT")
        private val FIAT_CURRENCY = stringPreferencesKey("FIAT_CURRENCY")
        private val SHOW_AMOUNT_IN_FIAT = booleanPreferencesKey("SHOW_AMOUNT_IN_FIAT")
        private val HOME_AMOUNT_DISPLAY_MODE = stringPreferencesKey("HOME_AMOUNT_DISPLAY_MODE")
        private val THEME = stringPreferencesKey("THEME")
        private val HIDE_BALANCE = booleanPreferencesKey("HIDE_BALANCE")
        // electrum
        val PREFS_ELECTRUM_ADDRESS_HOST = stringPreferencesKey("PREFS_ELECTRUM_ADDRESS_HOST")
        val PREFS_ELECTRUM_ADDRESS_PORT = intPreferencesKey("PREFS_ELECTRUM_ADDRESS_PORT")
        val PREFS_ELECTRUM_ADDRESS_REQUIRE_ONION_IF_TOR_ENABLED = booleanPreferencesKey("PREFS_ELECTRUM_ADDRESS_REQUIRE_ONION_IF_TOR_ENABLED")
        val PREFS_ELECTRUM_ADDRESS_PINNED_KEY = stringPreferencesKey("PREFS_ELECTRUM_ADDRESS_PINNED_KEY")
        // access control
        val PREFS_SCREEN_LOCK_BIOMETRICS = booleanPreferencesKey("PREFS_SCREEN_LOCK")
        val PREFS_SCREEN_LOCK_CUSTOM_PIN_ENABLED = booleanPreferencesKey("PREFS_SCREEN_LOCK_CUSTOM_PIN_ENABLED")
        val PREFS_CUSTOM_PIN_ATTEMPT_COUNT = intPreferencesKey("PREFS_CUSTOM_PIN_ATTEMPT_COUNT")
        val PREFS_AUTO_LOCK_DELAY = longPreferencesKey("PREFS_AUTO_LOCK_DELAY")
        // payments options
        private val INVOICE_DEFAULT_DESC = stringPreferencesKey("INVOICE_DEFAULT_DESC")
        private val INVOICE_DEFAULT_EXPIRY = longPreferencesKey("INVOICE_DEFAULT_EXPIRY")
        private val TRAMPOLINE_MAX_BASE_FEE = longPreferencesKey("TRAMPOLINE_MAX_BASE_FEE")
        private val TRAMPOLINE_MAX_PROPORTIONAL_FEE = longPreferencesKey("TRAMPOLINE_MAX_PROPORTIONAL_FEE")
        private val SWAP_ADDRESS_FORMAT = intPreferencesKey("SWAP_ADDRESS_FORMAT")
        private val LNURL_AUTH_SCHEME = intPreferencesKey("LNURL_AUTH_SCHEME")
        private val IS_OVERPAYMENT_ENABLED = booleanPreferencesKey("IS_OVERPAYMENT_ENABLED")
        // liquidity policy & channels management
        private val LIQUIDITY_POLICY = stringPreferencesKey("LIQUIDITY_POLICY")
        private val INCOMING_MAX_SAT_FEE_INTERNAL_TRACKER = longPreferencesKey("INCOMING_MAX_SAT_FEE_INTERNAL_TRACKER")
        private val INCOMING_MAX_PROP_FEE_INTERNAL_TRACKER = intPreferencesKey("INCOMING_MAX_PROP_FEE_INTERNAL_TRACKER")
        // tor
        private val IS_TOR_ENABLED = booleanPreferencesKey("IS_TOR_ENABLED")
        // misc
        private val SHOW_NOTIFICATION_PERMISSION_REMINDER = booleanPreferencesKey("SHOW_NOTIFICATION_PERMISSION_REMINDER")
    }

    val getBitcoinUnit: Flow<BitcoinUnit> = safeData.map { it[BITCOIN_UNIT]?.let { BitcoinUnit.valueOfOrNull(it) } ?: BitcoinUnit.Sat }
    suspend fun saveBitcoinUnit(coinUnit: BitcoinUnit) = data.edit { it[BITCOIN_UNIT] = coinUnit.name }

    val getFiatCurrency: Flow<FiatCurrency> = safeData.map { it[FIAT_CURRENCY]?.let { FiatCurrency.valueOfOrNull(it) } ?: FiatCurrency.USD }
    suspend fun saveFiatCurrency(currency: FiatCurrency) = data.edit { it[FIAT_CURRENCY] = currency.name }

    val getIsAmountInFiat: Flow<Boolean> = safeData.map { it[SHOW_AMOUNT_IN_FIAT] ?: false }
    suspend fun saveIsAmountInFiat(inFiat: Boolean) = data.edit { it[SHOW_AMOUNT_IN_FIAT] = inFiat }

    val getHomeAmountDisplayMode: Flow<HomeAmountDisplayMode> = safeData.map {
        HomeAmountDisplayMode.safeValueOf(it[HOME_AMOUNT_DISPLAY_MODE])
    }
    suspend fun saveHomeAmountDisplayMode(displayMode: HomeAmountDisplayMode) = data.edit {
        it[HOME_AMOUNT_DISPLAY_MODE] = displayMode.name
        when (displayMode) {
            HomeAmountDisplayMode.FIAT -> it[SHOW_AMOUNT_IN_FIAT] = true
            HomeAmountDisplayMode.BTC -> it[SHOW_AMOUNT_IN_FIAT] = false
            else -> Unit
        }
    }

    val getUserTheme: Flow<UserTheme> = safeData.map { UserTheme.safeValueOf(it[THEME]) }
    suspend fun saveUserTheme(theme: UserTheme) = data.edit { it[THEME] = theme.name }

    val getHideBalance: Flow<Boolean> = safeData.map { it[HIDE_BALANCE] ?: false }
    suspend fun saveHideBalance(hideBalance: Boolean) = data.edit { it[HIDE_BALANCE] = hideBalance }

    val getElectrumServer: Flow<ElectrumConfig.Custom?> = safeData.map {
        val host = it[PREFS_ELECTRUM_ADDRESS_HOST]?.takeIf { it.isNotBlank() }
        val port = it[PREFS_ELECTRUM_ADDRESS_PORT]
        val requireOnionIfTorEnabled = it[PREFS_ELECTRUM_ADDRESS_REQUIRE_ONION_IF_TOR_ENABLED] ?: true
        val pinnedKey = it[PREFS_ELECTRUM_ADDRESS_PINNED_KEY]?.takeIf { it.isNotBlank() }
        log.debug("retrieved electrum address from datastore, host=$host port=$port key=$pinnedKey")
        if (host != null && port != null && pinnedKey == null) {
            ElectrumConfig.Custom.create(ServerAddress(host, port, TcpSocket.TLS.TRUSTED_CERTIFICATES()), requireOnionIfTorEnabled)
        } else if (host != null && port != null && pinnedKey != null) {
            ElectrumConfig.Custom.create(ServerAddress(host, port, TcpSocket.TLS.PINNED_PUBLIC_KEY(pinnedKey)), requireOnionIfTorEnabled)
        } else {
            null
        }
    }

    suspend fun saveElectrumServer(config: ElectrumConfig.Custom?) = data.edit {
        if (config == null) {
            it.remove(PREFS_ELECTRUM_ADDRESS_HOST)
            it.remove(PREFS_ELECTRUM_ADDRESS_PORT)
            it.remove(PREFS_ELECTRUM_ADDRESS_PINNED_KEY)
            it.remove(PREFS_ELECTRUM_ADDRESS_REQUIRE_ONION_IF_TOR_ENABLED)
        } else {
            it[PREFS_ELECTRUM_ADDRESS_HOST] = config.server.host
            it[PREFS_ELECTRUM_ADDRESS_PORT] = config.server.port
            it[PREFS_ELECTRUM_ADDRESS_REQUIRE_ONION_IF_TOR_ENABLED] = config.requireOnionIfTorEnabled
            val tls = config.server.tls
            if (tls is TcpSocket.TLS.PINNED_PUBLIC_KEY) {
                it[PREFS_ELECTRUM_ADDRESS_PINNED_KEY] = tls.pubKey
            } else {
                it.remove(PREFS_ELECTRUM_ADDRESS_PINNED_KEY)
            }
        }
    }

    // -- security

    val getIsBiometricLockEnabled: Flow<Boolean> = safeData.map { it[PREFS_SCREEN_LOCK_BIOMETRICS] ?: false }
    suspend fun saveIsBiometricLockEnabled(isEnabled: Boolean) = data.edit { it[PREFS_SCREEN_LOCK_BIOMETRICS] = isEnabled }

    val getIsCustomPinLockEnabled: Flow<Boolean> = safeData.map { it[PREFS_SCREEN_LOCK_CUSTOM_PIN_ENABLED] ?: false }
    suspend fun saveIsCustomPinLockEnabled(isEnabled: Boolean) = data.edit { it[PREFS_SCREEN_LOCK_CUSTOM_PIN_ENABLED] = isEnabled }

    val getPinCodeAttempt: Flow<Int> = safeData.map {
        it[PREFS_CUSTOM_PIN_ATTEMPT_COUNT] ?: 0
    }
    suspend fun savePinCodeFailure() = data.edit {
        it[PREFS_CUSTOM_PIN_ATTEMPT_COUNT] = (it[PREFS_CUSTOM_PIN_ATTEMPT_COUNT] ?: 0) + 1
    }
    suspend fun savePinCodeSuccess() = data.edit {
        it[PREFS_CUSTOM_PIN_ATTEMPT_COUNT] = 0
    }

    val getAutoLockDelay: Flow<Duration> = safeData.map {
        it[PREFS_AUTO_LOCK_DELAY]?.toDuration(DurationUnit.MILLISECONDS) ?: 10.minutes
    }
    suspend fun saveAutoLockDelay(delay: Duration) = data.edit {
        it[PREFS_AUTO_LOCK_DELAY] = delay.inWholeMilliseconds
    }

    val getInvoiceDefaultDesc: Flow<String> = safeData.map { it[INVOICE_DEFAULT_DESC]?.takeIf { it.isNotBlank() } ?: "" }
    suspend fun saveInvoiceDefaultDesc(description: String) = data.edit { it[INVOICE_DEFAULT_DESC] = description }

    val getInvoiceDefaultExpiry: Flow<Long> = safeData.map { it[INVOICE_DEFAULT_EXPIRY] ?: (DateUtils.WEEK_IN_MILLIS / 1000) }
    suspend fun saveInvoiceDefaultExpiry(expirySeconds: Long) = data.edit { it[INVOICE_DEFAULT_EXPIRY] = expirySeconds }

    val getTrampolineMaxFee: Flow<TrampolineFees?> = safeData.map {
        val feeBase = it[TRAMPOLINE_MAX_BASE_FEE]?.sat
        val feeProportional = it[TRAMPOLINE_MAX_PROPORTIONAL_FEE]
        if (feeBase != null && feeProportional != null) {
            TrampolineFees(feeBase, feeProportional, CltvExpiryDelta(144))
        } else null
    }

    suspend fun saveTrampolineMaxFee(fee: TrampolineFees?) = data.edit {
        if (fee == null) {
            it.remove(TRAMPOLINE_MAX_BASE_FEE)
            it.remove(TRAMPOLINE_MAX_PROPORTIONAL_FEE)
        } else {
            it[TRAMPOLINE_MAX_BASE_FEE] = fee.feeBase.toLong()
            it[TRAMPOLINE_MAX_PROPORTIONAL_FEE] = fee.feeProportional
        }
    }

    val getSwapAddressFormat: Flow<SwapAddressFormat> = safeData.map {
        it[SWAP_ADDRESS_FORMAT]?.let { SwapAddressFormat.getFormatForCode(it) } ?: SwapAddressFormat.TAPROOT_ROTATE
    }
    suspend fun saveSwapAddressFormat(format: SwapAddressFormat) = data.edit {
        log.info("saving swap-address-format=$format")
        it[SWAP_ADDRESS_FORMAT] = format.code
    }

    val getLiquidityPolicy: Flow<LiquidityPolicy> = safeData.map {
        try {
            it[LIQUIDITY_POLICY]?.let { policy ->
                when (val res = json.decodeFromString<InternalLiquidityPolicy>(policy)) {
                    is InternalLiquidityPolicy.Auto -> LiquidityPolicy.Auto(
                        inboundLiquidityTarget = null,
                        maxAbsoluteFee = res.maxAbsoluteFee,
                        maxRelativeFeeBasisPoints = res.maxRelativeFeeBasisPoints,
                        skipAbsoluteFeeCheck = res.skipAbsoluteFeeCheck,
                        maxAllowedFeeCredit = 0.msat,
                    )
                    is InternalLiquidityPolicy.Disable -> LiquidityPolicy.Disable
                }
            }
        } catch (e: Exception) {
            log.error("failed to read liquidity-policy preference, replace with default: ${e.localizedMessage}")
            saveLiquidityPolicy(NodeParamsManager.defaultLiquidityPolicy)
            null
        } ?: NodeParamsManager.defaultLiquidityPolicy
    }

    suspend fun saveLiquidityPolicy(policy: LiquidityPolicy) = data.edit {
        log.info("saving new liquidity policy=$policy")
        val serialisable = when (policy) {
            is LiquidityPolicy.Auto -> InternalLiquidityPolicy.Auto(policy.maxRelativeFeeBasisPoints, policy.maxAbsoluteFee, policy.skipAbsoluteFeeCheck)
            is LiquidityPolicy.Disable -> InternalLiquidityPolicy.Disable
        }
        it[LIQUIDITY_POLICY] = json.encodeToString(serialisable)
        // also save the fee so that we don't lose the user fee preferences even when using a disabled policy
        if (policy is LiquidityPolicy.Auto) {
            it[INCOMING_MAX_SAT_FEE_INTERNAL_TRACKER] = policy.maxAbsoluteFee.sat
            it[INCOMING_MAX_PROP_FEE_INTERNAL_TRACKER] = policy.maxRelativeFeeBasisPoints
        }
    }

    /** This is used to keep track of the user's max fee preferences, even if he's not currently using a relevant liquidity policy. */
    val getIncomingMaxSatFeeInternal: Flow<Satoshi?> = safeData.map {
        it[INCOMING_MAX_SAT_FEE_INTERNAL_TRACKER]?.sat ?: NodeParamsManager.defaultLiquidityPolicy.maxAbsoluteFee
    }

    /** This is used to keep track of the user's proportional fee preferences, even if he's not currently using a relevant liquidity policy. */
    val getIncomingMaxPropFeeInternal: Flow<Int?> = safeData.map {
        it[INCOMING_MAX_PROP_FEE_INTERNAL_TRACKER] ?: NodeParamsManager.defaultLiquidityPolicy.maxRelativeFeeBasisPoints
    }

    val getLnurlAuthScheme: Flow<LnurlAuth.Scheme?> = safeData.map {
        when (it[LNURL_AUTH_SCHEME]) {
            LnurlAuth.Scheme.DEFAULT_SCHEME.id -> LnurlAuth.Scheme.DEFAULT_SCHEME
            LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME.id -> LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME
            else -> LnurlAuth.Scheme.DEFAULT_SCHEME
        }
    }

    suspend fun saveLnurlAuthScheme(scheme: LnurlAuth.Scheme?) = data.edit {
        if (scheme == null) {
            it.remove(LNURL_AUTH_SCHEME)
        } else {
            it[LNURL_AUTH_SCHEME] = scheme.id
        }
    }

    val getIsOverpaymentEnabled: Flow<Boolean> = safeData.map { it[IS_OVERPAYMENT_ENABLED] ?: false }
    suspend fun saveIsOverpaymentEnabled(enabled: Boolean) = data.edit { it[IS_OVERPAYMENT_ENABLED] = enabled }

    val getIsTorEnabled: Flow<Boolean> = safeData.map { it[IS_TOR_ENABLED] ?: false }
    suspend fun saveIsTorEnabled(isEnabled: Boolean) = data.edit { it[IS_TOR_ENABLED] = isEnabled }

    val getShowNotificationPermissionReminder: Flow<Boolean> = safeData.map { it[SHOW_NOTIFICATION_PERMISSION_REMINDER] ?: true }
    suspend fun saveShowNotificationPermissionReminder(show: Boolean) = data.edit { it[SHOW_NOTIFICATION_PERMISSION_REMINDER] = show }
}

/** Our own format for [LiquidityPolicy], serializable and decoupled from lightning-kmp. */
@Serializable
sealed class InternalLiquidityPolicy {
    @Serializable
    data object Disable : InternalLiquidityPolicy()

    @Serializable
    data class Auto(
        val maxRelativeFeeBasisPoints: Int,
        @Serializable(with = SatoshiSerializer::class) val maxAbsoluteFee: Satoshi,
        val skipAbsoluteFeeCheck: Boolean
    ) : InternalLiquidityPolicy()
}

enum class HomeAmountDisplayMode {
    BTC, FIAT, REDACTED;

    companion object {
        fun safeValueOf(mode: String?) = when (mode) {
            FIAT.name -> FIAT
            REDACTED.name -> REDACTED
            else -> BTC
        }
    }
}

enum class SwapAddressFormat(val code: Int) {
    LEGACY(0), TAPROOT_ROTATE(1);
    companion object {
        fun getFormatForCode(code: Int) = when (code) {
            0 -> LEGACY
            else -> TAPROOT_ROTATE
        }
    }
}
