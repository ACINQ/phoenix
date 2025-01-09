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

package fr.acinq.phoenix.android.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.PaymentRowState
import fr.acinq.phoenix.android.PaymentsViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.payments.details.PaymentLine
import fr.acinq.phoenix.android.utils.datastore.HomeAmountDisplayMode
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.managers.WalletBalance


@Composable
fun ColumnScope.PaymentsList(
    modifier: Modifier = Modifier,
    swapInBalance: WalletBalance,
    balanceDisplayMode: HomeAmountDisplayMode,
    onPaymentClick: (UUID) -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    payments: List<WalletPaymentInfo>,
    allPaymentsCount: Long,
) {
    Column(modifier = modifier.weight(1f, fill = true), horizontalAlignment = Alignment.CenterHorizontally) {
        if (payments.isEmpty()) {
            Text(
                text = stringResource(id = R.string.home__payments_none),
                style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center, fontSize = 14.sp),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .widthIn(max = 250.dp)
            )
        } else {
            LatestPaymentsList(
                allPaymentsCount = allPaymentsCount,
                payments = payments,
                onPaymentClick = onPaymentClick,
                onPaymentsHistoryClick = onPaymentsHistoryClick,
                isAmountRedacted = balanceDisplayMode == HomeAmountDisplayMode.REDACTED,
            )
        }
    }
}

@Composable
private fun ColumnScope.LatestPaymentsList(
    allPaymentsCount: Long,
    payments: List<WalletPaymentInfo>,
    onPaymentClick: (UUID) -> Unit,
    onPaymentsHistoryClick: () -> Unit,
    isAmountRedacted: Boolean,
) {
    val morePaymentsButton: @Composable () -> Unit = {
        FilledButton(
            text = stringResource(id = R.string.home__payments_more_button),
            icon = R.drawable.ic_chevron_down,
            iconTint = MaterialTheme.typography.caption.color,
            onClick = onPaymentsHistoryClick,
            backgroundColor = Color.Transparent,
            textStyle = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f, fill = true),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(payments) {
            PaymentLine(it, null, onPaymentClick, isAmountRedacted)
        }
        if (payments.isNotEmpty() && allPaymentsCount > PaymentsViewModel.paymentsCountInHome) {
            item {
                Spacer(Modifier.height(16.dp))
                morePaymentsButton()
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
