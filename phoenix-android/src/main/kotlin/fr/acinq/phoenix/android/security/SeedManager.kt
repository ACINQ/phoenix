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
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.managers.NodeParamsManager
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStoreException

sealed class DecryptSeedResult {
    /** @param userWalletsMap empty if there is no seed file yet, or if it contains no wallets. */
    data class Success(val userWalletsMap: Map<WalletId, UserWallet>): DecryptSeedResult()
    sealed class Failure: DecryptSeedResult() {
        data object SerializationError: Failure()
        data class TransientKeyStoreFailure(val cause: Exception): Failure()
        data class KeyStoreFailure(val cause: Exception): Failure()
        data class DecryptionError(val cause: Exception): Failure()
        data object SeedFileUnreadable: Failure()
        data object SeedInvalid: Failure()
        /** The legacy single seed was decrypted, but its pin codes could not be migrated to the multi-wallet format. */
        data class PinMigrationFailed(val cause: Exception): Failure()
    }
}

object SeedManager {
    private const val BASE_DATADIR = "node-data"
    private const val SEED_FILE = "seed.dat"
    private val log = LoggerFactory.getLogger(this::class.java)

    fun getDatadir(context: Context): File {
        return File(context.filesDir, BASE_DATADIR)
    }

    @Suppress("DEPRECATION")
    fun loadAndDecrypt(context: Context): DecryptSeedResult {
        val encryptedSeed = try {
            loadEncryptedSeedFromDisk(context)
        } catch (e: Exception) {
            return when {
                KeystoreHelper.isTransientFailure(e) -> {
                    log.error("transient keystore error when reading seed file: ", e)
                    DecryptSeedResult.Failure.TransientKeyStoreFailure(e)
                }
                KeystoreHelper.isKeystoreException(e) -> {
                    log.error("keystore error when reading seed file: ", e)
                    DecryptSeedResult.Failure.KeyStoreFailure(e)
                }
                else -> {
                    log.error("could not read seed file: ", e)
                    DecryptSeedResult.Failure.DecryptionError(e)
                }
            }
        }

        return when (encryptedSeed) {
            is EncryptedSeed.V2.SingleSeed -> {
                log.info("decrypting [V2.SingleSeed]...")
                val payload = try {
                    encryptedSeed.decrypt()
                } catch (e: Exception) {
                    log.error("failed to decrypt [V2.SingleSeed]: ", e)
                    return when {
                        KeystoreHelper.isTransientFailure(e) -> DecryptSeedResult.Failure.TransientKeyStoreFailure(e)
                        KeystoreHelper.isKeystoreException(e) -> DecryptSeedResult.Failure.KeyStoreFailure(e)
                        else -> DecryptSeedResult.Failure.DecryptionError(e)
                    }
                }
                val words = EncryptedSeed.V2.SingleSeed.toMnemonicsSafe(payload) ?: return DecryptSeedResult.Failure.SeedInvalid

                val seed = MnemonicCode.toSeed(words, "").toByteVector()
                val keyManager = LocalKeyManager(seed, NodeParamsManager.chain, NodeParamsManager.remoteSwapInXpub)
                val nodeId = keyManager.nodeKeys.nodeKey.publicKey
                val walletId = WalletId(nodeId)

                DataStoreManager.migratePrefsForWallet(context, walletId)
                try {
                    PinManager.migrateSingleWalletPinCode(context, walletId)
                } catch (e: Exception) {
                    // the wallet must not be loaded with legacy pin files, which the pin checks cannot read
                    log.error("failed to migrate pin codes for wallet=$walletId: ", e)
                    return DecryptSeedResult.Failure.PinMigrationFailed(e)
                }

                DecryptSeedResult.Success(userWalletsMap = mapOf(walletId to UserWallet(walletId, nodeId.toHex(), words)))
            }

            is EncryptedSeed.V2.MultipleSeed -> {
                log.info("decrypting [V2.MultipleSeed]")
                val seedMap = try {
                    encryptedSeed.decryptAndGetSeedMap()
                } catch (e: Exception) {
                    return when {
                        e is SerializationException || e is IllegalArgumentException -> {
                            log.error("failed to decrypt [V2.MultipleSeed]: ${e.javaClass.simpleName}")
                            DecryptSeedResult.Failure.SerializationError
                        }
                        KeystoreHelper.isTransientFailure(e) -> {
                            log.error("failed to decrypt [V2.MultipleSeed] (transient): ", e)
                            DecryptSeedResult.Failure.TransientKeyStoreFailure(e)
                        }
                        e is KeyStoreException -> {
                            log.error("failed to decrypt [V2.MultipleSeed]: ", e)
                            DecryptSeedResult.Failure.KeyStoreFailure(e)
                        }
                        else -> {
                            log.error("failed to decrypt [V2.MultipleSeed]: ", e)
                            DecryptSeedResult.Failure.DecryptionError(e)
                        }
                    }
                }

                if (seedMap.isEmpty()) {
                    log.debug("seed file contains no wallets")
                }
                seedMap.map { (walletId, words) ->
                    val seed = MnemonicCode.toSeed(words, "").toByteVector()
                    val keyManager = LocalKeyManager(seed, NodeParamsManager.chain, NodeParamsManager.remoteSwapInXpub)
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey
                    walletId to UserWallet(walletId, nodeId.toHex(), words)
                }.toMap().let {
                    DecryptSeedResult.Success(it)
                }
            }

            null -> {
                log.debug("no seed file found")
                DecryptSeedResult.Success(emptyMap())
            }
        }
    }

    /**
     * Wrapper method for [loadAndDecrypt].
     * Returns an empty map if the seed file does not exist yet, or contains no wallets.
     * Returns null if there was a problem when loading or decrypting the seed file.
     */
    suspend fun loadAndDecryptOrNull(context: Context): Map<WalletId, UserWallet>? = when (val res = loadAndDecrypt(context)) {
        is DecryptSeedResult.Success -> res.userWalletsMap
        is DecryptSeedResult.Failure -> null
    }

    /** Gets the encrypted seed from app private dir. */
    fun loadEncryptedSeedFromDisk(context: Context): EncryptedSeed? = loadSeedFromDir(getDatadir(context), SEED_FILE)

    /** Extracts an encrypted seed contained in a given file/folder. Returns null if the file does not exist. */
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

    @Synchronized
    fun writeSeedToDisk(context: Context, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean = false) = writeSeedToDir(getDatadir(context), seed, overwrite)

    /**
     * Writes the seed file atomically: the seed file will contain either its previous content or the new one, even if the
     * process dies during the write. If the data cannot be written or checked, an exception is thrown and the existing
     * seed file is left untouched.
     */
    private fun writeSeedToDir(dir: File, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val seedFile = File(dir, SEED_FILE)
        if (!overwrite && seedFile.exists()) {
            throw FileAlreadyExistsException(seedFile)
        }
        writeFileAtomically(target = seedFile, bytes = seed.serialize())
    }

    class UnreadableSeed(msg: String) : RuntimeException(msg)
}