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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import kotlin.math.roundToInt

/** A slider to pick a Satoshi value from an array of accepted values. */
@Composable
fun SatoshiSlider(
    modifier: Modifier = Modifier,
    onAmountChange: (Satoshi) -> Unit,
    onErrorStateChange: (Boolean) -> Unit,
    possibleValues: Array<Satoshi>,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(0.0f) }
    var errorMessage by remember { mutableStateOf("") }
    val valuesCount = remember(possibleValues) { possibleValues.size - 1 }

    Column(modifier = modifier.enableOrFade(enabled)) {
        Slider(
            value = index,
            onValueChange = {
                errorMessage = ""
                try {
                    index = it
                    val amountPicked = possibleValues[index.roundToInt()]
                    onAmountChange(amountPicked)
                    onErrorStateChange(false)
                } catch (e: Exception) {
                    errorMessage = context.getString(R.string.validation_invalid_amount)
                    onErrorStateChange(true)
                }
            },
            valueRange = 0.0f..valuesCount.toFloat(),
            steps = valuesCount - 1, // steps = spaces in-between options
            enabled = enabled,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colors.primary,
                inactiveTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.4f),
                activeTickColor = MaterialTheme.colors.primary,
                inactiveTickColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
            )
        )

        errorMessage.takeUnless { it.isBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            ErrorMessage(header = it, padding = PaddingValues(0.dp))
        }
    }
}

