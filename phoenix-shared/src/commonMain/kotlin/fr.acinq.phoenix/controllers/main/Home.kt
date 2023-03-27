package fr.acinq.phoenix.controllers.main

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.controllers.MVI

object Home {

    data class Model(
        val balance: MilliSatoshi?,
        val paymentsCount: Long
    ) : MVI.Model()

    val emptyModel = Model(
        balance = null,
        paymentsCount = 0
    )

    sealed class Intent : MVI.Intent()
}
