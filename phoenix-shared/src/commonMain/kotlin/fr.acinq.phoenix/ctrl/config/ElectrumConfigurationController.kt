package fr.acinq.phoenix.ctrl.config

import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.phoenix.data.ElectrumConfig

typealias ElectrumConfigurationController = MVI.Controller<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>

object ElectrumConfiguration {

    data class Model(
        val configuration: ElectrumConfig? = null,
        val connection: Connection = Connection.CLOSED,
        val feeRate: Long = 0,
        val blockHeight: Int = 0,
        val tipTimestamp: Long = 0,
        val walletIsInitialized: Boolean = false,
        val error: Error? = null
    ) : MVI.Model() {
        fun isCustom() = configuration != null && configuration is ElectrumConfig.Custom
    }

    sealed class Intent : MVI.Intent() {
        data class UpdateElectrumServer(val address: String?) : Intent()
    }

}
