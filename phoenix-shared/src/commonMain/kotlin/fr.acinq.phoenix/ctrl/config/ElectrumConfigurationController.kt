package fr.acinq.phoenix.ctrl.config

import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.phoenix.data.ElectrumServer

typealias ElectrumConfigurationController = MVI.Controller<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>

object ElectrumConfiguration {

    data class Model(
        val walletIsInitialized: Boolean = false,
        val connection: Connection = Connection.CLOSED,
        val electrumServer: ElectrumServer = ElectrumServer(),
        val feeRate: Long = 0,
        val xpub: String? = null,
        val path: String? = null,
        val error: Error? = null
    ) : MVI.Model()

    sealed class Intent : MVI.Intent() {
        data class UpdateElectrumServer(val customized: Boolean, val address: String) : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)
}
