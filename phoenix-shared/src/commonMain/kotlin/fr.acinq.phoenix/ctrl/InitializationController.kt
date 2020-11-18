package fr.acinq.phoenix.ctrl

typealias InitializationController = MVI.Controller<Initialization.Model, Initialization.Intent>

object Initialization {

    sealed class Model : MVI.Model() {
        object Initialization : Model()
        object Creating : Model()
    }

    sealed class Intent : MVI.Intent() {
        object CreateWallet : Intent()
    }

}
