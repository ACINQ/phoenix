package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.AppConnectionsDaemon
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.InvalidElectrumAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppElectrumConfigurationController(
    loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val electrumClient: ElectrumClient,
    private val appConnectionsDaemon: AppConnectionsDaemon
) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = ElectrumConfiguration.Model()
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        electrumClient = business.electrumClient,
        appConnectionsDaemon = business.appConnectionsDaemon!!
    )

    init {
        launch {
            combine(
                configurationManager.electrumConfig(),
                appConnectionsDaemon.lastElectrumServerAddress,
                electrumClient.connectionState,
                configurationManager.electrumMessages(),
                transform = { configState, currentServer, connectionState, message ->
                    ElectrumConfiguration.Model(
                        configuration = configState,
                        currentServer = currentServer,
                        connection = connectionState,
                        blockHeight = message?.height ?: 0,
                        tipTimestamp = message?.header?.time ?: 0,
                    )
                })
                .collect {
                    model(it)
                }
        }
    }


    override fun process(intent: ElectrumConfiguration.Intent) {
        when (intent) {
            is ElectrumConfiguration.Intent.UpdateElectrumServer -> {
                var error: Error? = null
                when {
                    intent.address.isNullOrBlank() -> configurationManager.updateElectrumConfig(null)
                    !intent.address.matches("""(.*):(\d*)""".toRegex()) -> {
                        error = InvalidElectrumAddress
                    }
                    else -> {
                        intent.address.split(":").let {
                            val host = it.first()
                            val port = it.last()
                            val serverAddress = ServerAddress(host, port.toInt(), TcpSocket.TLS.SAFE)
                            configurationManager.updateElectrumConfig(serverAddress)
                        }
                    }
                }
            }
        }
    }
}
