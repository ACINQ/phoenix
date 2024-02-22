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
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import fr.acinq.phoenix.utils.PlatformContext

/**
 * Contains a logging configuration.
 * Not an object, because the platform context is required for the log writers, which are platform dependent.
 *
 * Would have used [StaticConfig] but it cannot be extended.
 */
data class PhoenixLoggerConfig(private val platformContext: PlatformContext): LoggerConfig {
    override val logWriterList: List<LogWriter> = phoenixLogWriters(platformContext)
    override val minSeverity: Severity = Severity.Info
}

/**
 * Factory function to return a default list of LogWriters for each platform. The LogWriter is targeted at local development.
 * For production implementations, you may need to directly initialize your Logger config.
 */
expect fun phoenixLogWriters(ctx: PlatformContext): List<LogWriter>