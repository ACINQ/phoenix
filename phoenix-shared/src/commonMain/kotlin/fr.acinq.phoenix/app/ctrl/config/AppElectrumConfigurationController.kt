package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.KeyPath
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.InvalidElectrumAddress
import fr.acinq.phoenix.utils.TAG_IS_MAINNET
import fr.acinq.phoenix.utils.TAG_MASTER_PUBKEY_PATH
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppElectrumConfigurationController(di: DI) : AppController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>(di, ElectrumConfiguration.Model()) {
    private val configurationManager: AppConfigurationManager by instance()
    private val isMainnet: Boolean by instance(tag = TAG_IS_MAINNET)
    private val masterPubkeyPath: String by instance(tag = TAG_MASTER_PUBKEY_PATH)
    private val walletManager: WalletManager by instance()
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
            val wallet = walletManager.getWallet()

            model(
                if (wallet == null) {
                    ElectrumConfiguration.Model(
                        connection = electrumConnection,
                        electrumServer = configurationManager.getElectrumServer(),
                        error = error
                    )
                } else {
                    val xpub = wallet.masterPublicKey(masterPubkeyPath, isMainnet)

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
