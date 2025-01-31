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

package fr.acinq.phoenix.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.acinq.phoenix.utils.PlatformContext
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.collections.List
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
actual abstract class UsingContextTest {

    actual fun setUpDatabase(context: PlatformContext, databasePaths: List<Path>) {
        val dbDir = context.applicationContext.getDatabasePath("test").toPath().parent
        Files.createDirectories(dbDir)
        databasePaths.map { it.toNioPath() }.forEach { source ->
            val dest = dbDir.resolve(source.name)
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    actual fun getPlatformContext(): PlatformContext = PlatformContext(ApplicationProvider.getApplicationContext())

    companion object {
        @AfterClass
        @JvmStatic
        fun copyMigratedFiles() {
            val destDir = "build/test-results/migration-v10-v11/android".toPath()
            Files.createDirectories(destDir.toNioPath())

            val dbDir = ApplicationProvider.getApplicationContext<Context>().applicationContext.getDatabasePath("test").toPath().parent
            dbDir.listDirectoryEntries().forEach { migratedFile ->
                val dest = destDir.resolve(migratedFile.name)
                Files.copy(migratedFile, dest.toNioPath(), StandardCopyOption.REPLACE_EXISTING)
                println("copied migrated dbfile=${migratedFile.name} to $destDir")
            }
        }
    }
}
