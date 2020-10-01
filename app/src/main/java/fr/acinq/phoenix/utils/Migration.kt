/*
 * Copyright 2020 ACINQ SAS
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



package fr.acinq.phoenix.utils

import android.content.Context
import fr.acinq.phoenix.BuildConfig
import org.slf4j.LoggerFactory

@Suppress("FunctionName")
object Migration {
  private val log = LoggerFactory.getLogger(this::class.java)

  val VERSIONS_WITH_NOTABLE_CHANGES = listOf(15)

  /** Apply migration scripts when needed. */
  fun doMigration(context: Context) {
    val version = Prefs.getLastVersionUsed(context)
    if (0 < version && version < BuildConfig.VERSION_CODE) {
      log.info("previously used version=$version, now using version=${BuildConfig.VERSION_CODE}, starting migration")
      when {
        version < 15 -> applyMigration_15(context)
      }
      log.info("end of migration from version=$version")
      Prefs.setMigratedFrom(context, version)
    } else {
      log.debug("previously used version=$version, no migration needed")
    }
    Prefs.setLastVersionUsed(context, BuildConfig.VERSION_CODE)
  }

  /** A patch note must be shown if the given version is below at least one version with a notable change*/
  fun listNotableChangesSince(version: Int): List<Int> {
    return if (version > 0) {
      VERSIONS_WITH_NOTABLE_CHANGES.filter { it > version }
    } else {
      emptyList()
    }
  }

  // fun applyMigration_XX(context: Context, fromVersion: Int) {
  //   ...
  //   MigrationPrefs.saveMigrationDone(context, fromVersion, XX)
  // }

  private fun applyMigration_15(context: Context) {
    Prefs.setAutoAcceptPayToOpen(context, true)
  }
}
