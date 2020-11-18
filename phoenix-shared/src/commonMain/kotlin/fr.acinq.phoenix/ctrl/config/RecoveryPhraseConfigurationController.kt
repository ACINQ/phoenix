package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI

typealias RecoveryPhraseConfigurationController =
        MVI.Controller<RecoveryPhraseConfiguration.Model, RecoveryPhraseConfiguration.Intent>

object RecoveryPhraseConfiguration {

    sealed class Model : MVI.Model() {
        object Awaiting : Model()
        object Decrypting : Model()
        data class Decrypted(val mnemonics: List<String>) : Model()
    }

    sealed class Intent : MVI.Intent() {
        object Decrypt : Intent()
    }
}
