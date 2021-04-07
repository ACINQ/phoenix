package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.ElectrumAddress
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
    private val electrumClient: ElectrumClient
) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(loggerFactory, ElectrumConfiguration.Model()) {

    init {
        launch {
            combine(
                configurationManager.electrumConfig(),
                electrumClient.connectionState,
                configurationManager.electrumMessages(),
                transform = { configState, connectionState, message ->
                    ElectrumConfiguration.Model(
                        configuration = configState,
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
                            val addr = ElectrumAddress(
                                host = host,
                                sslPort = port.toInt()
                            ).asServerAddress(tls = TcpSocket.TLS.SAFE)
                            configurationManager.updateElectrumConfig(addr)
                        }
                    }
                }
            }
        }
    }
}
