package fr.acinq.phoenix.controllers.config

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.controllers.MVI

object CloseChannelsConfiguration {

    sealed class Model : MVI.Model() {

        object Loading : Model()
        data class Ready(val channels: List<ChannelInfo>, val address: String) : Model()
        data class ChannelsClosed(val channels: List<ChannelInfo>) : Model()

        data class ChannelInfo(
            val id: ByteVector32,
            val balance: Long, // in sats
            val status: ChannelInfoStatus
        )

        enum class ChannelInfoStatus {
            Normal,
            Offline,
            Syncing,
            Closing,
            Closed,
            Aborted
        }
    }

    sealed class Intent : MVI.Intent() {
        data class MutualCloseAllChannels(val address: String) : Intent()
        object ForceCloseAllChannels : Intent()
    }
}