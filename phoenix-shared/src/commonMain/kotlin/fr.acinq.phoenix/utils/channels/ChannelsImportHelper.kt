package fr.acinq.phoenix.utils.channels

import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.serialization.Encryption.from
import fr.acinq.lightning.serialization.Serialization
import fr.acinq.lightning.wire.EncryptedChannelData
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.lightning.logging.error
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first


object ChannelsImportHelper {

    suspend fun doImportChannels(
        data: String,
        biz: PhoenixBusiness,
    ): ChannelsImportResult {

        try {
            val loggerFactory = biz.loggerFactory
            val log = loggerFactory.newLogger(this::class)

            val nodeParams = biz.nodeParamsManager.nodeParams.filterNotNull().first()
            val peer = biz.peerManager.getPeer()

            val deserializedData = try {
                EncryptedChannelData(ByteVector(Hex.decode(data)))
            } catch(e: Exception) {
                log.error(e) { "failed to deserialize data blob" }
                return ChannelsImportResult.Failure.MalformedData
            }

            return PersistedChannelState
                .from(nodeParams.nodePrivateKey, deserializedData)
                .fold(
                    onFailure = {
                        log.error(it) { "failed to decrypt channel state" }
                        ChannelsImportResult.Failure.DecryptionError
                    },
                    onSuccess = {
                        when (it) {
                            is Serialization.DeserializationResult.Success -> {
                                peer.db.channels.addOrUpdateChannel(it.state)
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