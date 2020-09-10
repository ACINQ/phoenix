package fr.acinq.phoenix.ctrl

typealias ContentController = MVI.Controller<Content.Model, Content.Intent>

object Content {

    sealed class Model : MVI.Model() {
        object Waiting : Model()
        object IsInitialized : Model()
        object NeedInitialization : Model()
    }

    sealed class Intent : MVI.Intent()

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
