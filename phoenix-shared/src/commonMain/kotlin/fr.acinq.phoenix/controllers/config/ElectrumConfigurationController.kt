package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.controllers.AppController
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
                }
            ).collect {
                model(it)
            }
        }
    }

    override fun process(intent: ElectrumConfiguration.Intent) {
        when (intent) {
            is ElectrumConfiguration.Intent.UpdateElectrumServer -> {
                configurationManager.updateElectrumConfig(intent.server)
            }
        }
    }
}
