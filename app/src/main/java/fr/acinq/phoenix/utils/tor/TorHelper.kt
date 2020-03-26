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

package fr.acinq.phoenix.utils.tor

import android.content.Context
import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager
import com.msopentech.thali.toronionproxy.EventBroadcaster
import com.msopentech.thali.toronionproxy.TorConfig
import com.msopentech.thali.toronionproxy.TorInstaller
import com.msopentech.thali.toronionproxy.android.R
import fr.acinq.phoenix.utils.TorSetupException
import net.freehaven.tor.control.EventHandler
import org.slf4j.LoggerFactory
import org.torproject.android.binary.TorResourceInstaller
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.system.measureTimeMillis


object TorHelper {
  private val log = LoggerFactory.getLogger(this::class.java)

  const val STARTUP_TIMEOUT_SEC: Int = 30
  const val STARTUP_TRIES: Int = 2
  const val PORT: Int = 10462

  fun bootstrap(context: Context, eventHandler: EventHandler): AndroidOnionProxyManager {
    try {
      // configuration files
      val nativeDir = File(context.applicationInfo.nativeLibraryDir)
      val configDir = File(context.filesDir, "tor")
      if (!configDir.exists()) configDir.mkdir()
      val torConfig = TorConfig.Builder(nativeDir, configDir).fileCreationTimeout(STARTUP_TIMEOUT_SEC).build()
      val torInstaller = TorInstaller(context, configDir)

      // setup manager
      val onionProxyManager = AndroidOnionProxyManager(context, torConfig, torInstaller, null, null, eventHandler)
      onionProxyManager.setup()
      onionProxyManager.torInstaller.updateTorConfigCustom("ControlPort auto" +
        "\nControlPortWriteToFile " + onionProxyManager.context.config.controlPortFile +
        "\nCookieAuthFile " + onionProxyManager.context.config.cookieAuthFile +
        "\nCookieAuthentication 1" +
        "\nSocksPort $PORT")

      // start tor
      if (!onionProxyManager.startWithRepeat(STARTUP_TIMEOUT_SEC, STARTUP_TRIES, true)) {
        throw TorSetupException("could not start TOR after $STARTUP_TRIES with ${STARTUP_TIMEOUT_SEC}s timeout")
      } else {
        log.info("successfully started TOR")
      }
      return onionProxyManager
    } catch (e: Exception) {
      log.error("failed to start TOR: ", e)
      throw TorSetupException(e.localizedMessage ?: e.javaClass.simpleName)
    }
  }
}

/**
 * @param rootInstallFolder is the root folder for the tor configuration files, containing the geoip files, the bridges and the torrc
 * file. The tor executable will be located in the Android native library directory for the app.
 */
private class TorInstaller(val context: Context, rootInstallFolder: File) : TorInstaller() {
  protected val resourceInstaller: TorResourceInstaller = TorResourceInstaller(context, rootInstallFolder)

  override fun setup() {
    resourceInstaller.installResources()
  }

  override fun openBridgesStream(): InputStream {
    return context.resources.openRawResource(R.raw.bridges)
  }

  override fun updateTorConfigCustom(content: String) {
    resourceInstaller.torrcFile?.let { resourceInstaller.updateTorConfigCustom(it, content) }
  }
}
