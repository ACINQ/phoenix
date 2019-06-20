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

package fr.acinq.eclair.phoenix.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import fr.acinq.eclair.phoenix.BuildConfig
import org.slf4j.LoggerFactory

object Logging {
  fun setupLogger() {
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.reset()

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

    // set level
    lc.getLogger("fr.acinq.eclair.crypto").level = Level.WARN // ChaCha20Poly1305 spams a lot in debug
    if (BuildConfig.DEBUG) {
      lc.getLogger("io.netty").level = Level.DEBUG
    } else {
      lc.getLogger("io.netty").level = Level.WARN
    }

    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    root.addAppender(appender)
  }
}