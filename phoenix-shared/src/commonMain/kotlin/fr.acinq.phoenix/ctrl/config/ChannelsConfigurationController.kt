package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI


typealias ChannelsConfigurationController = MVI.Controller<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>


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
            val commitments: Pair<Long, Long>?,
            val json: String,
            val txUrl: String?
        )
    }

    val emptyModel = Model("{}", "", emptyList())

    sealed class Intent: MVI.Intent()

}
