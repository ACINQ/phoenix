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

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.io.NodeURI
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BuildConfig
import fr.acinq.eclair.phoenix.send.ReadingState
import java.io.File
import java.lang.RuntimeException

object Wallet {

  // ------------------------ DATADIR & FILES

  private const val ECLAIR_BASE_DATADIR = "node-data"
  private const val SEED_FILE = "seed.dat"
  private const val ECLAIR_DB_FILE = "eclair.sqlite"
  private const val NETWORK_DB_FILE = "network.sqlite"
  private val WALLET_DB_FILE = "wallet.sqlite"

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

  fun cleanInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
      trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
      trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
      else -> trimmed
    }
  }

  fun checkInvoice(input: String): String {
    val cleanInvoice = cleanInvoice(input)
    return try {
      PaymentRequest.read(cleanInvoice)
      cleanInvoice
    } catch (e1: Exception) {
      try {
        BitcoinURI(cleanInvoice)
        cleanInvoice
      } catch (e2: Exception) {
        throw RuntimeException("not a valid invoice: ${e1.localizedMessage} / ${e2.localizedMessage}")
      }
    }
  }

  fun extractInvoice(input: String): Any {
    val cleanInvoice = cleanInvoice(input)
    return try {
      PaymentRequest.read(cleanInvoice)
    } catch (e1: Exception) {
      try {
        BitcoinURI(cleanInvoice)
      } catch (e2: Exception) {
        throw RuntimeException("not a valid invoice: ${e1.localizedMessage} / ${e2.localizedMessage}")
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
      imm.hideSoftInputFromWindow(view.windowToken, 0 )
    }
  }

  // ------------------------ NODES & API URLS

  const val PRICE_RATE_API = "https://blockchain.info/ticker"
  val ACINQ: NodeURI = NodeURI.parse("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@34.250.234.192:9735")
  const val WALLET_CONTEXT_SOURCE = "https://acinq.co/mobile/walletcontext.json"
  const val DEFAULT_ONCHAIN_EXPLORER = "https://api.blockcypher.com/v1/btc/test3/txs/"
}
