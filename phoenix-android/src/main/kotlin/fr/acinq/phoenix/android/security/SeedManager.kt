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
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import java.io.File

sealed class SeedFileState {
    fun isReady() = this is Present

    object Unknown : SeedFileState()
    object Absent : SeedFileState()
    data class Present(internal val encryptedSeed: EncryptedSeed.V2) : SeedFileState()
    sealed class Error : SeedFileState() {
        data class Unreadable(val message: String?) : Error()
        object UnhandledSeedType : Error()
    }
}

object SeedManager {
    private val BASE_DATADIR = "node-data"
    private const val SEED_FILE = "seed.dat"
    private val log = newLogger(LoggerFactory.default)

    fun getDatadir(context: Context): File {
        return File(context.filesDir, BASE_DATADIR)
    }

    /** Extract the encrypted seed from app private dir. */
    fun loadSeedFromDisk(context: Context): EncryptedSeed? = loadSeedFromDir(getDatadir(context), SEED_FILE)

    fun getSeedState(context: Context): SeedFileState = try {
        when (val seed = loadSeedFromDisk(context)) {
            null -> SeedFileState.Absent
            is EncryptedSeed.V2.NoAuth -> SeedFileState.Present(seed)
            else -> SeedFileState.Error.UnhandledSeedType
        }
    } catch (e: Exception) {
        log.error(e) { "failed to read seed: " }
        SeedFileState.Error.Unreadable(e.localizedMessage)
    }

    /** Extract an encrypted seed contained in a given file/folder. */
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
            log.warning { "seed check do not match!" }
//            throw WriteErrorCheckDontMatch
        }
        temp.copyTo(File(dir, SEED_FILE), overwrite)
        temp.delete()
    }

    object WriteErrorCheckDontMatch : RuntimeException("failed to write the seed to disk: temporary file do not match")
    class UnreadableSeed(msg: String) : RuntimeException(msg)
}