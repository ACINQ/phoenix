package fr.acinq.phoenix.ctrl


typealias ReceiveController = MVI.Controller<Receive.Model, Receive.Intent>

object Receive {

    sealed class Model : MVI.Model() {
        object Awaiting : Model()
        object Generating: Model()
        data class Generated(val request: String): Model()
        data class Received(val amountMsat: Long): Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Ask(val amountMsat: Long) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
