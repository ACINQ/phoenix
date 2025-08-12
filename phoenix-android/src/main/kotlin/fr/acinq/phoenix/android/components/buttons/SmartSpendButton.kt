/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.auth.pincode.PinDialogTitle
import fr.acinq.phoenix.android.components.auth.spendinglock.CheckSpendingPinFlow
import kotlinx.coroutines.launch

/**
 * Helper button for sending a payment out of the wallet (include buying liquidity, or closing channel).
 * Will prompt for the spending PIN if it is enabled in the user prefs.
 * By default, it will check that `PeerManager.mayDoPayments` is true, or freeze the button if not.
 */
@Composable
fun SmartSpendButton(
    onSpend: suspend () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.send_pay_button),
    icon: Int = R.drawable.ic_send,
    shape: Shape = CircleShape,
    ignoreChannelsState: Boolean = false,
    prompt: @Composable () -> Unit = { PinDialogTitle(text = stringResource(id = R.string.pincode_check_spending_payment_title)) }
) {
    val scope = rememberCoroutineScope()
    val needPinCodeToPayFlow = LocalUserPrefs.current?.getIsSpendingPinEnabled?.collectAsState(null)
    val needPinCodeToPay = needPinCodeToPayFlow?.value
    val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()

    var showSendingPinCheck by remember { mutableStateOf(false) }
    if (showSendingPinCheck) {
        CheckSpendingPinFlow(
            onCancel = { showSendingPinCheck = false },
            onPinValid = { scope.launch { onSpend() } },
            prompt = prompt
        )
    }

    when {
        !mayDoPayments && !ignoreChannelsState -> {
            ProgressView(text = stringResource(id = R.string.send_connecting_button), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = modifier, horizontalArrangement = Arrangement.Center)
        }
        else -> when (needPinCodeToPay) {
            null -> {
                ProgressView(text = stringResource(R.string.utils_loading_prefs), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = modifier, horizontalArrangement = Arrangement.Center)
            }
            true -> {
                FilledButton(
                    text = text,
                    icon = icon,
                    shape = shape,
                    enabled = enabled,
                    modifier = modifier,
                    onClick = { showSendingPinCheck = true },
                )
            }
            false -> {
                FilledButton(
                    text = text,
                    icon = icon,
                    shape = shape,
                    enabled = enabled,
                    modifier = modifier,
                    onClick = { scope.launch { onSpend() } }
                )
            }
        }
    }
}