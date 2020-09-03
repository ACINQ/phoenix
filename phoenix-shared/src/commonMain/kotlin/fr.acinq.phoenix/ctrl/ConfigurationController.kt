package fr.acinq.phoenix.ctrl

typealias ConfigurationController = MVI.Controller<Configuration.Model, Configuration.Intent>

object Configuration {

    sealed class Model : MVI.Model() {
        object SimpleMode : Model()
        object FullMode : Model()
    }

    sealed class Intent : MVI.Intent()

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
