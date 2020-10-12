package fr.acinq.phoenix.app

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.channel.HasCommitments
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.io.ByteVector32KSerializer
import fr.acinq.eclair.io.eclairSerializersModule
import fr.acinq.eclair.utils.UUID
import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.kodein.db.*
import org.kodein.db.model.orm.Metadata
import org.kodein.db.orm.kotlinx.KotlinxSerializer


class AppChannelsDB(dbFactory: DBFactory<DB>) : ChannelsDb {

    val db = dbFactory.open(
        "channels",
        KotlinxSerializer(eclairSerializersModule)
    )

    @Serializable
    data class Channel(val channel: HasCommitments) : Metadata {
        override val id: Any get() = channel.channelId.toHex()
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
        try {
            println("PUT: ${Channel(state)}")
            db.put(Channel(state))
            println("OK!")
        } catch (t: Throwable) {
            println("OUCH!")
            t.printStackTrace()
            println("-----")
            throw t
        }
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
            .useModels { seq ->
                seq
                    .map { it.channel }
                    .toList()
            }

    }

    override suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry) {
        db.put(HtlcInfo(channelId, commitmentNumber, paymentHash, cltvExpiry))
    }

    override suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>> {
        return db.find<HtlcInfo>()
            .byId(channelId.toHex(), commitmentNumber)
            .useModels { seq ->
                seq
                    .map { it.paymentHash to it.cltvExpiry }
                    .toList()
            }
    }

    override fun close() {
        db.close()
    }

}