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

package fr.acinq.phoenix.android.components.buttons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.enableOrFade


@Composable
fun MenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.body1,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onSurface,
    iconSpace: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    enabled: Boolean = true,
) {
    MenuButton(
        content = {
            Text(
                text = text,
                style = textStyle,
                textAlign = TextAlign.Start
            )
        },
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        iconTint = iconTint,
        iconSpace = iconSpace,
        padding = padding,
        enabled = enabled,
    )
}

@Composable
fun MenuButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onSurface,
    iconSpace: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
            )
            .padding(padding)
            .enableOrFade(enabled),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PhoenixIcon(resourceId = icon, tint = iconTint)
            Spacer(modifier = Modifier.width(iconSpace))
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        PhoenixIcon(resourceId = R.drawable.ic_chevron_right, tint = MaterialTheme.typography.caption.color.copy(alpha = 0.6f))
    }
}