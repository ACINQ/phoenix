package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Content
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppContentController(di: DI) : AppController<Content.Model, Content.Intent>(di, Content.Model.NeedInitialization) {
    private val walletManager: WalletManager by instance()

    init {
        launch {
            walletManager.openWalletUpdatesSubscription().consumeEach {
                model { Content.Model.IsInitialized }
            }
        }
    }

    override fun process(intent: Content.Intent) = error("Nothing to process")
}
