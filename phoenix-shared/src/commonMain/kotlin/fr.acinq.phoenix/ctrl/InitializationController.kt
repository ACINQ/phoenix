package fr.acinq.phoenix.ctrl

typealias InitializationController = MVI.Controller<Initialization.Model, Initialization.Intent>

object Initialization {

    sealed class Model : MVI.Model() {
        object Ready : Model()
        data class GeneratedWallet(val mnemonics: List<String>, val seed: ByteArray) : Model() {
            override fun toString() = "GeneratedWallet"
        }
    }

    sealed class Intent : MVI.Intent() {
        data class GenerateWallet(val entropy: ByteArray) : Intent() {
            override fun toString() = "GenerateWallet"
        }
    }

}
