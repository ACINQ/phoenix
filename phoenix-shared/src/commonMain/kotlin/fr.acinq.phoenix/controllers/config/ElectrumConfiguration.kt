package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.data.ElectrumConfig

object ElectrumConfiguration {

    data class Model(
        val configuration: ElectrumConfig? = null,
        val currentServer: ServerAddress? = null,
        val connection: Connection = Connection.CLOSED(reason = null),
        val feeRate: Long = 0,
        val blockHeight: Int = 0,
        val tipTimestamp: Long = 0,
        val walletIsInitialized: Boolean = false,
        val error: Error? = null
    ) : MVI.Model() {
        fun isCustom() = configuration != null && configuration is ElectrumConfig.Custom
    }

    sealed class Intent : MVI.Intent() {
        data class UpdateElectrumServer(val server: ServerAddress?) : Intent()
    }
}
