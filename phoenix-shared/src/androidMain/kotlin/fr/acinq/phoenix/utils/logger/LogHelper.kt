package fr.acinq.phoenix.utils.logger

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
import fr.acinq.bitcoin.Chain
import fr.acinq.phoenix.managers.NodeParamsManager
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object LogHelper {
    private val LOGS_DIR = "logs"

    private val logLevel = if (NodeParamsManager.chain is Chain.Mainnet) Level.INFO else Level.DEBUG
    private val addLogcatLogs = NodeParamsManager.chain !is Chain.Mainnet

    /** Sets up the default SLF4J logger that will append log to phoenix.log, and maybe logcat. */
    fun setupDefaultLogger(context: Context) {
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.reset()

        val localFileAppender = LogHelper.getFileAppender(context, lc, walletId = null)

        // set level
        lc.getLogger("fr.acinq.lightning").level = logLevel
        lc.getLogger("fr.acinq.phoenix").level = logLevel

        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        root.level = logLevel
        root.addAppender(localFileAppender)

        if (addLogcatLogs) {
            val logcatAppender = LogHelper.getLogcatAppender(lc)
            root.addAppender(logcatAppender)
        }
    }

    /** Returns a logger for the given tag, that appends logs to the [phoenix-walletid.log] file, and maybe logcat. */
    fun getLogger(context: Context, walletId: String, tag: String): org.slf4j.Logger {
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.reset()

        val localFileAppender = getFileAppender(context, lc, walletId)

        // set level
        lc.getLogger("fr.acinq.lightning").level = logLevel
        lc.getLogger("fr.acinq.phoenix").level = logLevel

        val logger = LoggerFactory.getLogger(tag /*Logger.ROOT_LOGGER_NAME*/) as Logger
        logger.level = logLevel
        logger.addAppender(localFileAppender)

        if (addLogcatLogs) {
            val logcatAppender = getLogcatAppender(lc)
            logger.addAppender(logcatAppender)
        }
        return logger
    }

    @OptIn(ExperimentalTime::class)
    fun exportLogFile(context: Context, walletId: String?): File {

        val exportDir = File(context.cacheDir, LOGS_DIR)
        if (!exportDir.exists()) exportDir.mkdirs()

        val datetime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val zipFile = File(exportDir, "phoenix-${datetime.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.zip")

        val logFiles = listOf(getCurrentLogFile(context, null), getArchiveLogFileExact(context, null)) + (walletId?.let {
            listOf(getCurrentLogFile(context, it), getArchiveLogFileExact(context, it))
        } ?: emptyList())

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            logFiles.forEach { file ->
                if (file.exists() && file.isFile) {
                    FileInputStream(file).use { fis ->
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos, bufferSize = 1024)
                        zos.closeEntry()
                    }
                }
            }
        }

        return zipFile
    }

    fun getCurrentLogFile(context: Context, walletId: String?) = File(File(context.filesDir, LOGS_DIR),
        if (walletId == null) "phoenix-base.log" else "phoenix-${walletId.take(10)}.log")

    private fun getArchiveLogFilePattern(context: Context, walletId: String?) = File(File(context.filesDir, LOGS_DIR),
        if (walletId == null) "phoenix-base.archive-%i.log" else "phoenix-${walletId.take(10)}.archive-%i.log")

    private fun getArchiveLogFileExact(context: Context, walletId: String?) = File(File(context.filesDir, LOGS_DIR),
        if (walletId == null) "phoenix-base.archive-%i.log" else "phoenix-${walletId.take(10)}.archive-1.log")

    private fun getFileAppender(context: Context, lc: LoggerContext, walletId: String?): Appender<ILoggingEvent> {

        val logsDir = File(context.filesDir, LOGS_DIR)
        if (!logsDir.exists()) logsDir.mkdirs()

        val encoder = PatternLayoutEncoder()
        encoder.context = lc
        encoder.pattern = "%d %-5level %logger{24} %X{nodeId}%X{channelId} - %msg%ex{24}%n"
        encoder.start()

        val appender = RollingFileAppender<ILoggingEvent>()
        appender.context = lc
        appender.file = getCurrentLogFile(context, walletId).absolutePath

        val rollingPolicy = FixedWindowRollingPolicy()
        rollingPolicy.context = lc
        rollingPolicy.setParent(appender)
        rollingPolicy.minIndex = 1
        rollingPolicy.maxIndex = 1
        rollingPolicy.fileNamePattern = getArchiveLogFilePattern(context, walletId).absolutePath
        rollingPolicy.start()

        val triggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>()
        triggeringPolicy.context = lc
        triggeringPolicy.maxFileSize = FileSize.valueOf("6mb")
        triggeringPolicy.start()

        appender.encoder = encoder
        appender.rollingPolicy = rollingPolicy
        appender.triggeringPolicy = triggeringPolicy
        appender.start()

        return appender
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
}