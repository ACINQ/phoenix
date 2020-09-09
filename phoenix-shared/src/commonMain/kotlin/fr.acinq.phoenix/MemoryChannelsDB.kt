package fr.acinq.phoenix

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.channel.HasCommitments
import fr.acinq.eclair.db.ChannelsDb

class MemoryChannelsDB : ChannelsDb {

    private val channels = HashMap<ByteVector32, HasCommitments>()
    private val htlcInfos = HashMap<Pair<ByteVector32, Long>, Pair<ByteVector32, CltvExpiry>>()


    override suspend fun addOrUpdateChannel(state: HasCommitments) {
        channels[state.channelId] = state
    }

    override suspend fun listLocalChannels(): List<HasCommitments> {
        return channels.values.toList()
    }

    override suspend fun removeChannel(channelId: ByteVector32) {
        htlcInfos.keys.removeAll { it.first == channelId }
        channels.remove(channelId)
    }

    override suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry) {
        htlcInfos[channelId to commitmentNumber] = paymentHash to cltvExpiry
    }

    override suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>> {
        return htlcInfos.values.toList()
    }

    override fun close() {}
}