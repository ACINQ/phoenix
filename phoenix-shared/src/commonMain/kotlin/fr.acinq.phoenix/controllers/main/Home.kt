package fr.acinq.phoenix.controllers.main

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.controllers.MVI

object Home {

    data class Model(
        val balance: MilliSatoshi?,
    ) : MVI.Model()

    val emptyModel = Model(
        balance = null,
    )

    sealed class Intent : MVI.Intent()
}
