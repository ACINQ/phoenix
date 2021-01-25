package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.InvalidElectrumAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppElectrumConfigurationController(loggerFactory: LoggerFactory, private val configurationManager: AppConfigurationManager, private val chain: Chain, private val masterPubkeyPath: String, private val walletManager: WalletManager, private val electrumClient: ElectrumClient) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(loggerFactory, ElectrumConfiguration.Model()) {
    private var electrumConnection: Connection = Connection.CLOSED

    init {
        sendElectrumConfigurationModel()

        launch {
            electrumClient.connectionState.collect {
                electrumConnection = it
                sendElectrumConfigurationModel()
            }
        }

        launch {
            configurationManager.subscribeToElectrumServer().collect {
                sendElectrumConfigurationModel()
            }
        }
    }

    override fun process(intent: ElectrumConfiguration.Intent) {
        when(intent) {
            is ElectrumConfiguration.Intent.UpdateElectrumServer -> {
                var error: Error? = null

                when {
                    !intent.customized -> {
                        configurationManager.setRandomElectrumServer()
                    }
                    !intent.address.matches("""(.*):(\d*)""".toRegex()) -> {
                        error = InvalidElectrumAddress
                    }
                    else -> {
                        intent.address.split(":").let {
                            val host = it.first()
                            val port = it.last().toInt()
                            configurationManager.putElectrumServerAddress(host, port, intent.customized)
                        }
                    }
                }

                sendElectrumConfigurationModel(error)
            }
        }
    }

    private fun sendElectrumConfigurationModel(error: Error? = null) {
        launch {
            val wallet = walletManager.wallet.value

            model(
                if (wallet == null) {
                    ElectrumConfiguration.Model(
                        connection = electrumConnection,
                        electrumServer = configurationManager.getElectrumServer(),
                        error = error
                    )
                } else {
                    val xpub = wallet.masterPublicKey(masterPubkeyPath, chain == Chain.MAINNET)

                    ElectrumConfiguration.Model(
                        walletIsInitialized = true,
                        connection = electrumConnection,
                        electrumServer = configurationManager.getElectrumServer(),
                        xpub = xpub,
                        path = masterPubkeyPath,
                        error = error
                    )
                }
            )
        }
    }
}
