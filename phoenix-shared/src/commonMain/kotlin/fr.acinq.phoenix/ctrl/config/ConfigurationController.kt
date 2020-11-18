package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI

typealias ConfigurationController = MVI.Controller<Configuration.Model, Configuration.Intent>

object Configuration {

    sealed class Model : MVI.Model() {
        object SimpleMode : Model()
        object FullMode : Model()
    }

    sealed class Intent : MVI.Intent()
}
