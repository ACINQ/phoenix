package fr.acinq.phoenix.controllers.main

import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.controllers.AppController
import fr.acinq.phoenix.managers.BalanceManager
import fr.acinq.phoenix.managers.PaymentsManager
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


class AppHomeController(
    loggerFactory: LoggerFactory,
    private val paymentsManager: PaymentsManager,
    private val balanceManager: BalanceManager
) : AppController<Home.Model, Home.Intent>(
    loggerFactory = loggerFactory,
    firstModel = Home.emptyModel
) {
    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
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
            balanceManager.incomingSwaps.collect {
                model { copy(incomingBalance = it.values.sum().takeIf { it.msat > 0 }) }
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
