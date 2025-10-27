package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Chain
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.lightning.io.Peer
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.data.PreferredFiatCurrencies
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.mainnetElectrumServers
import fr.acinq.phoenix.data.mainnetElectrumServersOnion
import fr.acinq.phoenix.data.platformElectrumRegtestConf
import fr.acinq.phoenix.data.testnetElectrumServers
import fr.acinq.phoenix.data.testnetElectrumServersOnion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

class AppConfigurationManager(
    private val chain: Chain,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        chain = business.chain,
        electrumWatcher = business.electrumWatcher,
    )

    init {
        watchElectrumMessages()
    }

    /**
     * Used by the [PeerManager] to know what parameters to use when starting
     * up the [Peer] connection. If null, the [PeerManager] will wait before
     * instantiating the [Peer].
     */
    private val _startupParams by lazy { MutableStateFlow<StartupParams?>(null) }
    val startupParams: StateFlow<StartupParams?> by lazy { _startupParams }
    internal fun setStartupParams(params: StartupParams) {
        if (_startupParams.value == null) _startupParams.value = params
        if (_isTorEnabled.value == null) _isTorEnabled.value = params.isTorEnabled
    }

    /**
     * Used by the [AppConnectionsDaemon] to know which server to connect to.
     * If null, the daemon will wait for a config to be set.
     */
    private val _electrumConfig by lazy { MutableStateFlow<ElectrumConfig?>(null) }
    val electrumConfig: StateFlow<ElectrumConfig?> by lazy { _electrumConfig }

    /**
     * Use this method to set a server to connect to.
     * If null, will connect to a random server from the hard-coded list.
     */
    fun updateElectrumConfig(config: ElectrumConfig.Custom?) {
        _electrumConfig.value = config ?: ElectrumConfig.Random
    }

    fun randomElectrumServer(isTorEnabled: Boolean) = when (chain) {
        Chain.Mainnet -> if (isTorEnabled) mainnetElectrumServersOnion.random() else mainnetElectrumServers.random()
        Chain.Testnet3 -> if (isTorEnabled) testnetElectrumServersOnion.random() else testnetElectrumServers.random()
        Chain.Testnet4 -> TODO()
        Chain.Signet -> TODO()
        Chain.Regtest -> platformElectrumRegtestConf()
    }

    /** The flow containing the electrum header responses messages. */
    private val _electrumMessages by lazy { MutableStateFlow<HeaderSubscriptionResponse?>(null) }
    val electrumMessages: StateFlow<HeaderSubscriptionResponse?> = _electrumMessages

    private fun watchElectrumMessages() = launch {
        electrumWatcher.client.notifications.filterIsInstance<HeaderSubscriptionResponse>().collect {
            _electrumMessages.value = it
        }
    }

    // Tor configuration
    private val _isTorEnabled = MutableStateFlow<Boolean?>(null)
    val isTorEnabled get(): StateFlow<Boolean?> = _isTorEnabled.asStateFlow()
    fun updateTorUsage(enabled: Boolean): Unit {
        _isTorEnabled.value = enabled
    }

    private val _preferredFiatCurrencies = MutableStateFlow<PreferredFiatCurrencies?>(null)
    val preferredFiatCurrencies: StateFlow<PreferredFiatCurrencies?> by lazy { _preferredFiatCurrencies }

    fun updatePreferredFiatCurrencies(current: PreferredFiatCurrencies) {
        _preferredFiatCurrencies.value = current
    }
}
