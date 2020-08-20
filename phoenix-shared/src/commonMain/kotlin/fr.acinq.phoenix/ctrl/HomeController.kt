package fr.acinq.phoenix.ctrl

import fr.acinq.eklair.io.Peer
import fr.acinq.phoenix.app.Transaction


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val connected: Peer.Connection,
        val balanceSat: Long,
        val history: List<Transaction>
    ) : MVI.Model() {
//        data class Channel(val cid: String, val local: Long, val remote: Long, val state: String)
    }

    val emptyModel = Model(Peer.Connection.CLOSED, 0, emptyList())

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
