package fr.acinq.phoenix.legacy.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.WorkerThread
import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scala.Base58
import fr.acinq.bitcoin.scala.Block
import fr.acinq.bitcoin.scala.ByteVector32
import fr.acinq.bitcoin.scala.DeterministicWallet
import fr.acinq.eclair.blockchain.singleaddress.SingleAddressEclairWallet
import fr.acinq.eclair.io.NodeURI
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.BuildConfig
import fr.acinq.phoenix.legacy.background.Xpub
import fr.acinq.phoenix.legacy.lnurl.LNUrl
import fr.acinq.phoenix.legacy.lnurl.LNUrlError
import fr.acinq.phoenix.legacy.utils.crypto.SeedManager
import fr.acinq.phoenix.legacy.utils.tor.TorHelper
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

object Wallet {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  val ACINQ: NodeURI = NodeURI.parse("03864ef025fde8fb587d989186ce6a4a186895ee44a926bfc370e2c366597a3f8f@3.33.236.230:9735")
  val httpClient = OkHttpClient()

  private const val ECLAIR_BASE_DATADIR = "node-data"
  internal const val SEED_FILE = "seed.dat"
  private const val ECLAIR_DB_FILE = "eclair.sqlite"
  const val ECLAIR_DB_FILE_MIGRATION = "eclair-migration.sqlite"
  private val acceptedLightningPrefix by lazy { PaymentRequest.prefixes().get(getChainHash()) }

  fun getDatadir(context: Context): File {
    return File(context.filesDir, ECLAIR_BASE_DATADIR)
  }

  fun getChainDatadir(context: Context): File {
    return File(getDatadir(context), BuildConfig.CHAIN)
  }

  fun getEclairDBFile(context: Context): File {
    return File(getChainDatadir(context), ECLAIR_DB_FILE)
  }

  fun getEclairDBMigrationFile(context: Context): File {
    return File(getChainDatadir(context), ECLAIR_DB_FILE_MIGRATION)
  }

  fun hasWalletBeenSetup(context: Context): Boolean {
    return SeedManager.getSeedFromDir(getDatadir(context)) != null
  }

  fun isMainnet(): Boolean = "mainnet" == BuildConfig.CHAIN

  fun getChainHash(): ByteVector32 {
    return if (isMainnet()) Block.LivenetGenesisBlock().hash() else Block.TestnetGenesisBlock().hash()
  }

  fun isSupportedPrefix(paymentRequest: PaymentRequest): Boolean = acceptedLightningPrefix.isDefined && acceptedLightningPrefix.get() == paymentRequest.prefix()

  fun getScriptHashVersion(): Byte {
    return if (isMainnet()) Base58.`Prefix$`.`MODULE$`.ScriptAddress() else Base58.`Prefix$`.`MODULE$`.ScriptAddressTestnet()
  }

  fun buildAddress(master: DeterministicWallet.ExtendedPrivateKey): SingleAddressEclairWallet {
    val path = if (isMainnet()) {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'/0/0")
    } else {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'/0/0")
    }
    return SingleAddressEclairWallet(getChainHash(), DeterministicWallet.derivePrivateKey(master, path).publicKey())
  }

  fun buildKmpSwapInAddress(master: DeterministicWallet.ExtendedPrivateKey): String {
    val path = if (isMainnet()) {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/1'/0/0")
    } else {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/1'/0/0")
    }
    return fr.acinq.bitcoin.scala.`package$`.`MODULE$`.computeP2WpkhAddress(DeterministicWallet.derivePrivateKey(master, path).publicKey(), getChainHash())
  }

  fun buildXpub(master: DeterministicWallet.ExtendedPrivateKey): Xpub {
    val path = if (isMainnet()) {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'")
    } else {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'")
    }
    val xpub = DeterministicWallet.encode(
      DeterministicWallet.publicKey(DeterministicWallet.derivePrivateKey(master, path)),
      if (isMainnet()) DeterministicWallet.zpub() else DeterministicWallet.vpub()
    )
    return Xpub(xpub = xpub, path = path.toString())
  }

  private fun trimInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
      trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
      trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
      trimmed.startsWith("lnurl://", true) -> trimmed.drop(8)
      trimmed.startsWith("lnurl:", true) -> trimmed.drop(6)
      else -> trimmed
    }
  }

  /**
   * This methods reads arbitrary strings and deserializes it to a Lightning/Bitcoin object, if possible.
   * <p>
   * Object can be of type:
   * - Lightning payment request (BOLT 11)
   * - BitcoinURI
   * - LNURL
   *
   * If the string cannot be read, throws an exception.
   */
  fun parseLNObject(source: String): Any {
    val input = trimInvoice(source)
    return try {
      PaymentRequest.read(input)
    } catch (e1: Exception) {
      try {
        BitcoinURI(input)
      } catch (e2: Exception) {
        try {
          val uri = URI(input)
          if (uri.scheme != null && uri.getParams().containsKey("lightning")) {
            uri.getParams()["lightning"]!! // use the lightning fallback
          } else {
            input
          }.let { LNUrl.extractLNUrl(it) }
        } catch (e3: Exception) {
          when (e3) {
            is LNUrlError -> throw e3 // the input is correct, but the LNURL service returns an error that must be visible to the user.
            else -> {
              log.debug("unhandled input=$input")
              log.debug("invalid as PaymentRequest: ${e1.localizedMessage}")
              log.debug("invalid as BitcoinURI: ${e2.localizedMessage}")
              log.debug("invalid as LNURL: ", e3)
              throw UnreadableLightningObject("not a readable lightning object: ${e1.localizedMessage} / ${e2.localizedMessage} / ${e3.localizedMessage}")
            }
          }
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
    val conf = HashMap<String, Any>()

    // electrum config
    val electrumServer = Prefs.getElectrumServer(context)
    if (electrumServer.isNotBlank()) {
      try {
        val address = HostAndPort.fromString(electrumServer).withDefaultPort(50002)
        if (address.host.isNotBlank()) {
          conf["eclair.electrum.host"] = address.host
          conf["eclair.electrum.port"] = address.port
          if (address.isOnion()) {
            // If Tor is used, we don't require TLS; Tor already adds a layer of encryption.
            // However the user can still force the app to check the certificate.
            conf["eclair.electrum.ssl"] = if (Prefs.getForceElectrumSSL(context)) "strict" else "off"
          } else {
            // Otherwise we require TLS with a valid server certificate.
            conf["eclair.electrum.ssl"] = "strict"
          }
        }
      } catch (e: Exception) {
        throw InvalidElectrumAddress(electrumServer)
      }
    }

    // TOR config
    if (Prefs.isTorEnabled(context)) {
      conf["eclair.socks5.enabled"] = true
      conf["eclair.socks5.host"] = "127.0.0.1"
      conf["eclair.socks5.port"] = TorHelper.PORT
      conf["eclair.socks5.use-for-ipv4"] = true
      conf["eclair.socks5.use-for-ipv6"] = true
      conf["eclair.socks5.use-for-tor"] = true
      conf["eclair.socks5.randomize-credentials"] = false // this allows tor stream isolation
    }

    return ConfigFactory.parseMap(conf)
  }

  fun HostAndPort.isOnion() = this.host.endsWith(".onion")

  /** Execute a (blocking) HTTP GET on the given url, expecting a JSON response. Will throw an exception if the query fails, or if the response is invalid/not JSON. */
  @WorkerThread
  suspend fun OkHttpClient.simpleExecute(url: String, isSilent: Boolean = false, callback: (json: JSONObject) -> Unit) {
    newCall(okhttp3.Request.Builder().url(url).build()).execute().run {
      val body = body()
      if (isSuccessful && body != null) {
        callback(JSONObject(body.string())).also { body.close() }
      } else if (!isSilent) {
        throw RuntimeException("invalid response (code=${code()}) for url=$url")
      }
    }
  }
}
