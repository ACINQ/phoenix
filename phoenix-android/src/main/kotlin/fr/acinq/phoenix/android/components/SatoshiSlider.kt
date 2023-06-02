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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.R
import kotlin.math.log10
import kotlin.math.pow

/** A logarithmic slider to get a Satoshi value. Can be used to get a feerate for example. */
@Composable
fun SatoshiSlider(
    modifier: Modifier = Modifier,
    amount: Satoshi,
    onAmountChange: (Satoshi) -> Unit,
    minAmount: Satoshi = 1.sat,
    maxAmount: Satoshi = 500.sat,
    enabled: Boolean = true,
    steps: Int = 30,
) {
    val context = LocalContext.current
    val minFeerateLog = remember { log10(minAmount.sat.toFloat()) }
    val maxFeerateLog = remember { log10(maxAmount.sat.toFloat()) }
    var feerateLog by remember { mutableStateOf(log10(amount.sat.toFloat())) }

    var errorMessage by remember { mutableStateOf("") }

    Column(modifier = modifier.enableOrFade(enabled)) {
        Slider(
            value = feerateLog,
            onValueChange = {
                errorMessage = ""
                try {
                    feerateLog = it
                    val valueSat = 10f.pow(it).toLong().sat
                    onAmountChange(valueSat)
                } catch (e: Exception) {
                    errorMessage = context.getString(R.string.validation_invalid_number)
                }
            },
            valueRange = minFeerateLog..maxFeerateLog,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colors.primary,
                inactiveTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.4f),
                activeTickColor = MaterialTheme.colors.primary,
                inactiveTickColor = Color.Transparent,
            )
        )

        errorMessage.takeUnless { it.isBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            ErrorMessage(header = it, padding = PaddingValues(0.dp))
        }
    }
}