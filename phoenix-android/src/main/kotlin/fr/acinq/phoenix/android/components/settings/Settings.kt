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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.copyToClipboard

@Composable
fun Setting(
    modifier: Modifier = Modifier,
    title: String,
    titleNote: String? = null,
    subtitle: @Composable (ColumnScope.() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    maxTitleLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = title, role = Role.Button, onClick = onClick, enabled = enabled) else Modifier)
            .enableOrFade(enabled)
    ) {
        Row(modifier = Modifier.weight(1f).padding(padding)) {
            if (leadingIcon != null) {
                Column {
                    leadingIcon()
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.weight(1f),
                        maxLines = maxTitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (titleNote != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = titleNote,
                            style = MaterialTheme.typography.subtitle2.copy(fontSize = 12.sp),
                            modifier = Modifier.alignByBaseline(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.subtitle2) {
                        subtitle()
                    }
                }
            }
        }

        if (trailingIcon != null) {
            Column {
                trailingIcon()
            }
        }
    }
}

@Composable
fun Setting(title: String, description: String?, maxDescriptionLines: Int = Int.MAX_VALUE, icon: Int? = null) {
    Setting(
        title = title,
        subtitle = description?.let {
            { Text(text = it, maxLines = maxDescriptionLines, overflow = TextOverflow.Ellipsis) }
        },
        leadingIcon = icon?.let { { PhoenixIcon(resourceId = it) } },
    )
}

@Composable
fun Setting(title: String, subtitle: @Composable ColumnScope.() -> Unit, icon: Int? = null) {
    Setting(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon?.let { { PhoenixIcon(resourceId = it) } },
    )
}

@Composable
fun SettingWithCopy(
    title: String,
    titleNote: String? = null,
    value: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    Setting(
        title = title,
        titleNote = titleNote,
        subtitle = {
            Text(text = value, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        },
        trailingIcon = {
            Button(
                icon = R.drawable.ic_copy,
                onClick = { copyToClipboard(context, value, title) }
            )
        },
    )
}

@Composable
fun Setting(
    title: String,
    description: String,
    onClick: () -> Unit,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    Setting(
        title = title,
        leadingIcon = icon?.let { { PhoenixIcon(resourceId = it) } },
        enabled = enabled, onClick = onClick,
        subtitle = { Text(description) })
}

@Composable
fun SettingButton(
    text: String,
    icon: Int,
    textStyle: TextStyle = MaterialTheme.typography.button,
    iconTint: Color = MaterialTheme.colors.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    )
}
