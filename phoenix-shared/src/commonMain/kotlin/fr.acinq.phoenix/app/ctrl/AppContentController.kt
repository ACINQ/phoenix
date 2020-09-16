package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Content
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppContentController(di: DI) : AppController<Content.Model, Content.Intent>(di, Content.Model.Waiting) {
    private val walletManager: WalletManager by instance()

    init {
        launch {
            if (walletManager.getWallet() != null) {
                model(Content.Model.IsInitialized)
            } else {
                model(Content.Model.NeedInitialization)
                walletManager.openWalletUpdatesSubscription().consumeEach {
                    model(Content.Model.IsInitialized)
                    return@consumeEach
                }
            }
        }
    }

    override fun process(intent: Content.Intent) = error("Nothing to process")
}
