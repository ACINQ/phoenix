/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.content.Context
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import fr.acinq.phoenix.android.BuildConfig
import org.slf4j.LoggerFactory
import java.io.File

object Logging {

  const val LOGS_DIR = "logs"
  const val CURRENT_LOG_FILE = "phoenix.log"
  const val ARCHIVED_LOG_FILE = "phoenix.archive-%i.log"

  fun getLastLogFile(context: Context): File {
    return File(File(context.filesDir, LOGS_DIR), CURRENT_LOG_FILE)
  }

  fun setupLogger(context: Context) {
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.reset()

    val logcatAppender = getLogcatAppender(lc)
    val localFileAppender = getLocalFileAppender(context, lc)

    // set level
    lc.getLogger("fr.acinq.lightning").level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    lc.getLogger("fr.acinq.phoenix").level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    lc.getLogger("fr.acinq.lightning.crypto").level = Level.WARN // ChaCha20Poly1305 spams a lot in debug
    lc.getLogger("fr.acinq.lightning.db.BackupHandler").level = Level.WARN
    lc.getLogger("io.netty").level = if (BuildConfig.DEBUG) Level.INFO else Level.WARN

    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    root.addAppender(logcatAppender)
    root.addAppender(localFileAppender)
  }

  private fun getLogcatAppender(lc: LoggerContext): Appender<ILoggingEvent> {
    val tagEncoder = PatternLayoutEncoder()
    tagEncoder.context = lc
    tagEncoder.pattern = "%logger{12}"
    tagEncoder.start()

    val encoder = PatternLayoutEncoder()
    encoder.context = lc
    encoder.pattern = "%X{nodeId}%X{channelId} - %msg%ex{24}%n"
    encoder.start()

    val appender = LogcatAppender()
    appender.context = lc
    appender.encoder = encoder
    appender.tagEncoder = tagEncoder
    appender.start()
    return appender
  }

  private fun getLocalFileAppender(context: Context, lc: LoggerContext): Appender<ILoggingEvent> {

    val logsDir = File(context.filesDir, LOGS_DIR)
    if (!logsDir.exists()) logsDir.mkdirs()

    val encoder = PatternLayoutEncoder()
    encoder.context = lc
    encoder.pattern = "%d %-5level %logger{24} %X{nodeId}%X{channelId} - %msg%ex{24}%n"
    encoder.start()

    val appender = RollingFileAppender<ILoggingEvent>()
    appender.context = lc
    appender.file = File(logsDir, CURRENT_LOG_FILE).absolutePath

    val rollingPolicy = FixedWindowRollingPolicy()
    rollingPolicy.context = lc
    rollingPolicy.setParent(appender)
    rollingPolicy.minIndex = 1
    rollingPolicy.maxIndex = 2
    rollingPolicy.fileNamePattern = File(logsDir, ARCHIVED_LOG_FILE).absolutePath
    rollingPolicy.start()

    val triggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>()
    triggeringPolicy.context = lc
    triggeringPolicy.maxFileSize = FileSize.valueOf("8mb")
    triggeringPolicy.start()

    appender.encoder = encoder
    appender.rollingPolicy = rollingPolicy
    appender.triggeringPolicy = triggeringPolicy
    appender.start()

    return appender
  }


}
