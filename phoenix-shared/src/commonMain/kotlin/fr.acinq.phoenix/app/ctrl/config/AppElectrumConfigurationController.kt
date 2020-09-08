package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.KeyPath
import fr.acinq.eklair.blockchain.electrum.ElectrumClient
import fr.acinq.eklair.io.Peer
import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.utils.TAG_IS_MAINNET
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppElectrumConfigurationController(di: DI) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(di, ElectrumConfiguration.Model.Empty) {
    private val configurationManager: AppConfigurationManager by instance()
    private val isMainnet: Boolean by instance(tag = TAG_IS_MAINNET)
    private val walletManager: WalletManager by instance()
    private val peer: Peer by instance()
    private val electrumClient: ElectrumClient by instance()

    private var electrumConnection: Connection = Connection.CLOSED

    init {
        sendElectrumConfigurationModel()

        launch {
            electrumClient.openConnectedSubscription().consumeEach {
                electrumConnection = it
                sendElectrumConfigurationModel()
            }
        }

        launch {
            configurationManager.openElectrumServerUpdateSubscription().consumeEach {
                sendElectrumConfigurationModel()
            }
        }
    }

    override fun process(intent: ElectrumConfiguration.Intent) {
        when(intent) {
            is ElectrumConfiguration.Intent.UpdateElectrumServer -> {
                if (intent.address.matches("""(.*):(\d*)""".toRegex())) {
                    intent.address.split(":").let {
                        val host = it.first()
                        val port = it.last().toInt()
                        configurationManager.putElectrumServerAddress(host, port)
                    }

                    sendElectrumConfigurationModel()
                } else
                    launch {
                        model { ElectrumConfiguration.Model.InvalidAddress }
                    }
            }
        }
    }

    private fun sendElectrumConfigurationModel() {
        launch {
            val wallet = walletManager.getWallet()

            model {
                if (wallet == null) {
                    ElectrumConfiguration.Model.ShowElectrumServer(
                        connection = electrumConnection,
                        electrumServer = configurationManager.getElectrumServer()
                    )
                } else {
                    val path = if (isMainnet) "m/84'/0'/0'" else "m/84'/1'/0'"
                    val xpub = wallet.derivedPublicKey(KeyPath.computePath(path), isMainnet)

                    ElectrumConfiguration.Model.ShowElectrumServer(
                        walletIsInitialized = true,
                        connection = electrumConnection,
                        electrumServer = configurationManager.getElectrumServer(),
                        xpub = xpub,
                        path = path
                    )
                }
            }
        }
    }
}
