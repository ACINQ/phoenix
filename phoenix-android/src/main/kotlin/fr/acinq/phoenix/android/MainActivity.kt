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

package fr.acinq.phoenix.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.components.nfc.NfcState
import fr.acinq.phoenix.android.components.nfc.NfcStateRepository
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.services.HceService
import fr.acinq.phoenix.android.services.PaymentsForegroundService
import fr.acinq.phoenix.android.utils.PhoenixAndroidTheme
import fr.acinq.phoenix.android.utils.nfc.NfcReaderCallback
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MainActivity : AppCompatActivity() {

    val log: Logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    private var navController: NavHostController? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    appViewModel.clearActiveWallet()
                    appViewModel.startWalletImmediately.value = true
                }
                else -> Unit
            }
        }
    }

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        intent?.fixUri()
        onNewIntent(intent)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // lock screen when screen is off
        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, intentFilter)

        setContent {
            navController = rememberNavController()
            navController?.let {
                PhoenixAndroidTheme {
                    AppRoot(it, appViewModel)
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        appViewModel.scheduleAutoLock()
    }

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        log.info("receive new_intent with action=${intent?.action} data=${intent?.data}")

        when (intent?.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> {
                // ignored
            }
            else -> {
                // force the intent flag to single top, in order to avoid [handleDeepLink] finish the current activity.
                // this would otherwise clear the app view model, i.e. loose the state which virtually reboots the app
                // TODO: look into detaching the app state from the activity
                intent?.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent?.fixUri()
                try {
                    scope.launch {
                        delay(1000)
                        if (navController != null) {
                            log.info("handling deeplink=$intent")
                            navController?.handleDeepLink(intent)
                        } else {
                            log.warn("navigation controller is not initialized, ignoring deeplink=$intent")
                        }
                    }
                } catch (e: Exception) {
                    log.warn("could not handle deeplink: {}", e.localizedMessage)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, PaymentsForegroundService::class.java))
    }

    override fun onResume() {
        super.onResume()
        tryReconnect()
    }

    override fun onPause() {
        super.onPause()
        stopNfcReader()
    }

    fun isNfcReaderAvailable() : Boolean {
        return nfcAdapter?.isEnabled == true
    }

    fun startNfcReader() {
        if (NfcStateRepository.isEmulating()) {
            Toast.makeText(applicationContext, applicationContext.getString(R.string.nfc_err_busy), Toast.LENGTH_SHORT).show()
            return
        }
        NfcStateRepository.updateState(NfcState.ShowReader)
        nfcAdapter?.enableReaderMode(this@MainActivity, NfcReaderCallback(onFoundData = {
            if (navController != null) {
                runOnUiThread {
                    log.info("nfc reader found valid ndef data, redirecting to send-screen with input=$it")
                    navController?.navigate("${Screen.BusinessNavGraph.Send.route}?input=$it")
                }
            } else {
                log.warn("ignoring nfc start command, nav controller is not initialized yet")
            }
        }), NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, Bundle())
    }

    fun stopNfcReader() {
        if (NfcStateRepository.isReading()) {
            NfcStateRepository.updateState(NfcState.Inactive)
        }
        nfcAdapter?.disableReaderMode(this@MainActivity)
    }

    fun isHceSupported() : Boolean {
        val adapter = nfcAdapter
        return adapter != null && adapter.isEnabled && packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    }

    fun startHceService(paymentRequest: String) {
        if (nfcAdapter == null) {
            Toast.makeText(this, applicationContext.getString(R.string.nfc_err_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        if (nfcAdapter?.isEnabled == false) {
            Toast.makeText(this, applicationContext.getString(R.string.nfc_err_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            Toast.makeText(this, applicationContext.getString(R.string.nfc_err_hce_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        if (NfcStateRepository.isReading()) {
            Toast.makeText(applicationContext, applicationContext.getString(R.string.nfc_err_busy), Toast.LENGTH_SHORT).show()
            return
        }

        NfcStateRepository.updateState(NfcState.EmulatingTag(paymentRequest))
        val intent = Intent(this@MainActivity, HceService::class.java)
        startService(intent)
    }

    fun stopHceService() {
        stopService(Intent(this@MainActivity, HceService::class.java))
    }

    private fun tryReconnect() {
        lifecycleScope.launch {
            appViewModel.activeWalletInUI.value?.business?.let { business ->
                val daemon = business.appConnectionsDaemon ?: return@launch
                val connections = business.connectionsManager.connections.value
                if (connections.electrum !is Connection.ESTABLISHED) {
                    lifecycleScope.launch {
                        log.info("resuming app with electrum conn=${connections.electrum}, forcing reconnection...")
                        daemon.forceReconnect(AppConnectionsDaemon.ControlTarget.Electrum)
                    }
                }
                if (connections.peer !is Connection.ESTABLISHED) {
                    lifecycleScope.launch {
                        log.info("resuming app with peer conn=${connections.peer}, forcing reconnection...")
                        daemon.forceReconnect(AppConnectionsDaemon.ControlTarget.Peer)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
        log.debug("destroyed main activity")
    }

    /**
     * There are many apps launching LN-themed URIs, and they use all kind of pattern. This methods fixes them so they are standard
     * and can deeplink to the appropriate screen of the application.
     *
     * For example, instead of `phoenix:<invoice>`, use `lightning:<invoice>`.
     *
     * Note: [Intent] fields are mutable.
     */
    private fun Intent.fixUri() {
        val initialUri = this.data
        val scheme = initialUri?.scheme
        val ssp = initialUri?.schemeSpecificPart
        if (scheme == "phoenix" && ssp != null && (ssp.startsWith("lnbc") || ssp.startsWith("lntb") || ssp.startsWith("lnurl") || ssp.startsWith("lno"))) {
            this.data = "lightning:$ssp".toUri()
            log.debug("rewritten intent uri from {} to {}", initialUri, intent.data)
        }
    }

}
