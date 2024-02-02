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

package fr.acinq.phoenix.utils.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Severity.Assert
import co.touchlab.kermit.Severity.Debug
import co.touchlab.kermit.Severity.Error
import co.touchlab.kermit.Severity.Info
import co.touchlab.kermit.Severity.Verbose
import co.touchlab.kermit.Severity.Warn
import fr.acinq.phoenix.utils.PlatformContext
import org.slf4j.LoggerFactory


/**
 * Use SLF4J writer on Android. Note that writing logs to Logcat is already done in
 * phoenix-android SLF4J configuration.
 */
actual fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter> = listOf(Slf4jLogWriter())

class Slf4jLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val logger = LoggerFactory.getLogger(tag)
        when (severity) {
            Verbose -> logger.trace(message, throwable)
            Debug -> logger.debug(message, throwable)
            Info -> logger.info(message, throwable)
            Warn -> logger.warn(message, throwable)
            Error -> logger.error(message, throwable)
            Assert -> logger.error(message, throwable)
        }
    }
}
