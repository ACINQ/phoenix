package fr.acinq.phoenix.controllers.config

import co.touchlab.kermit.Logger
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.controllers.AppController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch


class AppElectrumConfigurationController(
    loggerFactory: Logger,
    private val configurationManager: AppConfigurationManager,
    private val electrumClient: ElectrumClient,
    private val appConnectionsDaemon: AppConnectionsDaemon?
) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(
    loggerFactory = loggerFactory,
    firstModel = ElectrumConfiguration.Model()
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory,
        configurationManager = business.appConfigurationManager,
        electrumClient = business.electrumClient,
        appConnectionsDaemon = business.appConnectionsDaemon
    )

    init {
        launch {
            combine(
                configurationManager.electrumConfig,
                appConnectionsDaemon?.lastElectrumServerAddress ?: flow { null },
                electrumClient.connectionStatus,
                configurationManager.electrumMessages,
                transform = { configState, currentServer, connectionStatus, message ->
                    ElectrumConfiguration.Model(
                        configuration = configState,
                        currentServer = currentServer,
                        connection = connectionStatus.toConnectionState(),
                        blockHeight = message?.blockHeight ?: 0,
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
