package fr.acinq.phoenix.controllers.config

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.controllers.MVI

object ChannelsConfiguration {

    data class Model(
        val nodeId: String,
        val json: String,
        val channels: List<Channel>
    ) : MVI.Model() {

        data class Channel(
            val id: String,
            val isOk: Boolean,
            val stateName: String,
            val localBalance: MilliSatoshi?,
            val remoteBalance: MilliSatoshi?,
            val json: String,
            val txId: String?
        )
    }

    val emptyModel = Model("{}", "", emptyList())

    sealed class Intent: MVI.Intent()

}
