/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.utils.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.nodeIdHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/**
 * This utility class provides helps backing up the channels/payments database
 * in an encrypted zip file, stored on disk in a public folder of the device.
 *
 * The backup file is NOT removed when the app is uninstalled.
 */
object LocalBackupHelper {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** The backup file is stored in the Documents directory of Android. */
    private val backupDir = "${Environment.DIRECTORY_DOCUMENTS}/phoenix-backup"

    private suspend fun prepareBackupContent(context: Context): ByteArray {
        return withContext(Dispatchers.IO) {
            log.info("preparing data...")
            val business = (context as PhoenixApplication).business.filterNotNull().first()
            val nodeParams = business.nodeParamsManager.nodeParams.filterNotNull().first()
            val channelsDbFile = context.getDatabasePath(DatabaseManager.channelsDbName(nodeParams.chain, nodeParams.nodeId))
            val paymentsDbFile = context.getDatabasePath(DatabaseManager.paymentsDbName(nodeParams.chain, nodeParams.nodeId))

            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zos ->
                log.info("zipping channels db...")
                FileInputStream(channelsDbFile).use { fis ->
                    zos.putNextEntry(ZipEntry(channelsDbFile.name))
                    zos.write(fis.readBytes())
                }
                log.info("zipping payments db file...")
                FileInputStream(paymentsDbFile).use { fis ->
                    zos.putNextEntry(ZipEntry(paymentsDbFile.name))
                    zos.write(fis.readBytes())
                }
            }

            bos.toByteArray()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createBackupFileUri(context: Context, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, backupDir)
        }
        return context.contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), values)
            ?: throw RuntimeException("failed to insert uri record for backup file")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getBackupFileUri(context: Context, fileName: String): Pair<Long, Uri>? {
        val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // columns to return -- we want the name & modified timestamp
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        // filter on the file's name
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME}"
        val selectionArgs = arrayOf(fileName)
        val resolver = context.contentResolver

        return resolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            null, null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val modifiedAtColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (cursor.moveToNext()) {
                val fileId = cursor.getLong(idColumn)
                val actualFileName = cursor.getString(nameColumn)
                val modifiedAt = cursor.getLong(modifiedAtColumn) * 1000
                log.info("found backup file with name=$actualFileName modified_at=${modifiedAt.toAbsoluteDateTimeString()}")
                modifiedAt to ContentUris.withAppendedId(contentUri, fileId)
            } else {
                log.info("no backup file found for name=$fileName")
                null
            }
        }
    }

    private fun getBackupFileName(keyManager: LocalKeyManager): String {
        return "phoenix-${keyManager.chain.name.lowercase()}-${keyManager.nodeIdHash().take(7)}.bak"
    }

    /**
     * Write an encrypted zip file of the payments/channels database, and store it in a public folder of the device.
     * Since we're using the media store API for that, no permission is required.
     */
    suspend fun saveBackupToDisk(context: Context, keyManager: LocalKeyManager) {
        val encryptedBackup = try {
            val data = prepareBackupContent(context)
            EncryptedBackup.encrypt(
                version = EncryptedBackup.Version.V1,
                data = data,
                keyManager = keyManager
            )
        } catch (e: Exception) {
            throw RuntimeException("failed to encrypt backup file", e)
        }

        val fileName = getBackupFileName(keyManager)

        log.debug("saving encrypted backup to private dir...")
        val internalBackup = File(context.filesDir, "phoenix-backup")
        val internalBackupFile = File(internalBackup, fileName)
        internalBackupFile.writeBytes(encryptedBackup.write())
        log.debug("encrypted backup successfully saved to private dir")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            log.debug("saving encrypted backup to public dir through mediastore api...")
            val resolver = context.contentResolver

            val uri = getBackupFileUri(context, fileName)?.second ?: createBackupFileUri(context, fileName)
            resolver.openOutputStream(uri, "w")?.use { outputStream ->
                val array = encryptedBackup.write()
                outputStream.write(array)
                log.debug("encrypted backup successfully saved to public dir ($uri)")
            } ?: run {
                log.error("public backup failed: cannot open output stream for uri=$uri")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getBackupData(context: Context, keyManager: LocalKeyManager): EncryptedBackup? {
        val (_, uri) = getBackupFileUri(context, getBackupFileName(keyManager)) ?: return null
        val resolver = context.contentResolver
        val data = resolver.openInputStream(uri).use {
            it!!.readBytes()
        }
        return EncryptedBackup.read(data)
    }

    fun resolveUriContent(context: Context, uri: Uri): EncryptedBackup? {
        val resolver = context.contentResolver
        val data = resolver.openInputStream(uri)?.use {
            it.readBytes()
        }
        return data?.let { EncryptedBackup.read(it) }
    }

    /** Extracts files from zip - folders are unhandled. */
    fun unzipData(data: ByteVector): Map<String, ByteArray> {
        ByteArrayInputStream(data.toByteArray()).use { bis ->
            ZipInputStream(bis).use { zis ->
                val files = mutableMapOf<String, ByteArray>()
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    ByteArrayOutputStream().use { bos ->
                        bos.write(zis.readBytes())
                        files.put(zipEntry.name, bos.toByteArray())
                    }
                    zipEntry = zis.nextEntry
                }
                return files.toMap()
            }
        }
    }

    /** Restore a database file to the app's database folder. If restoring a channels database, [canOverwrite] should ALWAYS be false. */
    fun restoreDbFile(context: Context, fileName: String, fileData: ByteArray, canOverwrite: Boolean = false) {
        val dbFile = context.getDatabasePath(fileName)
        if (dbFile.exists() && !canOverwrite) {
            throw RuntimeException("cannot overwrite db file=$fileName")
        } else {
            FileOutputStream(dbFile, false).use { fos ->
                fos.write(fileData)
                fos.flush()
            }
        }
    }
}

data class EncryptedBackup(val version: Version, val iv: IvParameterSpec, val ciphertext: ByteVector) {

    enum class Version(val code: Byte, val algorithm: String) {
        V1(1, "AES/CBC/PKCS5PADDING")
    }

    fun decrypt(keyManager: LocalKeyManager): ByteVector {
        val key = getKeyForVersion(version, keyManager)
        val cipher = Cipher.getInstance(version.algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(ciphertext.toByteArray()).byteVector()
    }

    fun write(): ByteArray {
        return when (version) {
            Version.V1 -> {
                val bos = ByteArrayOutputStream()
                bos.write(version.code.toInt())
                bos.write(iv.iv)
                bos.write(ciphertext.toByteArray())
                bos.toByteArray()
            }
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)

        /** Return the encryption key for the given [version]. */
        fun getKeyForVersion(version: Version, keyManager: LocalKeyManager): SecretKey {
            when (version) {
                Version.V1 -> {
                    val key = keyManager.privateKey(KeyPath("m/150'/1'/0'")).value.toByteArray()
                    return SecretKeySpec(key, 0, 32, "AES")
                }
            }
        }

        /**
         * Encrypt [data] using a key from [keyManager]. The key used depends on [version].
         * @return an [EncryptedBackup] object, containing the encrypted payload, the version and the IV.
         */
        fun encrypt(version: Version = Version.V1, data: ByteArray, keyManager: LocalKeyManager): EncryptedBackup {
            if (version == Version.V1) {
                val key = getKeyForVersion(version, keyManager)
                val cipher = Cipher.getInstance(version.algorithm)

                cipher.init(Cipher.ENCRYPT_MODE, key)
                val ciphertext = cipher.doFinal(data).byteVector()

                return EncryptedBackup(version, iv = IvParameterSpec(cipher.iv), ciphertext = ciphertext)
            } else {
                throw RuntimeException("unhandled version=$version")
            }
        }

        /** Extract an [EncryptedBackup] object from a blob. Throw if object is invalid. */
        fun read(data: ByteArray): EncryptedBackup? {
            return ByteArrayInputStream(data).use { bis ->
                when (val version = bis.read().toByte()) {
                    Version.V1.code -> {
                        val iv = ByteArray(16)
                        bis.read(iv, 0, 16)
                        val remainingBytes = bis.available()
                        val ciphertext = ByteArray(remainingBytes)
                        bis.read(ciphertext, 0, remainingBytes)
                        EncryptedBackup(version = Version.V1, iv = IvParameterSpec(iv), ciphertext = ciphertext.byteVector())
                    }

                    else -> {
                        throw RuntimeException("unhandled version=$version")
                    }
                }
            }
        }
    }
}
