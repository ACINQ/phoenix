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

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/** A rounded button with a solid surface background and a muted outline. */
@Composable
fun BorderButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    shape: Shape = CircleShape,
    backgroundColor: Color = MaterialTheme.colors.surface,
    borderColor: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    space: Dp = 12.dp,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.button,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
) {
    Button(
        text = text,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        enabledEffect = enabledEffect,
        space = space,
        onClick = onClick,
        shape = shape,
        backgroundColor = backgroundColor,
        border = BorderStroke(ButtonDefaults.OutlinedBorderSize, if (enabled) borderColor else borderColor.copy(alpha = 0.35f)),
        textStyle = textStyle,
        padding = padding,
        maxLines = maxLines,
        interactionSource = interactionSource,
        modifier = modifier
    )
}





