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

package fr.acinq.phoenix.legacy.utils.tor

import net.freehaven.tor.control.EventHandler
import org.slf4j.LoggerFactory

enum class TorConnectionStatus {
  LAUNCHED, CONNECTED, FAILED, CLOSED
}

abstract class TorEventHandler : EventHandler {
  private val log = LoggerFactory.getLogger(this::class.java)

  abstract fun onConnectionUpdate(name: String, status: TorConnectionStatus)

  override fun orConnStatus(status: String?, name: String?) {
    when (status) {
      "LAUNCHED" -> {
        log.info("connection to or=$name started")
        name?.let { onConnectionUpdate(name, TorConnectionStatus.LAUNCHED) }
      }
      "CONNECTED" -> {
        log.info("connection to or=$name successful")
        name?.let { onConnectionUpdate(name, TorConnectionStatus.CONNECTED) }
      }
      "FAILED" -> {
        log.info("connection or=$name failed")
        name?.let { onConnectionUpdate(name, TorConnectionStatus.FAILED) }
      }
      "CLOSED" -> {
        log.info("connection closed with or=$name")
        name?.let { onConnectionUpdate(name, TorConnectionStatus.CLOSED) }
      }
    }
  }

  override fun circuitStatus(status: String?, circuitId: String?, serverIdsPath: String?) {
    when (status) {
      "LAUNCHED" -> log.debug("circuit id=$circuitId has been assigned")
      "BUILT" -> log.debug("circuit id=$circuitId built with path=$serverIdsPath")
      "EXTENDED" -> log.debug("circuit id=$circuitId extended with path=$serverIdsPath")
      "FAILED" -> log.debug("circuit id=$circuitId failed")
      "CLOSED" -> log.debug("circuit id=$circuitId closed with path=$serverIdsPath")
    }
  }

  override fun streamStatus(status: String?, streamId: String?, target: String?) {
    log.debug("stream id=$streamId in state=$status with target=$target")
  }

  override fun bandwidthUsed(read: Long, written: Long) {
    log.debug("bandwidth read=$read written=$written")
  }

  override fun newDescriptors(orList: MutableList<String>?) {
    log.debug("new or list=$orList")
  }

  override fun unrecognized(type: String?, message: String?) {
    message("INFO", "$type: $message")
  }

  override fun message(severity: String?, message: String?) {
    when (severity) {
      "DEBUG" -> log.debug("tor message: $message")
      "INFO", "NOTICE" -> log.info("tor message: $message")
      "WARN" -> log.warn("tor message: $message")
      "ERR" -> log.error("tor message: $message")
    }
  }
}
