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
import fr.acinq.phoenix.android.security.EncryptedData
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toAbsoluteDateTimeString
import fr.acinq.phoenix.csv.WalletPaymentCsvWriter
import fr.acinq.phoenix.managers.DatabaseManager
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

sealed class CsvExportState {
    data object Init : CsvExportState()
    data object Generating : CsvExportState()
    data class Success(val uri: Uri, val content: String) : CsvExportState()
    data object NoData : CsvExportState()
    data class Failed(val error: Throwable) : CsvExportState()
}

sealed class DatabaseExportState {
    data object Init : DatabaseExportState()
    data object Exporting : DatabaseExportState()
    data class Success(val uri: Uri) : DatabaseExportState()
    sealed class Failed : DatabaseExportState() {
        data class Generic(val cause: Throwable) : Failed()
        data object CannotWriteToUri : Failed()
        data object EncryptionError : Failed()
    }
}

class PaymentsExportViewModel(
    private val dbManager: DatabaseManager,
    private val walletManager: WalletManager,
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

    var csvExportState by mutableStateOf<CsvExportState>(CsvExportState.Init)
        private set
    var databaseExportState by mutableStateOf<DatabaseExportState>(DatabaseExportState.Init)
        private set

    private val authority = "${BuildConfig.APPLICATION_ID}.provider"

    init {
        refreshOldestCompletedTimestamp()
    }

    fun reset() {
        csvExportState = CsvExportState.Init
    }

    fun generateCSV(context: Context) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to generate CSV: ", e)
            csvExportState = CsvExportState.Failed(e)
        }) {
            if (csvExportState is CsvExportState.Generating) return@launch
            if (startTimestampMillis == null) throw IllegalArgumentException("start timestamp is undefined")
            csvExportState = CsvExportState.Generating
            val csvConfig = WalletPaymentCsvWriter.Configuration(
                includesFiat = includesFiat,
                includesDescription = includesDescription,
                includesNotes = includesNotes,
                includesOriginDestination = includesOriginDestination,
            )
            val csvWriter = WalletPaymentCsvWriter(csvConfig)
            log.debug("exporting payments data between start={} end={} config={}", startTimestampMillis?.toAbsoluteDateTimeString(), endTimestampMillis.toAbsoluteDateTimeString(), csvConfig)
            val batchSize = 32
            var batchOffset = 0
            var fetching = true
            while (fetching) {
                dbManager.paymentsDb().listCompletedPayments(
                    startDate = startTimestampMillis!!,
                    endDate = endTimestampMillis,
                    count = batchSize.toLong(),
                    skip = batchOffset.toLong(),
                ).map {
                    csvWriter.add(it.payment, it.metadata)
                }.let { result ->
                    if (result.isEmpty()) {
                        fetching = false
                    } else {
                        batchOffset += result.size
                    }
                }
            }
            val content = csvWriter.dumpAndClear()
            // create file & write to disk
            val exportDir = File(context.cacheDir, "payments")
            if (!exportDir.exists()) exportDir.mkdir()
            val file = File.createTempFile("phoenix-", ".csv", exportDir)
            val writer = FileWriter(file, true)
            writer.write(content)
            writer.close()
            val uri = FileProvider.getUriForFile(context, authority, file)
            log.info("processed payments CSV export")
            csvExportState = CsvExportState.Success(uri, content)
        }
    }

    fun vacuumDatabase(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to export payments database: ", e)
            databaseExportState = DatabaseExportState.Failed.Generic(e)
        }) {
            if (databaseExportState is DatabaseExportState.Exporting) return@launch
            databaseExportState = DatabaseExportState.Exporting

            // 1 - vacuum existing database into a temporary file in the cache dir
            val exportDir = File(context.cacheDir, "payments")
            if (!exportDir.exists()) exportDir.mkdir()
            val file = File.createTempFile("phoenix-payments-db-", ".sqlite", exportDir)
            dbManager.paymentsDb().driver.execute(null, "VACUUM INTO '${file.absolutePath}'", 0)
            delay(1_000)
            log.info("payments-db successfully vacuumed")

            // 2 - encrypt file
            val encryptedData = try {
                FileInputStream(file).use { fis ->
                    val data = fis.readBytes()
                    EncryptedData.encrypt(
                        version = EncryptedData.Version.V1,
                        data = data,
                        keyManager = walletManager.keyManager.filterNotNull().first()
                    )
                }
            } catch (e: Exception) {
                log.error("failed to encrypt payments-db: ", e)
                databaseExportState = DatabaseExportState.Failed.EncryptionError
                return@launch
            }

            // 3 - write encrypted file to the provided URI (which does not need permission since it's user provided)
            context.contentResolver.openOutputStream(uri, "w")?.use { os ->
                os.write(encryptedData.write())
            } ?: run {
                databaseExportState = DatabaseExportState.Failed.CannotWriteToUri
                return@launch
            }


            log.info("payment-db export written to disk")
            databaseExportState = DatabaseExportState.Success(uri)
            delay(5_000)
            file.delete()
            log.debug("cleaned up cache data (${file.name})")
        }
    }

    private fun refreshOldestCompletedTimestamp() {
        viewModelScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            log.error("failed to get oldest completed payment timestamp: ", e)
            csvExportState = CsvExportState.Failed(e)
        }) {
            dbManager.paymentsDb().getOldestCompletedDate().let {
                oldestCompletedTimestamp = it
                if (startTimestampMillis == null) startTimestampMillis = it
            }
        }
    }

    class Factory(
        private val dbManager: DatabaseManager,
        private val walletManager: WalletManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentsExportViewModel(dbManager, walletManager) as T
        }
    }
}