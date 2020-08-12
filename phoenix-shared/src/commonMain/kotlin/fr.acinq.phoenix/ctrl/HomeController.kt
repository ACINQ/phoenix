package fr.acinq.phoenix.ctrl

import fr.acinq.eklair.io.Peer


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val connected: Peer.Connection,
        val channels: List<Channel>
    ) : MVI.Model() {
        data class Channel(val cid: String, val local: Long, val remote: Long, val state: String)
    }

    val emptyModel = Model(Peer.Connection.CLOSED, emptyList())

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
