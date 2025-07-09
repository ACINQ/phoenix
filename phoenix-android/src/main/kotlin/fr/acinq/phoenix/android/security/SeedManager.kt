/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.security

import android.content.Context
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.android.security.EncryptedSeed.Companion.toMnemonics
import fr.acinq.phoenix.android.security.EncryptedSeed.Companion.toMnemonicsSafe
import fr.acinq.phoenix.managers.NodeParamsManager
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStoreException

sealed class SeedFileState {
    fun isReady() = this is Present

    data object Unknown : SeedFileState()
    data object Absent : SeedFileState()
    data class Present(internal val encryptedSeed: EncryptedSeed.V2) : SeedFileState()
    sealed class Error : SeedFileState() {
        data class Unreadable(val message: String?) : Error()
    }
}

sealed class DecryptSeedResult {

    sealed class Success: DecryptSeedResult() {
        abstract val mnemonics: List<String>
        data class Nominal(override val mnemonics: List<String>): Success()
        /**
         * Edge case when you want to load a specific node id, but it's not available (e.g., the preference tracking the expected node
         * id is not in sync with the actual data). In this case we return the mnemonics and the node id of the first available seed
         * that could be found. This scenario may actually be interpreted as an error in the UI.
         */
        data class Unexpected(override val mnemonics: List<String>, val expectedNodeId: String, val actualNodeId: String): Success()
    }

    sealed class Failure: DecryptSeedResult() {
        data object SeedNotFound: Failure()
        data class KeyStoreFailure(val cause: KeyStoreException): Failure()
        data class DecryptionError(val cause: Exception): Failure()
        data object SeedFileUnreadable: Failure()
        data object SeedInvalid: Failure()
    }
}

object SeedManager {
    private val BASE_DATADIR = "node-data"
    private const val SEED_FILE = "seed.dat"
    private val log = LoggerFactory.getLogger(this::class.java)

    fun getDatadir(context: Context): File {
        return File(context.filesDir, BASE_DATADIR)
    }

    /**
     * Decrypts the first available seed from the application folder.
     * If [expectedNodeId] is set, will try to return the seed that matches.
     */
    fun loadAndDecryptSeed(context: Context, expectedNodeId: String?): DecryptSeedResult {
        val encryptedSeed = try {
            loadEncryptedSeedFromDisk(context)
        } catch (e: Exception) {
            log.error("could read seed file: ", e)
            return DecryptSeedResult.Failure.SeedFileUnreadable
        }

        when (encryptedSeed) {
            is EncryptedSeed.V2.SingleSeed -> {
                log.info("decrypting [V2.SingleSeed]...")
                val payload = try {
                    encryptedSeed.decrypt()
                } catch (e: Exception) {
                    log.error("failed to decrypt [V2.SingleSeed]: ", e)
                    return when (e) {
                        is KeyStoreException -> DecryptSeedResult.Failure.KeyStoreFailure(e)
                        else -> DecryptSeedResult.Failure.DecryptionError(e)
                    }
                }

                val words = toMnemonicsSafe(payload) ?: return DecryptSeedResult.Failure.SeedInvalid

                return if (expectedNodeId.isNullOrBlank()) {
                    DecryptSeedResult.Success.Nominal(mnemonics = words)
                } else {
                    val seed = MnemonicCode.toSeed(words, "").toByteVector()
                    val keyManager = LocalKeyManager(seed, NodeParamsManager.chain, NodeParamsManager.remoteSwapInXpub)
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey.toHex()
                    if (nodeId != expectedNodeId) {
                        DecryptSeedResult.Success.Unexpected(mnemonics = words, expectedNodeId = expectedNodeId, actualNodeId = nodeId)
                    } else {
                        DecryptSeedResult.Success.Nominal(mnemonics = words)
                    }
                }
            }
            is EncryptedSeed.V2.MultipleSeed -> {
                log.info("decrypting [V2.MultipleSeed]")
                val seedMap = try {
                    encryptedSeed.decryptAndGetSeedMap()
                } catch (e: Exception) {
                    log.error("failed to decrypt [V2.MultipleSeed]: ", e)
                    return when (e) {
                        is KeyStoreException -> DecryptSeedResult.Failure.KeyStoreFailure(e)
                        else -> DecryptSeedResult.Failure.DecryptionError(e)
                    }
                }

                return when {
                    seedMap.isEmpty() -> DecryptSeedResult.Failure.SeedNotFound
                    expectedNodeId.isNullOrBlank() -> {
                        log.debug("loading first available seed in map")
                        toMnemonicsSafe(seedMap.values.first())?.let { DecryptSeedResult.Success.Nominal(it) }
                            ?: DecryptSeedResult.Failure.SeedInvalid
                    }
                    else -> {
                        return when (val match = seedMap[expectedNodeId]) {
                            null -> {
                                val (nodeId, payload) = seedMap.entries.first()
                                toMnemonicsSafe(payload)?.let { DecryptSeedResult.Success.Unexpected(mnemonics = it, expectedNodeId = expectedNodeId, actualNodeId = nodeId) }
                                    ?: DecryptSeedResult.Failure.SeedInvalid
                            }
                            else -> {
                                toMnemonicsSafe(match)?.let { DecryptSeedResult.Success.Nominal(it) }
                                    ?: DecryptSeedResult.Failure.SeedInvalid
                            }
                        }
                    }
                }
            }
            null -> return DecryptSeedResult.Failure.SeedNotFound
        }
    }

    fun loadAndDecryptAll(context: Context): Map<String, List<String>>? {
        val encryptedSeed = try {
            loadEncryptedSeedFromDisk(context)
        } catch (e: Exception) {
            log.error("could read seed file: ", e)
            return null
        }

        return when (encryptedSeed) {
            is EncryptedSeed.V2.SingleSeed -> {
                log.info("(all) decrypting [V2.SingleSeed]...")
                return try {
                    val words = toMnemonics(encryptedSeed.decrypt())
                    val seed = MnemonicCode.toSeed(words, "").toByteVector()
                    val keyManager = LocalKeyManager(seed, NodeParamsManager.chain, NodeParamsManager.remoteSwapInXpub)
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey.toHex()
                    mapOf(nodeId to words)
                } catch (e: Exception) {
                    log.error("failed to decrypt [V2.SingleSeed]: ", e)
                    null
                }
            }
            is EncryptedSeed.V2.MultipleSeed -> {
                log.info("(all) decrypting [V2.MultipleSeed]...")
                try {
                    encryptedSeed.decryptAndGetSeedMap().map { (nodeId, seed) -> nodeId to toMnemonics(seed) }.toMap()
                } catch (e: Exception) {
                    log.error("failed to decrypt [V2.MultipleSeed]: ", e)
                    null
                }
            }
            null -> null
        }
    }

    /** Gets the encrypted seed from app private dir. */
    fun loadEncryptedSeedFromDisk(context: Context): EncryptedSeed? = loadSeedFromDir(getDatadir(context), SEED_FILE)

    fun getSeedState(context: Context): SeedFileState = TODO("seed state should be handled in views...")
//        try {
//        when (val seed = loadEncryptedSeedFromDisk(context)) {
//            null -> SeedFileState.Absent
//            is EncryptedSeed.V2.SingleSeed -> SeedFileState.Present(seed)
//            is EncryptedSeed.V2.MultipleSeed -> SeedFileState.Present(seed)
//        }
//    } catch (e: Exception) {
//        log.error("failed to read seed: ", e)
//        SeedFileState.Error.Unreadable(e.localizedMessage)
//    }

    /** Extracts an encrypted seed contained in a given file/folder. */
    private fun loadSeedFromDir(dir: File, seedFileName: String): EncryptedSeed? {
        val seedFile = File(dir, seedFileName)
        return if (!seedFile.exists()) {
            null
        } else if (!seedFile.canRead()) {
            throw UnreadableSeed("file is unreadable")
        } else if (!seedFile.isFile) {
            throw UnreadableSeed("not a file")
        } else {
            seedFile.readBytes().let {
                if (it.isEmpty()) {
                    throw UnreadableSeed("empty file!")
                } else {
                    EncryptedSeed.deserialize(it)
                }
            }
        }
    }

    fun writeSeedToDisk(context: Context, seed: EncryptedSeed.V2, overwrite: Boolean = false) = writeSeedToDir(getDatadir(context), seed, overwrite)

    private fun writeSeedToDir(dir: File, seed: EncryptedSeed.V2, overwrite: Boolean) {
        // 1 - create dir
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // 2 - encrypt and write in a temporary file
        val temp = File(dir, "temporary_seed.dat")
        temp.writeBytes(seed.serialize())

        // 3 - decrypt temp file and check validity; if correct, move temp file to final file
        val checkSeed = loadSeedFromDir(dir, temp.name) as EncryptedSeed.V2
        if (!checkSeed.ciphertext.contentEquals(seed.ciphertext)) {
            log.warn("seed check do not match!")
//            throw WriteErrorCheckDontMatch
        }
        temp.copyTo(File(dir, SEED_FILE), overwrite)
        temp.delete()
    }

    object WriteErrorCheckDontMatch : RuntimeException("failed to write the seed to disk: temporary file do not match")
    class UnreadableSeed(msg: String) : RuntimeException(msg)
}