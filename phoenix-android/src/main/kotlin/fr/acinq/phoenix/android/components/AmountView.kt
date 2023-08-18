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

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.launch

@Composable
fun AmountView(
    amount: MilliSatoshi,
    modifier: Modifier = Modifier,
    showUnit: Boolean = true,
    forceUnit: CurrencyUnit? = null,
    isRedacted: Boolean = false,
    prefix: String? = null,
    amountTextStyle: TextStyle = MaterialTheme.typography.body1,
    unitTextStyle: TextStyle = MaterialTheme.typography.body1,
    separatorSpace: Dp = 4.dp,
    mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE,
    onClick: (suspend (Context, Boolean) -> Unit)? = { context, inFiat -> UserPrefs.saveIsAmountInFiat(context, !inFiat) }
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val unit = forceUnit ?: if (LocalShowInFiat.current) {
        LocalFiatCurrency.current
    } else {
        LocalBitcoinUnit.current
    }
    val inFiat = LocalShowInFiat.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { scope.launch { onClick(context, inFiat) } }
            ) else Modifier)
    ) {
        if (!isRedacted && prefix != null && amount > MilliSatoshi(0)) {
            Text(
                text = prefix,
                style = amountTextStyle,
                modifier = Modifier.alignBy(FirstBaseline)
            )
        }
        Text(
            text = if (isRedacted) "****" else amount.toPrettyString(unit, fiatRate, mSatDisplayPolicy = mSatDisplayPolicy),
            style = amountTextStyle,
            modifier = Modifier.alignBy(FirstBaseline)
        )
        if (!isRedacted && showUnit) {
            Spacer(modifier = Modifier.width(separatorSpace))
            Text(
                text = unit.displayCode,
                style = unitTextStyle,
                modifier = Modifier.alignBy(FirstBaseline)
            )
        }
    }
}

/** Outputs a column with the amount in fiat or btc on top, and the reverse below. Can switch by clicking on top amount. */
@Composable
fun AmountWithAltView(
    amount: MilliSatoshi,
    modifier: Modifier = Modifier,
    amountTextStyle: TextStyle = MaterialTheme.typography.body1,
    unitTextStyle: TextStyle = MaterialTheme.typography.body1,
    separatorSpace: Dp = 4.dp,
    spaceBetweenAmounts: Dp = 4.dp,
    isOutgoing: Boolean? = null,
) {
    val (topUnit, bottomUnit) = if (LocalShowInFiat.current) {
        LocalFiatCurrency.current to LocalBitcoinUnit.current
    } else {
        LocalBitcoinUnit.current to LocalFiatCurrency.current
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AmountView(
            amount = amount,
            amountTextStyle = amountTextStyle,
            unitTextStyle = unitTextStyle,
            separatorSpace = separatorSpace,
            modifier = modifier,
            forceUnit = topUnit,
            prefix = isOutgoing?.let { stringResource(id = if (it) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received) }
        )
        Spacer(modifier = Modifier.height(spaceBetweenAmounts))
        AmountView(
            amount = amount,
            amountTextStyle = MaterialTheme.typography.caption,
            unitTextStyle = MaterialTheme.typography.caption,
            separatorSpace = separatorSpace,
            modifier = modifier,
            forceUnit = bottomUnit,
            prefix = if (bottomUnit is FiatCurrency) stringResource(R.string.utils_rounded) else null
        )
    }
}

/** Outputs a column with the amount in bitcoin on top, and the fiat amount below. */
@Composable
fun ColumnScope.AmountWithFiatBelow(
    amount: MilliSatoshi,
    amountTextStyle: TextStyle = MaterialTheme.typography.body1,
    fiatTextStyle: TextStyle = MaterialTheme.typography.caption,
) {
    val prefBtcUnit = LocalBitcoinUnit.current
    val prefFiatCurrency = LocalFiatCurrency.current
    Text(
        text = amount.toPrettyString(prefBtcUnit, withUnit = true),
        style = amountTextStyle,
    )
    Text(
        text = stringResource(id = R.string.utils_converted_amount, amount.toPrettyString(prefFiatCurrency, fiatRate, withUnit = true)),
        style = fiatTextStyle,
    )
}

/** Outputs a row with the amount in bitcoin on the left, and the fiat amount on the right. */
@Composable
fun AmountWithFiatRowView(
    amount: MilliSatoshi,
    modifier: Modifier = Modifier,
    amountTextStyle: TextStyle = MaterialTheme.typography.body1,
    unitTextStyle: TextStyle = MaterialTheme.typography.body1,
    fiatTextStyle: TextStyle = MaterialTheme.typography.caption,
    separatorSpace: Dp = 4.dp,
) {
    val prefBtcUnit = LocalBitcoinUnit.current
    Row {
        AmountView(amount = amount, amountTextStyle = amountTextStyle, unitTextStyle = unitTextStyle, separatorSpace = separatorSpace, modifier = modifier, forceUnit = prefBtcUnit, onClick = null)
        Spacer(modifier = Modifier.width(4.dp))
        AmountInFiatView(amount = amount, style = fiatTextStyle)
    }
}

/** Creates a Text component with [amount] converted to fiat and properly formatted. */
@Composable
fun AmountInFiatView(
    amount: MilliSatoshi,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.caption
) {
    val prefFiatCurrency = LocalFiatCurrency.current
    val rate = fiatRate
    val fiatAmount = remember(amount) { amount.toPrettyString(prefFiatCurrency, rate, withUnit = true) }
    Text(text = stringResource(id = R.string.utils_converted_amount, fiatAmount), style = style, modifier = modifier)
}
