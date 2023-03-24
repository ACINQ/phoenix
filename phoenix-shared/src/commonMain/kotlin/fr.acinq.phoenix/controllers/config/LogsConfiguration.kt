package fr.acinq.phoenix.controllers.config

import fr.acinq.phoenix.controllers.MVI

object LogsConfiguration {

    sealed class Model : MVI.Model() {
        object Awaiting : Model()
        object Exporting : Model()
        data class Ready(val path: String) : Model()
    }

    sealed class Intent : MVI.Intent() {
        object Export : Intent()
    }
}
