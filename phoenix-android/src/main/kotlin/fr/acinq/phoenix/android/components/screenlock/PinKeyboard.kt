/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.components.screenlock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button

@Composable
fun PinKeyboard(
    onPinPress: (Int) -> Unit,
    onResetPress: () -> Unit,
    isEnabled: Boolean,
) {
    Column(
        modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            PinButton(pin = 1, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 2, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 3, onClick = onPinPress, isEnabled = isEnabled)
        }
        Row {
            PinButton(pin = 4, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 5, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 6, onClick = onPinPress, isEnabled = isEnabled)
        }
        Row {
            PinButton(pin = 7, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 8, onClick = onPinPress, isEnabled = isEnabled)
            PinButton(pin = 9, onClick = onPinPress, isEnabled = isEnabled)
        }
        Row {
            Spacer(modifier = Modifier.weight(1f))
            PinButton(pin = 0, onClick = onPinPress, isEnabled = isEnabled)
            ResetButton(onClick = onResetPress, isEnabled = isEnabled)
        }
    }
}

@Composable
private fun RowScope.PinButton(pin: Int, onClick: (Int) -> Unit, isEnabled: Boolean) {
    Button(
        text = pin.toString(),
        onClick = { onClick(pin) },
        modifier = Modifier.height(76.dp).weight(1f),
        enabled = isEnabled,
        textStyle = MaterialTheme.typography.h3,
    )
}
@Composable
private fun RowScope.ResetButton(onClick: () -> Unit, isEnabled: Boolean) {
    Button(
        icon = R.drawable.ic_trash,
        onClick = onClick,
        modifier = Modifier.height(76.dp).weight(1f),
        enabled = isEnabled,
        textStyle = MaterialTheme.typography.h3,
    )
}
