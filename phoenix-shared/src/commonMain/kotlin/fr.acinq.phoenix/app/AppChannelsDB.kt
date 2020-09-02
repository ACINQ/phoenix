package fr.acinq.phoenix.app

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.CltvExpiry
import fr.acinq.eklair.channel.HasCommitments
import fr.acinq.eklair.db.ChannelsDb
import fr.acinq.eklair.io.ByteVector32KSerializer
import fr.acinq.eklair.utils.UUID
import kotlinx.serialization.Serializable
import org.kodein.db.*
import org.kodein.db.model.orm.Metadata


class AppChannelsDB(dbFactory: DBFactory<DB>) : ChannelsDb {

    val db = dbFactory.open("channels")

    @Serializable
    data class Channel(val channel: HasCommitments) : Metadata {
        override val id: Any get() = listOf(channel.channelId.toHex())
    }

    @Serializable
    data class HtlcInfo(
        @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32,
        val commitmentNumber: Long,
        @Serializable(with = ByteVector32KSerializer::class) val paymentHash: ByteVector32,
        val cltvExpiry: CltvExpiry) : Metadata {
        val uuid = UUID.randomUUID()
        override val id: Any get() = listOf(channelId.toHex(), commitmentNumber, uuid)
    }

    override suspend fun addOrUpdateChannel(state: HasCommitments) {
        db.put(Channel(state))
    }

    override suspend fun removeChannel(channelId: ByteVector32) {
        db.execBatch {
            deleteAll(db.find<HtlcInfo>().byId(channelId.toHex()))
            delete(key(channelId.toHex()))
        }
    }

    override suspend fun listLocalChannels(): List<HasCommitments> {
        return db.find<Channel>()
            .all()
            .useModels()
            .map { it.channel }
            .toList()
    }

    override suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry) {
        db.put(HtlcInfo(channelId, commitmentNumber, paymentHash, cltvExpiry))
    }

    override suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>> {
        return db.find<HtlcInfo>()
            .byId(channelId.toHex(), commitmentNumber)
            .useModels()
            .map { it.paymentHash to it.cltvExpiry }
            .toList()
    }

    override fun close() {
        db.close()
    }

}