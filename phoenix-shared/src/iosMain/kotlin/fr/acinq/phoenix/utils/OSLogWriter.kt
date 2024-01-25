@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package fr.acinq.phoenix.utils

import co.touchlab.kermit.DefaultFormatter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import co.touchlab.kermit.darwin.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.concurrent.AtomicReference
import platform.darwin.OS_LOG_TYPE_DEBUG
import platform.darwin.OS_LOG_TYPE_DEFAULT
import platform.darwin.OS_LOG_TYPE_ERROR
import platform.darwin.OS_LOG_TYPE_FAULT
import platform.darwin.OS_LOG_TYPE_INFO
import platform.darwin.os_log_type_t
import kotlin.experimental.ExperimentalNativeApi

/**
 * This is based off the implementation in Kermit.
 * Except their implementation is broken:
 * https://github.com/touchlab/Kermit/pull/381
 */
@OptIn(ExperimentalNativeApi::class)
open class OSLogWriter internal constructor(
    private val messageStringFormatter: MessageStringFormatter,
    private val darwinLogger: DarwinLogger
) : LogWriter() {

    constructor(
        messageStringFormatter: MessageStringFormatter = DefaultFormatter
    ) : this(messageStringFormatter, DarwinLoggerActual)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        callLog(
            severity, formatMessage(
                severity = severity,
                message = Message(message),
                tag = Tag(tag)
            ), throwable
        )
    }

    // Added to do some testing on log format. https://github.com/touchlab/Kermit/issues/243
    open fun callLog(severity: Severity, message: String, throwable: Throwable?) {
        val tag = "PhoenixShared.FooBar"
        val type = kermitSeverityToOsLogType(severity)
        darwinLogger.log(tag, type, message)
        if (throwable != null) {
            logThrowable(tag, type, throwable)
        }
    }

    open fun logThrowable(tag: String, type: os_log_type_t, throwable: Throwable){
        darwinLogger.log(tag, type, throwable.getStackTrace().joinToString("\n"))
    }

    private fun kermitSeverityToOsLogType(severity: Severity): os_log_type_t = when (severity) {
        Severity.Verbose, Severity.Debug -> OS_LOG_TYPE_DEBUG
        Severity.Info -> OS_LOG_TYPE_INFO
        Severity.Warn -> OS_LOG_TYPE_DEFAULT
        Severity.Error -> OS_LOG_TYPE_ERROR
        Severity.Assert -> OS_LOG_TYPE_FAULT
    }

    open fun formatMessage(severity: Severity, tag: Tag, message: Message): String =
        messageStringFormatter.formatMessage(null, tag, message)
}


internal interface DarwinLogger {
    fun log(tag: String, type: os_log_type_t, message: String)
}

@OptIn(ExperimentalForeignApi::class)
private object DarwinLoggerActual : DarwinLogger {
    private val _logMap = AtomicReference(mapOf<String, darwin_os_log_t>())
    fun splitTag(tag: String): Pair<String, String> {
        return when (val idx = tag.lastIndexOf(".")) {
            -1 -> Pair("", tag)
            else -> Pair(tag.substring(0, idx), tag.substring(idx+1))
        }
    }
    fun getLog(tag: String): darwin_os_log_t {
        var currentLogMap = _logMap.value
        currentLogMap[tag]?.let { return it }
        val (subsystem, category) = splitTag(tag)
        val log = darwin_log_create(subsystem, category)!!
        while (true) {
            val newPair: Pair<String, darwin_os_log_t> = Pair(tag, log)
            val updatedLogMap: Map<String, darwin_os_log_t> = currentLogMap.plus(newPair)
            if (_logMap.compareAndSet(currentLogMap, updatedLogMap)) {
                return log
            } else {
                currentLogMap = _logMap.value
                currentLogMap[tag]?.let { return it }
            }
        }
    }
    override fun log(tag: String, type: os_log_type_t, message: String) {
        darwin_log_with_type(getLog(tag), type, message)
    }
}