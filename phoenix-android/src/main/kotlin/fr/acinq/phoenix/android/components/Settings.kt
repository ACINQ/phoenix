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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.copyToClipboard


@Composable
fun Setting(modifier: Modifier = Modifier, title: String, description: String?) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.height(2.dp))
        Text(description ?: "", style = MaterialTheme.typography.subtitle2)
    }
}

@Composable
fun SettingWithCopy(
    title: String,
    titleMuted: String? = null,
    value: String,
    maxLinesValue: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    Row {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp).weight(1f)) {
            Row {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.alignByBaseline(),
                )
                if (titleMuted != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = titleMuted,
                        style = MaterialTheme.typography.subtitle2.copy(fontSize = 12.sp),
                        modifier = Modifier
                            .alignByBaseline(),
                    )
                }

            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.subtitle2, maxLines = maxLinesValue, overflow = TextOverflow.Ellipsis)
        }
        Button(
            icon = R.drawable.ic_copy,
            onClick = { copyToClipboard(context, value, title) }
        )
    }
}

@Composable
fun SettingWithDecoration(
    modifier: Modifier = Modifier,
    title: String,
    description: @Composable () -> Unit = {},
    decoration: (@Composable ()-> Unit)?,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (decoration != null) {
                decoration()
                Spacer(Modifier.width(12.dp))
            }
            Text(text = title, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            if (decoration != null) {
                Spacer(modifier = Modifier.width(30.dp))
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
fun SettingInteractive(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)
) {
    SettingInteractive(modifier = modifier, title = title, icon = icon, enabled = enabled, onClick = onClick,
        description = { Text(description) })
}

@Composable
fun SettingInteractive(
    modifier: Modifier = Modifier,
    title: String,
    description: @Composable () -> Unit = {},
    icon: Int? = null,
    iconTint: Color? = null,
    maxTitleLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onClick: (() -> Unit)
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = { if (enabled) onClick() })
            .enableOrFade(enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PhoenixIcon(icon, tint = iconTint ?: LocalContentColor.current, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f),
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
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
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckChangeAttempt: ((Boolean) -> Unit)
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = { if (enabled) onCheckChangeAttempt(!isChecked) })
            .enableOrFade(enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                PhoenixIcon(it, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(12.dp))
            }
            Text(text = title, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = isChecked, onCheckedChange = null)
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

@Composable
fun SettingButton(
    text: Int,
    icon: Int,
    textStyle: TextStyle = MaterialTheme.typography.button,
    iconTint: Color = MaterialTheme.colors.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        text = stringResource(id = text),
        textStyle = textStyle,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    )
}
