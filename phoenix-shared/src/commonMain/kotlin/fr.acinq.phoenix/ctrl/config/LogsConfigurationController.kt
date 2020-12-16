package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI

typealias LogsConfigurationController = MVI.Controller<LogsConfiguration.Model, LogsConfiguration.Intent>

object LogsConfiguration {

    sealed class Model : MVI.Model() {
        object Loading : Model()
        data class Ready(val path: String) : Model()
    }

    sealed class Intent : MVI.Intent()

}
