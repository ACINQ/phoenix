/*
 * Copyright 2026 ACINQ SAS
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

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Replaces the content of [target] with [bytes], atomically.
 *
 * The data is first written and synced to a temporary file next to [target], read back and compared, then the temporary
 * file is renamed to [target]. If any step fails, the temporary file is deleted before the exception is rethrown.
 *
 * @throws IOException if the data could not be written, checked or renamed; [target] is then left untouched.
 */
internal fun writeFileAtomically(target: File, bytes: ByteArray) {
    val dir = target.absoluteFile.parentFile!!
    // temp file, in the same directory, so that the rename is atomic.
    val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")

    try {
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!temp.readBytes().contentEquals(bytes)) {
            throw IOException("content of ${temp.name} does not match the data written")
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
    syncDirectory(dir)
}

/**
 * Syncs the directory itself, so that a rename is persisted to storage. This is best effort: the rename is already
 * atomic, and a failure here does not undo it.
 */
private fun syncDirectory(dir: File) {
    try {
        FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
    } catch (e: Exception) {
        // ignored: not all platforms support syncing a directory
    }
}
