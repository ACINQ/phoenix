/*
 * Copyright 2021 ACINQ SAS
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.CurrencyUnit
import kotlinx.coroutines.launch

@Composable
fun AmountView(
    amount: MilliSatoshi,
    modifier: Modifier = Modifier,
    showUnit: Boolean = true,
    forceUnit: CurrencyUnit? = null,
    isOutgoing: Boolean? = null,
    amountTextStyle: TextStyle = MaterialTheme.typography.body1,
    unitTextStyle: TextStyle = MaterialTheme.typography.body1,
    separatorSpace: Dp = 4.dp,
    mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE
) {
    val unit = forceUnit ?: if (LocalShowInFiat.current) {
        LocalFiatCurrency.current
    } else {
        LocalBitcoinUnit.current
    }
    val inFiat = LocalShowInFiat.current

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Row(horizontalArrangement = Arrangement.Center,
        modifier = modifier.clickable {
            scope.launch {
                UserPrefs.saveIsAmountInFiat(context, !inFiat)
            }
        })
    {
        if (isOutgoing != null && amount > MilliSatoshi(0)) {
            Text(
                text = stringResource(id = if (isOutgoing) R.string.paymentline_sent_prefix else R.string.paymentline_received_prefix),
                style = amountTextStyle,
                modifier = Modifier.alignBy(FirstBaseline)
            )
        }
        Text(
            text = amount.toPrettyString(unit, fiatRate, mSatDisplayPolicy = mSatDisplayPolicy),
            style = amountTextStyle,
            modifier = Modifier.alignBy(FirstBaseline)
        )
        if (showUnit) {
            Spacer(modifier = Modifier.width(separatorSpace))
            Text(
                text = unit.toString(),
                style = unitTextStyle,
                modifier = Modifier.alignBy(FirstBaseline)
            )
        }
    }
}