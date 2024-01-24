package fr.acinq.phoenix.controllers.main

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.BalanceManager
import fr.acinq.phoenix.managers.PaymentsManager
import kotlinx.coroutines.launch


class AppHomeController(
    loggerFactory: Logger,
    private val paymentsManager: PaymentsManager,
    private val balanceManager: BalanceManager
) : AppController<Home.Model, Home.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Home.emptyModel
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.newLoggerFactory,
        paymentsManager = business.paymentsManager,
        balanceManager = business.balanceManager
    )

    init {
        launch {
            balanceManager.balance.collect {
                model { copy(balance = it) }
            }
        }

        launch {
            paymentsManager.paymentsCount.collect {
                model { copy(paymentsCount = it) }
            }
        }
    }

    override fun process(intent: Home.Intent) {}
}
