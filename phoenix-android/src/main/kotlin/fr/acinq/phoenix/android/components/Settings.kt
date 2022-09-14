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
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R


@Composable
fun SettingCategory(textResId: Int) {
    Text(
        text = stringResource(id = textResId),
        style = MaterialTheme.typography.subtitle1.copy(fontSize = 14.sp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp, top = 24.dp, end = 0.dp, bottom = 4.dp)
    )
}

@Composable
fun Setting(modifier: Modifier = Modifier, title: String, description: String?) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.height(2.dp))
        Text(description ?: "", style = MaterialTheme.typography.subtitle2)
    }
}

@Composable
fun SettingInteractive(
    modifier: Modifier = Modifier,
    title: String,
    description: String?,
    icon: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)
) {
    SettingInteractive(modifier = modifier, title = title, icon = icon, enabled = enabled, onClick = onClick,
        description = { description?.let { Text(it) } })
}

@Composable
fun SettingInteractive(
    modifier: Modifier = Modifier,
    title: String,
    description: @Composable () -> Unit = {},
    icon: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = { if (enabled) onClick() })
            .enableOrFade(enabled)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PhoenixIcon(icon, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(16.dp))
            }
            Text(text = title, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            if (icon != null) {
                Spacer(modifier = Modifier.width(34.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.subtitle2) {
                    description()
                }
            }
        }
    }
}

@Composable
fun SettingSwitch(
    modifier: Modifier = Modifier,
    title: String,
    description: String?,
    icon: Int = R.drawable.ic_blank,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckChangeAttempt: ((Boolean) -> Unit)
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = { if (enabled) onCheckChangeAttempt(!isChecked) })
            .enableOrFade(enabled)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhoenixIcon(icon, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = isChecked, onCheckedChange = null)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(34.dp))
            Text(text = description ?: "", style = MaterialTheme.typography.subtitle2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
fun SettingButton(
    text: Int,
    icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        text = stringResource(id = text),
        icon = icon,
        iconTint = MaterialTheme.colors.onSurface,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    )
}
