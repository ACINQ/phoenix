package fr.acinq.phoenix.controllers.main

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class AppContentController(
    loggerFactory: Logger,
    private val walletManager: WalletManager
) : AppController<Content.Model, Content.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Content.Model.Waiting
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory,
        walletManager = business.walletManager
    )

    init {
        launch {
            if (walletManager.isLoaded()) {
                model(Content.Model.IsInitialized)
            } else {
                model(Content.Model.NeedInitialization)
                // Suspends until a wallet is created
                walletManager.keyManager.filterNotNull().first()
                model(Content.Model.IsInitialized)
            }
        }
    }

    override fun process(intent: Content.Intent) = error("Nothing to process")
}
