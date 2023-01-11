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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.home.PaymentLine
import fr.acinq.phoenix.android.home.PaymentLineLoading
import fr.acinq.phoenix.android.home.PaymentsViewModel
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.WalletPaymentId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
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
    val listState = rememberLazyListState()
    val allPaymentsCount by business.paymentsManager.paymentsCount.collectAsState()
    val payments by paymentsViewModel.paymentsFlow.collectAsState()
    val groupedPayments = payments.values.groupBy {
        val date = Instant.fromEpochMilliseconds(it.orderRow.completedAt ?: it.orderRow.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
        date.month to date.year
    }
    DefaultScreenLayout(
        isScrollable = false
    ) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.payments_history_title, allPaymentsCount))
        Card(modifier = Modifier.weight(1f, fill = false)) {
            LaunchedEffect(key1 = listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull() }
                    .filterNotNull()
                    .map { it.index }
                    .distinctUntilChanged()
                    .filter { index ->
                        val entriesInListCount = groupedPayments.entries.size + payments.size
                        val isLastElementFetched = index == entriesInListCount - 1
                        isLastElementFetched
                    }
                    .distinctUntilChanged()
                    .collect { index ->
                        val hasMorePaymentsToFetch = payments.size < allPaymentsCount
                        if (hasMorePaymentsToFetch) {
                            // Subscribe to a bit more payments. Ideally would be the screen height / height of each payment.
                            paymentsViewModel.subscribeToPayments(offset = 0, count = index + 10)
                        }
                    }
                }
            LazyColumn(
                state = listState,
            ) {
                groupedPayments.forEach { (date, payments) ->
                    stickyHeader {
                        Text(
                            text = "${date.first.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()} ${date.second}",
                            style = MaterialTheme.typography.body2.copy(fontSize = 12.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colors.surface)
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                    items(items = payments) {
                        if (it.paymentInfo == null) {
                            LaunchedEffect(key1 = it.orderRow.id.identifier) {
                                paymentsViewModel.fetchPaymentDetails(it.orderRow)
                            }
                            PaymentLineLoading(it.orderRow.id, onPaymentClick)
                        } else {
                            PaymentLine(it.paymentInfo, onPaymentClick)
                        }
                    }
                }
            }
        }
    }
}
