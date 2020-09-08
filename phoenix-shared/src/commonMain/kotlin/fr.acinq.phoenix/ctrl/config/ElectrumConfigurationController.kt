package fr.acinq.phoenix.ctrl.config

import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.phoenix.data.ElectrumServer

typealias ElectrumConfigurationController = MVI.Controller<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>

object ElectrumConfiguration {

    sealed class Model : MVI.Model() {
        object Empty : Model()
        data class ShowElectrumServer(
            val walletIsInitialized: Boolean = false,
            val connection: Connection,
            val electrumServer: ElectrumServer,
            val feeRate: Long = 0,
            val xpub: String? = null,
            val path: String? = null
        ) : Model()
        object InvalidAddress: Model()
    }

    sealed class Intent : MVI.Intent() {
        data class UpdateElectrumServer(val customized: Boolean, val address: String) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
