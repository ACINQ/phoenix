package fr.acinq.phoenix.controllers.main

import fr.acinq.phoenix.controllers.MVI

object Content {

    sealed class Model : MVI.Model() {
        object Waiting : Model()
        object IsInitialized : Model()
        object NeedInitialization : Model()
    }

    sealed class Intent : MVI.Intent()

}
