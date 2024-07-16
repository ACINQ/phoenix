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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.utils.gray300
import fr.acinq.phoenix.android.utils.gray600

@Composable
fun SwitchView(
    modifier: Modifier = Modifier,
    text: String,
    textStyle: TextStyle = MaterialTheme.typography.body1,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    var internalChecked by rememberSaveable { mutableStateOf(checked) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                enabled = enabled
            ) {
                internalChecked = !internalChecked
                onCheckedChange(internalChecked)
            }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = 6.dp, end = 16.dp)
        ) {
            Text(text = text, style = textStyle)
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.subtitle2)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier
                .enableOrFade(enabled)
                .offset(y = 4.dp)
                .indication(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = false, color = if (isDarkTheme) gray300 else gray600, radius = 28.dp)
                )
        )
    }
}

@Composable
fun BasicSwitchWithText(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.body1,
    enabled: Boolean = true,
) {
    var internalChecked by rememberSaveable { mutableStateOf(checked) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                enabled = enabled
            ) {
                internalChecked = !internalChecked
                onCheckedChange(internalChecked)
            }
            .padding(vertical = 6.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier
                .enableOrFade(enabled)
                .offset(y = (-2).dp)
                .indication(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = false, color = if (isDarkTheme) gray300 else gray600, radius = 28.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = textStyle)
    }
}
