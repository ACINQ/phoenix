package fr.acinq.phoenix.ctrl


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val connected: Boolean,
        val channels: List<Channel>
    ) : MVI.Model() {
        data class Channel(val cid: String, val local: Long, val remote: Long, val state: String)
    }

    val emptyModel = Model(false, emptyList())

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
