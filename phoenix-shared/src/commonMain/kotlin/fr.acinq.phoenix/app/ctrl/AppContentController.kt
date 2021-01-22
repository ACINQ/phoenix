package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.ctrl.Content
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppContentController(loggerFactory: LoggerFactory, private val walletManager: WalletManager) : AppController<Content.Model, Content.Intent>(loggerFactory, Content.Model.Waiting) {
    init {
        launch {
            if (walletManager.wallet.value != null) {
                model(Content.Model.IsInitialized)
            } else {
                model(Content.Model.NeedInitialization)
                // Suspends until a wallet is created
                walletManager.wallet.first { it != null }
                model(Content.Model.IsInitialized)
            }
        }
    }

    override fun process(intent: Content.Intent) = error("Nothing to process")
}
