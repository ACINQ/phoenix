package fr.acinq.phoenix.app

import fr.acinq.eklair.blockchain.electrum.ElectrumClient
import fr.acinq.eklair.blockchain.electrum.HeaderSubscriptionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class AppElectrumDaemon(override val di: DI) : DIAware, CoroutineScope by MainScope() {
    private val electrumClient: ElectrumClient by instance()
    private val appConfigurationManager: AppConfigurationManager by instance()

    init {
        launch {
            electrumClient.openNotificationsSubscription().consumeAsFlow()
                .filterIsInstance<HeaderSubscriptionResponse>().collect { notification ->
                    appConfigurationManager.putElectrumServer(
                        appConfigurationManager.getElectrumServer().copy(
                            blockHeight = notification.height,
                            tipTimestamp = notification.header.time
                        )
                    )
                }
        }
    }
}
