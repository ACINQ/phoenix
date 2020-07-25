package fr.acinq.phoenix.ctrl

import fr.acinq.eklair.MilliSatoshi


typealias DemoController = MVI.Controller<Demo.Model, Demo.Intent>

object Demo {

    sealed class Model : MVI.Model() {
        object Empty : Model()
        data class Request(val request: String) : Model()
    }

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
        data class Receive(val amount: MilliSatoshi) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}
