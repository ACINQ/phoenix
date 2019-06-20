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
import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.io.NodeURI
import fr.acinq.eclair.phoenix.BuildConfig
import java.io.File

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

  // ------------------------ NODES & API URLS

  const val PRICE_RATE_API = "https://blockchain.info/fr/ticker"
  val ACINQ: NodeURI = NodeURI.parse("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735")
  const val WALLET_CONTEXT_SOURCE = "https://acinq.co/mobile/walletcontext.json"
  const val DEFAULT_ONCHAIN_EXPLORER = "https://api.blockcypher.com/v1/btc/test3/txs/"
}
