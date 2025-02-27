package fr.acinq.phoenix.managers

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.utils.extensions.isOnion
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class AppConnectionsDaemon(
    loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val peerManager: PeerManager,
    private val currencyManager: CurrencyManager,
    private val networkMonitor: NetworkMonitor,
    private val tcpSocketBuilder: suspend () -> TcpSocket.Builder,
    private val electrumClient: ElectrumClient,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        walletManager = business.walletManager,
        peerManager = business.peerManager,
        currencyManager = business.currencyManager,
        networkMonitor = business.networkMonitor,
        tcpSocketBuilder = business.tcpSocketBuilderFactory,
        electrumClient = business.electrumClient
    )

    private val logger = loggerFactory.newLogger(this::class)

    private var peerConnectionJob: Job? = null
    private var electrumConnectionJob: Job? = null
    private var httpControlFlowEnabled: Boolean = false

    private data class TrafficControl(
        val walletIsAvailable: Boolean = false,
        val internetIsAvailable: Boolean = false,
        val torIsEnabled: Boolean = false,

        /**
         * Under normal circumstances, the connections are automatically managed based on whether
         * or not the network connection is available. However, the app may need to influence
         * this decision.
         *
         * For example, on iOS:
         * - When the app goes into background mode, it wants to force a disconnect.
         * - Unless a payment is in-flight, in which case it wants to stay connected until
         *   the payment completes.
         * - And if the app is backgrounded, but the app receives a push notification,
         *   then it wants to re-connect and handle the incoming payment,
         *   and disconnect again afterwards.
         *
         * This complexity is handled by a simple voting mechanism.
         * (Think: retainCount from manual memory-management systems)
         *
         * The rules are:
         * - if disconnectCount > 0 => triggers disconnect & prevents future connection attempts
         * - if disconnectCount <= 0 => allows connection based on network availability (as usual)
         *
         * Any part of the app that "votes" is expected to properly balance their calls.
         * For example, on iOS:
         * - When the app goes into the background, it increments the count (vote to disconnect)
         *   And when the app returns to the foreground, it decrements the count (undo vote)
         * - When an in-flight payment is detected, it decrements the count (vote to remain connected).
         *   And when the payment completes, it increments the count (undo vote).
         * - When a push notifications wakes the app, in decrements the count (vote to connect).
         *   And when it finishes processing, it increments the count (undo vote).
         */
        val disconnectCount: Int = 0,

        /** If a configuration value changes, this value can be incremented to force a disconnection. Only used for Electrum. */
        val configVersion: Int = 0
    ) {
        val canConnect get() = walletIsAvailable && internetIsAvailable && disconnectCount <= 0

        fun incrementDisconnectCount(): TrafficControl {
            val safeInc = disconnectCount.let { if (it == Int.MAX_VALUE) it else it + 1 }
            return copy(disconnectCount = safeInc)
        }

        fun decrementDisconnectCount(): TrafficControl {
            val safeDec = disconnectCount.let { if (it == Int.MIN_VALUE) it else it - 1 }
            return copy(disconnectCount = safeDec)
        }

        override fun toString(): String {
            return "can_connect=${canConnect.toString().uppercase()} (should_connect=${if (disconnectCount <= 0) "YES" else "NO ($disconnectCount)" } internet=${if (internetIsAvailable) "OK" else "NOK"} tor=${if (torIsEnabled) "YES" else "NO"})"
        }
    }

    private val torControlFlow = MutableStateFlow(TrafficControl())
    private val torControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private val peerControlFlow = MutableStateFlow(TrafficControl())
    private val peerControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private val electrumControlFlow = MutableStateFlow(TrafficControl())
    private val electrumControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private val httpApiControlFlow = MutableStateFlow(TrafficControl())
    private val httpApiControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private var _lastElectrumServerAddress = MutableStateFlow<ServerAddress?>(null)
    val lastElectrumServerAddress: StateFlow<ServerAddress?> = _lastElectrumServerAddress

    init {
        fun enableControlFlow(
            label: String,
            controlFlow: MutableStateFlow<TrafficControl>,
            controlChanges: ReceiveChannel<TrafficControl.() -> TrafficControl>
        ) = launch {
            controlChanges.consumeEach { change ->
                val newState = controlFlow.value.change()
                if (newState.walletIsAvailable && (label == "peer" || label == "electrum")) {
                    logger.debug { "$label $newState" }
                }
                controlFlow.value = newState
            }
        }

        enableControlFlow("tor", torControlFlow, torControlChanges)
        enableControlFlow("peer", peerControlFlow, peerControlChanges)
        enableControlFlow("electrum", electrumControlFlow, electrumControlChanges)
        enableControlFlow("apis", httpApiControlFlow, httpApiControlChanges)

        // Wallet monitor
        launch {
            // Suspends until the wallet is initialized
            walletManager.keyManager.filterNotNull().first()
            logger.debug { "walletIsAvailable = true" }
            torControlChanges.send { copy(walletIsAvailable = true) }
            peerControlChanges.send { copy(walletIsAvailable = true) }
            electrumControlChanges.send { copy(walletIsAvailable = true) }
            httpApiControlChanges.send { copy(walletIsAvailable = true) }
        }

        // Internet monitor
        launch {
            networkMonitor.start()
            networkMonitor.networkState.collect {
                val newValue = it == NetworkState.Available
                logger.debug { "internetIsAvailable = $newValue" }
                torControlChanges.send { copy(internetIsAvailable = newValue) }
                peerControlChanges.send { copy(internetIsAvailable = newValue) }
                electrumControlChanges.send { copy(internetIsAvailable = newValue) }
                httpApiControlChanges.send { copy(internetIsAvailable = newValue) }
            }
        }

        // Tor enabled monitor
        launch {
            configurationManager.isTorEnabled.filterNotNull().collect { newValue ->
                logger.debug { "torIsEnabled = $newValue" }
                torControlChanges.send { copy(torIsEnabled = newValue) }
                peerControlChanges.send { copy(torIsEnabled = newValue) }
                electrumControlChanges.send { copy(torIsEnabled = newValue) }
                httpApiControlChanges.send { copy(torIsEnabled = newValue) }
            }
        }

        // Peer
        launch {
            var configVersion = 0
            var torIsEnabled = false
            peerControlFlow.collect {
                val peer = peerManager.getPeer()
                val forceDisconnect: Boolean =
                    if (configVersion != it.configVersion || torIsEnabled != it.torIsEnabled) {
                        configVersion = it.configVersion
                        torIsEnabled = it.torIsEnabled
                        true
                    } else {
                        false
                    }
                if (forceDisconnect || !it.canConnect) {
                    peerConnectionJob?.let { job ->
                        logger.info { "disconnect and cancel peer connection loop" }
                        job.cancelAndJoin()
                        peer.disconnect()
                        peerConnectionJob = null
                    }
                }
                if (it.canConnect) {
                    if (peerConnectionJob == null) {
                        logger.debug { "starting peer connection loop" }
                        peerConnectionJob = connectionLoop(
                            name = "Peer",
                            statusStateFlow = peer.connectionState.stateIn(this)
                        ) { connectionAttempt ->
                            peer.socketBuilder = tcpSocketBuilder()
                            try {
                                val (connectTimeout, handshakeTimeout) = when {
                                    connectionAttempt <= 1 -> 1.seconds to 2.seconds
                                    connectionAttempt <= 3 -> 2.seconds to 4.seconds
                                    connectionAttempt <= 6 -> 4.seconds to 7.seconds
                                    connectionAttempt <= 10 -> 7.seconds to 10.seconds
                                    else -> 10.seconds to 15.seconds
                                }.run { if (it.torIsEnabled) first.times(3) to second.times(3) else this }
                                logger.info { "calling Peer.connect with connect_timeout=$connectTimeout handshake_timeout=$handshakeTimeout" }
                                if (it.torIsEnabled && !peer.walletParams.trampolineNode.isOnion) {
                                    logger.error { "PEER CONNECTION ABORTED: MUST USE AN ONION ADDRESS" }
                                } else {
                                    val res = peer.connect(connectTimeout = connectTimeout, handshakeTimeout = handshakeTimeout)
                                    logger.debug { "finished peer.connect ($res) " }
                                }
                            } catch (e: Exception) {
                                logger.error { "error when connecting to peer: ${e.message ?: e::class.simpleName}" }
                            }
                        }
                    }
                }
            }
        }

        // Electrum
        launch {
            var configVersion = 0
            var torIsEnabled = false
            electrumControlFlow.collect {
                val forceDisconnect: Boolean =
                    if (configVersion != it.configVersion || torIsEnabled != it.torIsEnabled) {
                        configVersion = it.configVersion
                        torIsEnabled = it.torIsEnabled
                        true
                    } else {
                        false
                    }
                if (forceDisconnect || !it.canConnect) {
                    electrumConnectionJob?.let { job ->
                        logger.info { "disconnect and cancel electrum connection loop" }
                        job.cancelAndJoin()
                        electrumClient.disconnect()
                        electrumConnectionJob = null
                    }
                }
                if (it.canConnect) {
                    if (electrumConnectionJob == null) {
                        logger.debug { "starting electrum connection loop" }
                        electrumConnectionJob = connectionLoop(
                            name = "Electrum",
                            statusStateFlow = electrumClient.connectionStatus.map { it.toConnectionState() }.stateIn(this)
                        ) { connectionAttempt ->
                            val electrumConfig = configurationManager.electrumConfig.value
                            val electrumServerAddress = when (electrumConfig) {
                                is ElectrumConfig.Custom -> electrumConfig.server
                                is ElectrumConfig.Random -> configurationManager.randomElectrumServer(it.torIsEnabled)
                                null -> null
                            }
                            if (electrumServerAddress == null) {
                                logger.debug { "ignoring electrum connection opportunity because no server is configured yet" }
                            } else {
                                try {
                                    val handshakeTimeout = when {
                                        it.torIsEnabled && connectionAttempt <= 6 -> 20.seconds
                                        it.torIsEnabled -> 40.seconds
                                        connectionAttempt <= 3 -> 4.seconds
                                        connectionAttempt <= 6 -> 7.seconds
                                        connectionAttempt <= 10 -> 15.seconds
                                        else -> 20.seconds
                                    }
                                    logger.info { "calling ElectrumClient.connect to server=$electrumServerAddress with handshake_timeout=$handshakeTimeout" }
                                    val requireOnionIfTorEnabled = electrumConfig is ElectrumConfig.Custom && electrumConfig.requireOnionIfTorEnabled
                                    if (it.torIsEnabled && !electrumServerAddress.isOnion && requireOnionIfTorEnabled) {
                                        logger.error { "ELECTRUM CONNECTION ABORTED: MUST USE AN ONION ADDRESS" }
                                    } else {
                                        electrumClient.connect(electrumServerAddress, tcpSocketBuilder(), timeout = handshakeTimeout)
                                    }
                                } catch (e: Exception) {
                                    logger.error { "error when connecting to electrum: ${e.message ?: e::class.simpleName}"}
                                }
                            }
                            _lastElectrumServerAddress.value = electrumServerAddress
                        }
                    }
                }
            }
        }

        // HTTP APIs
        launch {
            httpApiControlFlow.collect {
                when {
                    it.internetIsAvailable && it.disconnectCount <= 0 -> {
                        if (!httpControlFlowEnabled) {
                            httpControlFlowEnabled = true
                            configurationManager.enableNetworkAccess()
                            currencyManager.enableNetworkAccess()
                        }
                    }
                    else -> {
                        if (httpControlFlowEnabled) {
                            httpControlFlowEnabled = false
                            configurationManager.disableNetworkAccess()
                            currencyManager.disableNetworkAccess()
                        }
                    }
                }
            }
        }

        // Listen to electrum configuration changes and reconnect when needed.
        launch {
            var previousElectrumConfig: ElectrumConfig? = null
            configurationManager.electrumConfig.collect { newElectrumConfig ->
                val changed = when (val oldElectrumConfig = previousElectrumConfig) {
                    null -> newElectrumConfig != null
                    else -> newElectrumConfig != oldElectrumConfig
                }
                if (changed) {
                    logger.info { "electrum config has changed: from=$previousElectrumConfig to $newElectrumConfig, reconnecting..." }
                    electrumControlChanges.send { copy(configVersion = configVersion + 1) }
                } else {
                    logger.debug { "electrum config: no changes" }
                }
                previousElectrumConfig = newElectrumConfig
            }
        }
    }

    data class ControlTarget(val flags: Int) { // <- bitmask

        companion object {
            val Peer = ControlTarget(0b0001)
            val Electrum = ControlTarget(0b0010)
            val Http = ControlTarget(0b0100)
            val Tor = ControlTarget(0b1000)
            val All = ControlTarget(0b1111)
        }

        /* The `+` operator is implemented, so it can be used like so:
         * `val options = ControlTarget.Peer + ControlTarget.Electrum`
         */
        operator fun plus(other: ControlTarget): ControlTarget {
            return ControlTarget(this.flags or other.flags)
        }

        fun contains(options: ControlTarget): Boolean {
            return (this.flags and options.flags) != 0
        }

        val containsPeer get() = contains(Peer)
        val containsElectrum get() = contains(Electrum)
        val containsHttp get() = contains(Http)
        val containsTor get() = contains(Tor)
    }

    /** Vote to disconnect the target. */
    fun incrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.containsPeer) {
                peerControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsElectrum) {
                electrumControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsHttp) {
                httpApiControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsTor) {
                torControlChanges.send { incrementDisconnectCount() }
            }
        }
    }

    /** Vote to connect the target. */
    fun decrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.containsPeer) {
                peerControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsElectrum) {
                electrumControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsHttp) {
                httpApiControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsTor) {
                torControlChanges.send { decrementDisconnectCount() }
            }
        }
    }

    /** Vote to connect the target. */
    fun forceReconnect(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.containsPeer) {
                peerControlChanges.send { copy(disconnectCount = -1, configVersion = this.configVersion + 1) }
            }
            if (target.containsElectrum) {
                electrumControlChanges.send { copy(disconnectCount = -1, configVersion = this.configVersion + 1) }
            }
            if (target.containsHttp) {
                httpApiControlChanges.send { copy(disconnectCount = -1) }
            }
            if (target.containsTor) {
                torControlChanges.send { copy(disconnectCount = -1) }
            }
        }
    }

    /**
     * Attempts to connect to [name] everytime the connection changes to [Connection.CLOSED]. Repeated failed attempts
     * are throttled with an exponential backoff. This pause cannot be cancelled, which can be an issue if the network
     * conditions have just changed and we want to reconnect immediately. In this case this job should be cancelled
     * altogether and restarted.
     *
     * @param connect the parameter is the current number of failed consecutive attempts. If this counter is large (i.e.
     *          we have connection issues), the internal connection method should use more lax parameters. Conversely, if
     *          we've just started the connection loop, the connection method should fail fast to provide a snappier UX.
     */
    private fun connectionLoop(
        name: String,
        statusStateFlow: StateFlow<Connection>,
        connect: suspend (Int) -> Unit
    ) = launch {
        // tracks how many failed connection attempts have been made in a row
        // when connection keeps failing, this loop is paused for a bit
        var connectionCounter = 0
        statusStateFlow.collect {
            logger.debug { "$name connection state is $it" }
            if (it is Connection.CLOSED) {
                val pause = connectionPause(connectionCounter)
                logger.info { "next $name connection attempt #$connectionCounter in $pause" }
                delay(pause)
                connectionCounter++
                connect(connectionCounter)
            } else if (it == Connection.ESTABLISHED) {
                connectionCounter = 0
            }
        }
    }

    private fun connectionPause(attemptCount: Int): Duration {
        return when {
            attemptCount <= 0 -> 0.1.seconds
            attemptCount == 1 -> 0.25.seconds
            attemptCount == 2 -> 0.5.seconds
            attemptCount == 3 -> 1.seconds
            attemptCount == 4 -> 2.seconds
            attemptCount == 5 -> 4.seconds
            else -> 8.seconds
        }
    }
}
