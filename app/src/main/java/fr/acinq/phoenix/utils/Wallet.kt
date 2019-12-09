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

package fr.acinq.phoenix.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.common.base.Strings
import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.eclair.io.NodeURI
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BuildConfig
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object Wallet {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  val ACINQ: NodeURI = NodeURI.parse("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@34.250.234.192:9735")
  val httpClient = OkHttpClient()

  // ------------------------ DATADIR & FILES

  private const val ECLAIR_BASE_DATADIR = "node-data"
  private const val SEED_FILE = "seed.dat"
  private const val ECLAIR_DB_FILE = "eclair.sqlite"
  private const val NETWORK_DB_FILE = "network.sqlite"
  private const val WALLET_DB_FILE = "wallet.sqlite"

  fun getDatadir(context: Context): File {
    return File(context.filesDir, ECLAIR_BASE_DATADIR)
  }

  fun getSeedFile(context: Context): File {
    return File(getDatadir(context), SEED_FILE)
  }

  fun getChainDatadir(context: Context): File {
    return File(getDatadir(context), BuildConfig.CHAIN)
  }

  fun getEclairDBFile(context: Context): File {
    return File(getChainDatadir(context), ECLAIR_DB_FILE)
  }

  fun getNetworkDBFile(context: Context): File {
    return File(getChainDatadir(context), NETWORK_DB_FILE)
  }

  fun getChainHash(): ByteVector32 {
    return if ("mainnet" == BuildConfig.CHAIN) Block.LivenetGenesisBlock().hash() else Block.TestnetGenesisBlock().hash()
  }

  fun getXpubKeyPath(): DeterministicWallet.KeyPath {
    return if ("mainnet" == BuildConfig.CHAIN) DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'") else DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'")
  }
  fun getNodeKeyPath(): DeterministicWallet.KeyPath {
    return if ("mainnet" == BuildConfig.CHAIN) DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'/0/0") else DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'/0/0")
  }

  fun cleanUpInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
      trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
      trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
      else -> trimmed
    }
  }

  fun extractInvoice(input: String): Any {
    val invoice = cleanUpInvoice(input)
    return try {
      PaymentRequest.read(invoice)
    } catch (e1: Exception) {
      try {
        BitcoinURI(invoice)
      } catch (e2: Exception) {
        try {
          LNUrl(input)
        } catch (e3: Exception) {
          log.debug("unhandled input=$input")
          log.debug("invalid as PaymentRequest: ${e1.localizedMessage}")
          log.debug("invalid as BitcoinURI: ${e2.localizedMessage}")
          log.debug("invalid as LNURL: ${e3.localizedMessage}")
          throw RuntimeException("not a valid invoice: ${e1.localizedMessage} / ${e2.localizedMessage} / ${e3.localizedMessage}")
        }
      }
    }
  }

  fun showKeyboard(context: Context?, view: View) {
    context?.let {
      val imm = it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showSoftInput(view, InputMethodManager.RESULT_UNCHANGED_SHOWN)
    }
  }

  fun hideKeyboard(context: Context?, view: View) {
    context?.let {
      val imm = it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
  }

  /**
   * Builds a TypeSafe configuration for the node. Returns an empty config if there are no valid user's prefs.
   */
  fun getOverrideConfig(context: Context): Config {
    val electrumServer = Prefs.getElectrumServer(context)
    if (!Strings.isNullOrEmpty(electrumServer)) {
      try {
        val address = HostAndPort.fromString(electrumServer).withDefaultPort(50002)
        val conf = HashMap<String, Any>()
        if (!Strings.isNullOrEmpty(address.host)) {
          conf["eclair.electrum.host"] = address.host
          conf["eclair.electrum.port"] = address.port
          // custom server certificate must be valid
          conf["eclair.electrum.ssl"] = "strict"
          return ConfigFactory.parseMap(conf)
        }
      } catch (e: Exception) {
        log.error("invalid electrum server=$electrumServer, using empty config instead", e)
      }
    }
    return ConfigFactory.empty()
  }
}
