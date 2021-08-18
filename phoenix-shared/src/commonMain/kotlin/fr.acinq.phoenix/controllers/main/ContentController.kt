package fr.acinq.phoenix.controllers.main

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppContentController(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : AppController<Content.Model, Content.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Content.Model.Waiting
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        walletManager = business.walletManager
    )

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
