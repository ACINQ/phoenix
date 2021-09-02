package fr.acinq.phoenix.controllers.config

import fr.acinq.phoenix.controllers.MVI

object Configuration {

    sealed class Model : MVI.Model() {
        object SimpleMode : Model()
        object FullMode : Model()
    }

    sealed class Intent : MVI.Intent()
}
