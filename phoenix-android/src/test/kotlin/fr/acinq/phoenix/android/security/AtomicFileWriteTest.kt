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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AtomicFileWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `write a new file`() {
        val target = File(folder.root, "seed.dat")
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
        writeFileAtomically(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(temp.exists())
    }

    @Test
    fun `replace an existing file`() {
        val target = File(folder.root, "seed.dat").apply { writeBytes(byteArrayOf(9, 9, 9, 9, 9, 9)) }
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
        writeFileAtomically(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(temp.exists())
    }

    @Test
    fun `overwrite a stale temporary file`() {
        val target = File(folder.root, "seed.dat").apply { writeBytes(byteArrayOf(9)) }
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp").apply { writeBytes(ByteArray(1000) { 7 }) }
        writeFileAtomically(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(temp.exists())
    }

    @Test
    fun `existing file is untouched when the temporary file cannot be written`() {
        val target = File(folder.root, "seed.dat").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        // a directory cannot be opened as a file, so writing the temporary file fails
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp").apply { mkdir() }
        try {
            writeFileAtomically(target, byteArrayOf(1, 2, 3))
            fail("write should have failed")
        } catch (e: IOException) {
            // expected
        }
        assertArrayEquals(byteArrayOf(9, 9, 9), target.readBytes())
    }

    @Test
    fun `temporary file is deleted when the rename fails`() {
        // a non-empty directory cannot be replaced by a file, so the rename fails after the temporary file is written
        val target = File(folder.root, "seed.dat").apply { mkdir() }
        File(target, "child").writeBytes(byteArrayOf(9))
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
        try {
            writeFileAtomically(target, byteArrayOf(1, 2, 3))
            fail("write should have failed")
        } catch (e: IOException) {
            // expected
        }
        assertFalse(temp.exists())
        assertArrayEquals(byteArrayOf(9), File(target, "child").readBytes())
    }
}
