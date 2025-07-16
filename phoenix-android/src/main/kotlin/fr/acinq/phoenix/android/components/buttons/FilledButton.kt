/*
 * Copyright 2025 ACINQ SAS
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.utils.mutedBgColor

/** A rounded button with a primary background color. */
@Composable
fun FilledButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onPrimary,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    space: Dp = 12.dp,
    shape: Shape = CircleShape,
    textStyle: TextStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary),
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    backgroundColor: Color = MaterialTheme.colors.primary,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    onClick: () -> Unit,
) {
    Button(
        text = text,
        icon = icon,
        iconTint = iconTint,
        maxLines = maxLines,
        enabled = enabled,
        enabledEffect = enabledEffect,
        space = space,
        onClick = onClick,
        shape = shape,
        backgroundColor = backgroundColor,
        textStyle = textStyle,
        padding = padding,
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    )
}

@Composable
fun MutedFilledButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    space: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(12.dp),
    onClick: () -> Unit,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    FilledButton(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = mutedBgColor,
        textStyle = MaterialTheme.typography.button,
        iconTint = iconTint,
        modifier = modifier, text = text, icon = icon, maxLines = maxLines, enabled = enabled,
        enabledEffect = enabledEffect, space = space, padding = padding, onClick = onClick,
        horizontalArrangement = horizontalArrangement,
    )
}

@Composable
fun TransparentFilledButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.button,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    space: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(12.dp),
    onClick: () -> Unit,
) {
    FilledButton(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.Transparent,
        textStyle = textStyle,
        iconTint = iconTint,
        modifier = modifier, text = text, icon = icon, maxLines = maxLines, enabled = enabled,
        enabledEffect = enabledEffect, space = space, padding = padding, onClick = onClick,
    )
}

@Composable
fun SurfaceFilledButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.button,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    space: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    padding: PaddingValues = PaddingValues(12.dp),
    onClick: () -> Unit,
) {
    FilledButton(
        shape = shape,
        backgroundColor = MaterialTheme.colors.surface,
        textStyle = textStyle,
        iconTint = iconTint,
        modifier = modifier, text = text, icon = icon, maxLines = maxLines, enabled = enabled,
        enabledEffect = enabledEffect, space = space, padding = padding, onClick = onClick,
    )
}