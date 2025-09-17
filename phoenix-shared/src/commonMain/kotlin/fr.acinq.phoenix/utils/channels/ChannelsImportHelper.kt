package fr.acinq.phoenix.utils.channels

import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.serialization.channel.Encryption.from
import fr.acinq.lightning.serialization.channel.Serialization
import fr.acinq.lightning.wire.EncryptedChannelData
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first


object ChannelsImportHelper {

    suspend fun doImportChannels(
        data: String,
        biz: PhoenixBusiness,
    ): ChannelsImportResult {

        val loggerFactory = biz.loggerFactory
        val log = loggerFactory.newLogger(this::class)
        try {

            log.info { "initiating channels-data import" }

            val nodeParams = biz.nodeParamsManager.nodeParams.filterNotNull().first()
            val peer = biz.peerManager.getPeer()

            val encryptedChannelData = try {
                EncryptedChannelData(ByteVector(Hex.decode(data)))
            } catch(e: Exception) {
                log.error(e) { "failed to deserialize data blob" }
                return ChannelsImportResult.Failure.MalformedData
            }

            return PersistedChannelState
                .from(nodeParams.nodePrivateKey, encryptedChannelData)
                .fold(
                    onFailure = {
                        log.error(it) { "failed to decrypt channel state" }
                        ChannelsImportResult.Failure.DecryptionError
                    },
                    onSuccess = {
                        when (it) {
                            is Serialization.DeserializationResult.Success -> {
                                log.info { "successfully imported channel=${it.state.channelId}" }
                                peer.db.channels.addOrUpdateChannel(it.state)
                                val channel = (peer.db.channels as? SqliteChannelsDb)?.getChannel(it.state.channelId)
                                log.info { "channel added/updated to database, is_closed=${channel?.third}" }
                                ChannelsImportResult.Success(it.state)
                            }
                            is Serialization.DeserializationResult.UnknownVersion -> {
                                log.error { "cannot use channel state: unknown version=${it.version}" }
                                ChannelsImportResult.Failure.UnknownVersion(it.version)
                            }
                        }
                    }
                )

        } catch (e: Exception) {
            log.error(e) { "error when importing channels" }
            return ChannelsImportResult.Failure.Generic(e)
        }
    }
}

sealed class ChannelsImportResult {
    data class Success(val channel: PersistedChannelState) : ChannelsImportResult()
    sealed class Failure : ChannelsImportResult() {
        data class Generic(val error: Throwable) : Failure()
        data class UnknownVersion(val version: Int) : Failure()
        object MalformedData : Failure()
        object DecryptionError : Failure()
    }
}