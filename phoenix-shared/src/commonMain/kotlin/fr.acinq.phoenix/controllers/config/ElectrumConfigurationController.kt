package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.controllers.AppController
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
                val error: Error? = when {
                    intent.address.isNullOrBlank() -> {
                        configurationManager.updateElectrumConfig(null)
                        null
                    }
                    intent.address.matches("""(.*):(\d*)""".toRegex()) -> {
                        intent.address.split(":").let {
                            val host = it.first()
                            val port = it.last()
                            val serverAddress = ServerAddress(host, port.toInt(), TcpSocket.TLS.TRUSTED_CERTIFICATES)
                            configurationManager.updateElectrumConfig(serverAddress)
                        }
                        null
                    }
                    else -> {
                        InvalidElectrumAddress
                    }
                }
                launch {
                    model { copy(error = error) }
                }
            }
        }
    }
}
