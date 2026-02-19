package fr.acinq.phoenix.android.settings.troubleshoot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.utils.Logging
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.shareFile
import fr.acinq.phoenix.utils.diagnostics.DiagnosticsHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class LogsExportState {
    object Init : LogsExportState()
    object Exporting : LogsExportState()
    object Failed : LogsExportState()
}

sealed class DiagnosticsExportState {
    object Init : DiagnosticsExportState()
    object Generating : DiagnosticsExportState()
    data class Success(val data: String) : DiagnosticsExportState()
    sealed class Failure : DiagnosticsExportState() {
        data class Generic(val cause: Throwable) : Failure()
    }
}

class DiagnosticsViewModel(val application: PhoenixApplication, val business: PhoenixBusiness, val walletId: WalletId) : ViewModel() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val authority = "${BuildConfig.APPLICATION_ID}.provider"
    val logsViewState = MutableStateFlow<LogsExportState>(LogsExportState.Init)
    val logsShareState = MutableStateFlow<LogsExportState>(LogsExportState.Init)

    val diagnosticExportState = MutableStateFlow<DiagnosticsExportState>(DiagnosticsExportState.Init)

    fun viewLogs(context: Context) {
        if (logsViewState.value == LogsExportState.Exporting) return
        logsViewState.value = LogsExportState.Exporting

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            logger.error("error in viewLogs: ", e)
            logsViewState.value = LogsExportState.Failed
        }) {
            val logFile = Logging.exportLogFile(application.applicationContext)
            val uri = FileProvider.getUriForFile(application.applicationContext, authority, logFile)

            viewModelScope.launch(Dispatchers.Main) {
                val localViewIntent: Intent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    type = "text/plain"
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val viewIntent = Intent.createChooser(localViewIntent, application.applicationContext.getString(R.string.troubleshooting_logs_view_with))
                context.startActivity(viewIntent)

                logsViewState.value = LogsExportState.Init
            }
        }
    }

    fun shareLogs() {
        if (logsShareState.value is LogsExportState.Exporting) return
        logsShareState.value = LogsExportState.Exporting

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            logger.error("error in shareLogs: ", e)
            logsShareState.value = LogsExportState.Failed
        }) {
            val logFile = Logging.exportLogFile(application.applicationContext)
            viewModelScope.launch(Dispatchers.Main) {
                shareFile(
                    context = application.applicationContext,
                    data = FileProvider.getUriForFile(application.applicationContext, authority, logFile),
                    subject = application.applicationContext.getString(R.string.troubleshooting_logs_share_subject),
                    chooserTitle = application.applicationContext.getString(R.string.troubleshooting_logs_share_title)
                )
                logsShareState.value = LogsExportState.Init
            }
        }
    }

    fun copyDiagnostics() {
        if (diagnosticExportState.value is DiagnosticsExportState.Generating) return
        diagnosticExportState.value = DiagnosticsExportState.Generating

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            logger.error("error in copyDiagnostics: ", e)
            diagnosticExportState.value = DiagnosticsExportState.Failure.Generic(e)
        }) {
            val globalPrefs = application.globalPrefs
            val userPrefs = DataStoreManager.loadUserPrefsForWallet(application.applicationContext, walletId)

            val result = StringBuilder()
            result.append(DiagnosticsHelper.getDiagnostics(business))

            result.appendLine(DiagnosticsHelper.SEPARATOR)
            result.appendLine("liquidity policy: ${userPrefs.getLiquidityPolicy.first()}")
            result.appendLine("swap address format: ${userPrefs.getSwapAddressFormat.first()}")
            result.appendLine("overpayment enabled: ${userPrefs.getIsOverpaymentEnabled.first()}")
            result.appendLine("lnurl-auth scheme: ${userPrefs.getLnurlAuthScheme.first()}")

            result.appendLine(DiagnosticsHelper.SEPARATOR)
            result.appendLine("android version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT} ${Build.VERSION.CODENAME})")
            result.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")

            result.appendLine(DiagnosticsHelper.SEPARATOR)
            result.appendLine("fcm token: ${!globalPrefs.getFcmToken.first().isNullOrBlank()}")
            result.appendLine("btc unit: ${userPrefs.getBitcoinUnits.first()}")
            result.appendLine("fiat currency: ${userPrefs.getFiatCurrencies.first()}")
            result.appendLine("spending pin enabled: ${userPrefs.getSpendingPinEnabled.first()}")
            result.appendLine("lock pin enabled: ${userPrefs.getLockPinEnabled.first()}")
            result.appendLine("biometric enabled: ${userPrefs.getLockBiometricsEnabled.first()}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.appendLine("notification permission: ${ContextCompat.checkSelfPermission(application.applicationContext, 
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED}")
            }

            viewModelScope.launch(Dispatchers.Main) {
                copyToClipboard(application.applicationContext, data = result.toString(), dataLabel = "Phoenix diagnostics data")
            }
        }
    }

    class Factory(
        private val application: PhoenixApplication,
        private val business: PhoenixBusiness,
        private val walletId: WalletId,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DiagnosticsViewModel(application, business, walletId) as T
        }
    }
}