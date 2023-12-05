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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import fr.acinq.phoenix.android.PhoenixApplication
import org.kodein.log.frontend.slf4jFrontend
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun logger(name: String? = null): org.kodein.log.Logger {
    val context = LocalContext.current
    val application = context.applicationContext
    val tag = name?.let { org.kodein.log.Logger.Tag(BuildConfig.APPLICATION_ID, it) } ?: org.kodein.log.Logger.Tag(context::class)

    return if (application !is PhoenixApplication) { // Preview mode
        remember(tag) { org.kodein.log.LoggerFactory(slf4jFrontend).newLogger(tag) }
    } else {
        val businessState = application.business.collectAsState()
        when (val business = businessState.value) {
            null -> remember(tag) { org.kodein.log.LoggerFactory(slf4jFrontend).newLogger(tag) }
            else -> remember(tag) { business.loggerFactory.newLogger(tag) }
        }
    }
}

object Logging {

    private const val LOGS_DIR = "logs"
    private const val CURRENT_LOG_FILE = "phoenix.log"
    private const val ARCHIVED_LOG_FILE = "phoenix.archive-%i.log"

    fun exportLogFile(context: Context): File {
        val export = File(File(context.filesDir, LOGS_DIR), "phoenix_export.log")
        val exportOutputStream = FileOutputStream(export)
        val exportChannel = exportOutputStream.channel

        // write archive-1 to export file, if available
        File(File(context.filesDir, LOGS_DIR), "phoenix.archive-1.log").takeIf {
            it.exists() && it.isFile && it.canRead()
        }?.let {
            FileInputStream(it)
        }?.also {
            val channel = it.channel
            channel.transferTo(0, channel.size(), exportChannel)
            channel.close()
        }?.close()

        // write current log file to export file, if available
        File(File(context.filesDir, LOGS_DIR), CURRENT_LOG_FILE).takeIf {
            it.exists() && it.isFile && it.canRead()
        }?.let {
            FileInputStream(it)
        }?.also {
            val channel = it.channel
            channel.transferTo(0, channel.size(), exportChannel)
            channel.close()
        }?.close()

        exportChannel.close()
        exportOutputStream.close()
        return export
    }

    fun setupLogger(context: Context) {
        // cleanup export file
        val export = File(File(context.filesDir, LOGS_DIR), "phoenix_export.log")
        if (export.exists() && export.isFile) {
            try {
                export.delete()
            } catch (_: Exception) {}
        }
        // cleanup unused archive-2 file
        val archive2 = File(File(context.filesDir, LOGS_DIR), "phoenix.archive-2.log")
        if (archive2.exists() && archive2.isFile) {
            try {
                archive2.delete()
            } catch (_: Exception) {}
        }

        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.reset()

        val localFileAppender = getLocalFileAppender(context, lc)

        // set level
        lc.getLogger("fr.acinq.lightning").level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
        lc.getLogger("fr.acinq.phoenix").level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO

        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        root.level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
        root.addAppender(localFileAppender)

        if (BuildConfig.DEBUG) {
            val logcatAppender = getLogcatAppender(lc)
            root.addAppender(logcatAppender)
        }
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
        rollingPolicy.maxIndex = 1
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
