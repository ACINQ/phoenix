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

package fr.acinq.phoenix.android.components.inputs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.MempoolFeerate

/** A feerate picker using a [SatoshiLogSlider] + speed estimation, depending on the [mempoolFeerate] param. */
@Composable
fun FeerateSlider(
    feerate: Satoshi,
    onFeerateChange: (Satoshi) -> Unit,
    mempoolFeerate: MempoolFeerate?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var showManualFeeDialog by remember { mutableStateOf(false) }

    if (showManualFeeDialog) {
        Dialog(
            onDismiss = { showManualFeeDialog = false },
            title = stringResource(R.string.feerate_slider_dialog_title),
        ) {
            var errorMessage by remember { mutableStateOf("") }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(16.dp))
                InlineSatoshiInput(
                    amount = feerate,
                    onAmountChange = {
                        errorMessage = ""
                        when {
                            it == null -> Unit
                            else -> { onFeerateChange(it) }
                        }
                    },
                    label = { Text(text = stringResource(R.string.feerate_slider_dialog_input_label), style = MaterialTheme.typography.body2) },
                    minAmount = 0.sat, maxAmount = 3000.sat,
                    placeholder = { Text(text = stringResource(R.string.feerate_slider_dialog_input_placeholder)) },
                    isError = errorMessage.isNotBlank(),
                    showConvertedValue = false,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = errorMessage, color = negativeColor, fontSize = 14.sp)
                }

                if (mempoolFeerate == null) {
                    Spacer(Modifier.height(16.dp))
                    InfoMessage(
                        header = stringResource(id = R.string.mempool_unknown_feerate_title),
                        details = stringResource(id = R.string.mempool_unknown_feerate_details),
                        padding = PaddingValues(0.dp),
                    )
                }
            }
        }
    }

    Column(modifier = modifier
        .then(Modifier.clickable(
            onClick = { showManualFeeDialog = true },
            onClickLabel = stringResource(R.string.feerate_slider_dialog_title),
            role = Role.Button,
            enabled = enabled,
        ))
    ) {
        // the actual value of the feerate, in sat/vbyte
        Text(text = stringResource(id = R.string.cpfp_feerate_value, feerate.sat), style = MaterialTheme.typography.body2)

        // feedback giving a speed estimate from the current state of the  mempool
        if (mempoolFeerate == null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.mempool_unknown_feerate_title),
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        } else {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body1.copy(fontSize = 14.sp)) {
                when {
                    feerate >= mempoolFeerate.fastest.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_fastest), maxLines = 1, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    feerate >= mempoolFeerate.halfHour.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_halfhour), maxLines = 1, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    feerate >= mempoolFeerate.hour.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_hour), maxLines = 1, modifier = Modifier.padding(vertical = 1.dp))
                    }
                    else -> {
                        TextWithIcon(text = stringResource(id = R.string.mempool_slow), icon = R.drawable.ic_alert_triangle, iconTint = MaterialTheme.colors.onSurface, space = 4.dp, maxLines = 1)
                    }
                }
            }
        }

        SatoshiLogSlider(
            modifier = Modifier
                .widthIn(max = 130.dp)
                .offset(x = (-4).dp, y = (-8).dp),
            amount = feerate,
            onAmountChange = onFeerateChange,
            minAmount = mempoolFeerate?.minimum?.feerate ?: 1.sat,
            maxAmount = mempoolFeerate?.fastest?.feerate?.times(2) ?: 500.sat,
            enabled = enabled
        )
    }
}