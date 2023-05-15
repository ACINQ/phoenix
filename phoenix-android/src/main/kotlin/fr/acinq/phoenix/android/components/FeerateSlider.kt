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

package fr.acinq.phoenix.android.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.MempoolFeerate

@Composable
fun FeerateSlider(
    feerate: Satoshi,
    onFeerateChange: (Satoshi) -> Unit,
    mempoolFeerate: MempoolFeerate?
) {
    var showUnknownMempoolStateDialog by remember { mutableStateOf(false) }
    Column {
        // the actual value of the feerate, in sat/vbyte
        Text(text = stringResource(id = R.string.cpfp_feerate_value, feerate.sat), style = MaterialTheme.typography.body2)

        // feedback giving a speed estimate from the current state of the  mempool
        if (mempoolFeerate == null) {
            if (showUnknownMempoolStateDialog) {
                Dialog(
                    onDismiss = { showUnknownMempoolStateDialog = false },
                    title = stringResource(id = R.string.mempool_unknown_feerate_title)
                ) {
                    Text(
                        text = stringResource(id = R.string.mempool_unknown_feerate_details),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            FilledButton(
                text = stringResource(id = R.string.mempool_unknown_feerate_title),
                icon = R.drawable.ic_alert_triangle,
                textStyle = MaterialTheme.typography.body1.copy(color = negativeColor, fontSize = 14.sp),
                iconTint = negativeColor,
                maxLines = 1,
                space = 6.dp,
                backgroundColor = Color.Transparent,
                padding = PaddingValues(6.dp),
                modifier = Modifier.offset(x = (-6).dp),
                onClick = { showUnknownMempoolStateDialog = true }
            )
        } else {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body1.copy(fontSize = 14.sp)) {
                when {
                    feerate >= mempoolFeerate.fastest.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_fastest), maxLines = 1)
                    }
                    feerate >= mempoolFeerate.halfHour.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_halfhour))
                    }
                    feerate >= mempoolFeerate.hour.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_hour))
                    }
                    feerate >= mempoolFeerate.economy.feerate -> {
                        Text(text = stringResource(id = R.string.mempool_eco))
                    }
                    else -> {
                        TextWithIcon(text = stringResource(id = R.string.mempool_slow), icon = R.drawable.ic_alert_triangle, iconTint = MaterialTheme.colors.onSurface, space = 4.dp)
                    }
                }
            }
        }

        SatoshiSlider(
            modifier = Modifier
                .widthIn(max = 130.dp)
                .offset(x = (-4).dp, y = (-8).dp),
            amount = feerate,
            onAmountChange = onFeerateChange,
            minAmount = mempoolFeerate?.minimum?.feerate ?: 1.sat,
            maxAmount = mempoolFeerate?.fastest?.feerate?.times(5) ?: 500.sat
        )
    }
}