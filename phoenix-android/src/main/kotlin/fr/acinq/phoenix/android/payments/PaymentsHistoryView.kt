/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.home.PaymentLine
import fr.acinq.phoenix.android.home.PaymentLineLoading
import fr.acinq.phoenix.android.home.PaymentsViewModel
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.WalletPaymentId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentsHistoryView(
    onBackClick: () -> Unit,
    paymentsViewModel: PaymentsViewModel,
    onPaymentClick: (WalletPaymentId) -> Unit,
) {
    val log = logger("PaymentsHistory")
    val groupedPayments = paymentsViewModel.allPaymentsFlow.collectAsState().value.values.toList()
        .groupBy {
            val date = Instant.fromEpochMilliseconds(it.orderRow.completedAt ?: it.orderRow.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
            date.month
        }

    DefaultScreenLayout(
        isScrollable = false
    ) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.payments_history_title))
        Card {
            LazyColumn {
                groupedPayments.forEach { (month, payments) ->
                    stickyHeader {
                        Text(
                            text = month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
                            style = MaterialTheme.typography.body2.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                    items(items = payments) {
                        if (it.paymentInfo == null) {
                            LaunchedEffect(key1 = it.orderRow.id.identifier) {
                                paymentsViewModel.getPaymentDescription(it.orderRow)
                            }
                            PaymentLineLoading(it.orderRow.id, it.orderRow.createdAt, onPaymentClick)
                        } else {
                            PaymentLine(it.paymentInfo, onPaymentClick)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.payments_history_backup_notavailable),
            style = MaterialTheme.typography.caption.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}