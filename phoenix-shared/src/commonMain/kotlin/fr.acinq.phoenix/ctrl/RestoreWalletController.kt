package fr.acinq.phoenix.ctrl

typealias RestoreWalletController = MVI.Controller<RestoreWallet.Model, RestoreWallet.Intent>

object RestoreWallet {

    sealed class Model : MVI.Model() {
        object Warning : Model()
        object Ready : Model()
        data class Wordlist(val words: List<String>) : Model()
        object InvalidSeed : Model()
    }

    sealed class Intent : MVI.Intent() {
        object AcceptWarning : Intent()
        data class ValidateSeed(val mnemonics: List<String>) : Intent()
        data class FilterWordList(val predicate: String) : Intent()
    }

}
