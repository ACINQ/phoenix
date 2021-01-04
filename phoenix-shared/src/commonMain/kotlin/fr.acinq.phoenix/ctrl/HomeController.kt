package fr.acinq.phoenix.ctrl

import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.Connections
import fr.acinq.phoenix.utils.plus


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val balanceSat: Long,
        val history: List<Transaction>,
        val lastTransaction: Transaction?
    ) : MVI.Model() {
    }

    val emptyModel = Model(0, emptyList(), null)

    sealed class Intent : MVI.Intent()

}
