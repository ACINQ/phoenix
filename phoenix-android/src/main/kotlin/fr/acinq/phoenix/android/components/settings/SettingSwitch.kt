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

package fr.acinq.phoenix.android.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.enableOrFade

@Composable
fun SettingSwitch(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckChangeAttempt: ((Boolean) -> Unit)
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = { if (enabled) onCheckChangeAttempt(!isChecked) }, enabled = enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                PhoenixIcon(it, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(12.dp))
            }
            Text(text = title, style = if (enabled) MaterialTheme.typography.body2 else MaterialTheme.typography.caption, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = isChecked, onCheckedChange = null, enabled = enabled, modifier = Modifier.enableOrFade(enabled))
        }
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth()) {
                icon?.let {
                    Spacer(modifier = Modifier.width(30.dp))
                }
                Text(text = description, style = MaterialTheme.typography.subtitle2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}