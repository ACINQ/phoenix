package fr.acinq.phoenix.ctrl

typealias InitController = MVI.Controller<Init.Model, Init.Intent>

object Init {

    sealed class Model : MVI.Model() {
        object Initialization : Model()
        object Creating : Model()
    }

    sealed class Intent : MVI.Intent() {
        object CreateWallet : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
