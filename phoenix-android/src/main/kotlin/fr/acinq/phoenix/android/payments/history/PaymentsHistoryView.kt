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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.payments.details.PaymentLine
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle
import java.util.Locale


private sealed class PaymentsGroup {
    object Today : PaymentsGroup() {
        override fun hashCode(): Int = "today".hashCode()
    }

    object Yesterday : PaymentsGroup() {
        override fun hashCode(): Int = "yesterday".hashCode()
    }

    object ThisWeek : PaymentsGroup() {
        override fun hashCode(): Int = "thisweek".hashCode()
    }

    object LastWeek : PaymentsGroup() {
        override fun hashCode(): Int = "lastweek".hashCode()
    }

    data class Other(val month: Month, val year: Int) : PaymentsGroup() {
        override fun hashCode(): Int = "$year-$month".hashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return if (other !is Other) {
                false
            } else {
                if (month != other.month) false
                else year == other.year
            }
        }
    }
}

@Composable
fun PaymentsHistoryView(
    onBackClick: () -> Unit,
    paymentsViewModel: PaymentsViewModel,
    onPaymentClick: (UUID) -> Unit,
    onCsvExportClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    val allPaymentsCount by business.paymentsManager.paymentsCount.collectAsState()
    val paymentsPage by paymentsViewModel.paymentsPage.collectAsState()
    val payments by paymentsViewModel.paymentsFlow.collectAsState()

    val groupedPayments = remember(payments) {
        val timezone = TimeZone.currentSystemDefault()
        val (todaysDayOfWeek, today) = java.time.LocalDate.now().atTime(23, 59, 59).toKotlinLocalDateTime().let { it.dayOfWeek to it.toInstant(timezone) }
        payments.values.groupBy {
            val paymentInstant = Instant.fromEpochMilliseconds(it.payment.createdAt)
            val daysElapsed = paymentInstant.daysUntil(today, timezone)
            when {
                daysElapsed == 0 -> PaymentsGroup.Today
                todaysDayOfWeek.value != 1 && daysElapsed == 1 -> PaymentsGroup.Yesterday
                todaysDayOfWeek.value - 1 - daysElapsed >= 0 -> PaymentsGroup.ThisWeek
                todaysDayOfWeek.value + 7 - 1 - daysElapsed >= 0 -> PaymentsGroup.LastWeek
                else -> paymentInstant.toLocalDateTime(timezone).let { PaymentsGroup.Other(it.month, it.year) }
            }
        }
    }

    val log = logger("PaymentsHistory")

    DefaultScreenLayout(
        isScrollable = false,
        backgroundColor = MaterialTheme.colors.background
    ) {
        DefaultScreenHeader(
            content = {
                Text(text = stringResource(id = R.string.payments_history_title, allPaymentsCount))
                Spacer(Modifier.weight(1f))
                Button(
                    text = stringResource(id = R.string.payments_history_export_button),
                    icon = R.drawable.ic_share,
                    shape = CircleShape,
                    onClick = onCsvExportClick
                )
            },
            onBackClick = onBackClick,
        )

        LaunchedEffect(key1 = listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }?.run { first().index to last().index } }
                .filterNotNull()
                .distinctUntilChanged()
                .map { (topIndex, bottomIndex) ->
                    val listSize = groupedPayments.entries.size + payments.size // we must take group headers into account
                    val isFirstVisible = topIndex == 0
                    val isLastVisible = bottomIndex + 10 >= listSize - 1
                    val hasMorePaymentsAbove = paymentsPage.offset > 0
                    val hasMorePaymentsBelow = paymentsPage.rows.isNotEmpty()
                    log.debug("[$topIndex:$bottomIndex ($listSize) isFirstVisible=$isFirstVisible hasMoreAbove=$hasMorePaymentsAbove isLastVisible=$isLastVisible hasMoreBelow=$hasMorePaymentsBelow")
                    if (hasMorePaymentsBelow && isLastVisible) {
                        paymentsPage.offset + PaymentsViewModel.pageSize / 2
                    } else if (hasMorePaymentsAbove && isFirstVisible) {
                        paymentsPage.offset - PaymentsViewModel.pageSize / 2
                    } else null
                }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { offset ->
                    paymentsViewModel.subscribeToPayments(offset, PaymentsViewModel.pageSize)
                }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            groupedPayments.forEach { (header, payments) ->
                item {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.background)) {
                        CardHeader(
                            text = when (header) {
                                PaymentsGroup.Today -> stringResource(id = R.string.payments_history_today)
                                PaymentsGroup.Yesterday -> stringResource(id = R.string.payments_history_yesterday)
                                PaymentsGroup.ThisWeek -> stringResource(id = R.string.payments_history_thisweek)
                                PaymentsGroup.LastWeek -> stringResource(id = R.string.payments_history_lastweek)
                                is PaymentsGroup.Other -> "${header.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()} ${header.year}"
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                itemsIndexed(items = payments) { index, item ->
                    ItemCard(index = index, maxItemsCount = payments.size) {
                        PaymentLine(item, null, onPaymentClick)
                    }
                }
            }
        }
    }
}
