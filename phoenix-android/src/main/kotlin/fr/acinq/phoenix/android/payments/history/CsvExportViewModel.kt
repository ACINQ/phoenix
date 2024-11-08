/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.history


import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.extensions.basicDescription
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.PaymentsFetcher
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.CsvWriter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter

sealed class CsvExportState {
    object Init : CsvExportState()
    data class Generating(val exportedCount: Int) : CsvExportState()
    data class Success(val paymentsCount: Int, val uri: Uri, val content: String) : CsvExportState()
    object NoData : CsvExportState()
    data class Failed(val error: Throwable) : CsvExportState()
}

class CsvExportViewModel(
    private val peerManager: PeerManager,
    private val dbManager: DatabaseManager,
    private val paymentsFetcher: PaymentsFetcher,
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    /** Timestamp in millis of the oldest completed payment (incoming or outgoing) */
    var oldestCompletedTimestamp by mutableStateOf<Long?>(null)
        private set
    var startTimestampMillis by mutableStateOf<Long?>(null)
    var endTimestampMillis by mutableStateOf(currentTimestampMillis())
    var includesFiat by mutableStateOf(true)
    var includesDescription by mutableStateOf(true)
    var includesNotes by mutableStateOf(true)
    var includesOriginDestination by mutableStateOf(true)
    var state by mutableStateOf<CsvExportState>(CsvExportState.Init)
        private set

    private val authority = "${BuildConfig.APPLICATION_ID}.provider"

    init {
        refreshOldestCompletedTimestamp()
    }

    fun reset() {
        state = CsvExportState.Init
    }

    fun generateCSV(context: Context) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to generate CSV: ", e)
            state = CsvExportState.Failed(e)
        }) {
            if (state is CsvExportState.Generating) return@launch
            if (startTimestampMillis == null) throw IllegalArgumentException("start timestamp is undefined")
            state = CsvExportState.Generating(exportedCount = 0)
            val csvConfig = CsvWriter.Configuration(
                includesFiat = includesFiat,
                includesDescription = includesDescription,
                includesNotes = includesNotes,
                includesOriginDestination = includesOriginDestination,
            )
            log.debug("exporting payments data between start={} end={} config={}", startTimestampMillis?.toAbsoluteDateTimeString(), endTimestampMillis.toAbsoluteDateTimeString(), csvConfig)
            val batchSize = 32
            var batchOffset = 0
            var fetching = true
            val rows = mutableListOf<String>()
            rows += CsvWriter.makeHeaderRow(csvConfig)
            while (fetching) {
                dbManager.paymentsDb().listRangeSuccessfulPaymentsOrder(
                    startDate = startTimestampMillis!!,
                    endDate = endTimestampMillis,
                    count = batchSize,
                    skip = batchOffset
                ).map { paymentRow ->
                    paymentsFetcher.getPayment(paymentRow, WalletPaymentFetchOptions.All)?.let { info ->
                        val descriptions = listOf(
                            info.payment.basicDescription(),
                            info.metadata.userDescription,
                            info.metadata.lnurl?.pay?.metadata?.longDesc
                        ).mapNotNull { it.takeIf { !it.isNullOrBlank() } }
                        val row = CsvWriter.makeRow(
                            info = info,
                            localizedDescription = descriptions.joinToString("\n"),
                            config = csvConfig
                        )
                        state = CsvExportState.Generating(rows.size - 1)
                        rows += row
                    }
                }.let { result ->
                    if (result.isEmpty()) {
                        fetching = false
                    } else {
                        batchOffset += result.size
                    }
                }
            }
            val paymentsCount = rows.size - 1 // offset header row
            if (paymentsCount <= 1) {
                log.debug("no data found for this export attempt")
                state = CsvExportState.NoData
            } else {
                // create file & write to disk
                val exportDir = File(context.cacheDir, "payments")
                if (!exportDir.exists()) exportDir.mkdir()
                val file = File.createTempFile("phoenix-", ".csv", exportDir)
                val writer = FileWriter(file, true)
                rows.forEach {
                    writer.write(it)
                }
                writer.close()
                val uri = FileProvider.getUriForFile(context, authority, file)
                val content = rows.joinToString(separator = "")
                log.info("processed $paymentsCount payments CSV export")
                state = CsvExportState.Success(paymentsCount, uri, content)
            }
        }
    }

    private fun refreshOldestCompletedTimestamp() {
        viewModelScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            log.error("failed to get oldest completed payment timestamp: ", e)
            state = CsvExportState.Failed(e)
        }) {
            dbManager.paymentsDb().getOldestCompletedDate().let {
                oldestCompletedTimestamp = it
                if (startTimestampMillis == null) startTimestampMillis = it
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager,
        private val dbManager: DatabaseManager,
        private val paymentsFetcher: PaymentsFetcher,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CsvExportViewModel(peerManager, dbManager, paymentsFetcher) as T
        }
    }
}