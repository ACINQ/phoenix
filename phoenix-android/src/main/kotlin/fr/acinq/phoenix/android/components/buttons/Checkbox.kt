/*
 * Copyright 2022 ACINQ SAS
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.isDarkTheme
import fr.acinq.phoenix.android.utils.gray300
import fr.acinq.phoenix.android.utils.gray600

@Composable
fun Checkbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.body1,
    enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(vertical = 16.dp, horizontal = 0.dp)
) {
    var internalChecked by rememberSaveable { mutableStateOf(checked) }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .enableOrFade(enabled)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Checkbox) {
                internalChecked = !internalChecked
                onCheckedChange(internalChecked)
            }
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = internalChecked,
            onCheckedChange = null,
            modifier = Modifier.indication(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, color = if (isDarkTheme) gray300 else gray600, radius = 28.dp)
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = textStyle)
    }
}