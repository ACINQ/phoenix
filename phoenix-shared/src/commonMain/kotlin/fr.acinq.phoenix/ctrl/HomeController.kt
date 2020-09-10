package fr.acinq.phoenix.ctrl

import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.plus


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val connections: Connections,
        val balanceSat: Long,
        val history: List<Transaction>,
        val lastTransaction: Transaction?
    ) : MVI.Model() {
    }

    val emptyModel = Model(Connections(), 0, emptyList(), null)

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}

data class Connections(
    val internet: Connection = Connection.CLOSED,
    val peer: Connection = Connection.CLOSED,
    val electrum: Connection = Connection.CLOSED
) {
    val global : Connection
        get() = internet + peer + electrum
}
