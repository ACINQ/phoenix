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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextWithIcon

/**
 * A very customisable button, by default using a square shape with a transparent bg.
 *
 * Most of the code is directly taken from Material's implementation of contained button: cf: [androidx.compose.material.ButtonKt.Button]
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    iconSize: Dp = ButtonDefaults.IconSize,
    space: Dp = 12.dp,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.button,
    padding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RectangleShape,
    border: BorderStroke? = null,
    elevation: ButtonElevation? = null,
    role: Role = Role.Button,
    backgroundColor: Color = Color.Unspecified, // transparent by default!
    onClickLabel: String? = null,
) {
    val colors = ButtonDefaults.buttonColors(
        backgroundColor = backgroundColor,
        disabledBackgroundColor = if (enabledEffect && backgroundColor != Color.Unspecified) backgroundColor.copy(alpha = 0.4f) else backgroundColor,
    )
    val contentColor by colors.contentColor(true)
    Surface(
        shape = shape,
        color = colors.backgroundColor(enabled).value,
        contentColor = colors.contentColor(enabled).value,
        border = border,
        elevation = elevation?.elevation(enabled, interactionSource)?.value ?: 0.dp,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onClickLabel = onClickLabel,
                onLongClick = onLongClick,
                onLongClickLabel = null,
                onDoubleClick = null,
                enabled = enabled,
                role = role,
                interactionSource = interactionSource,
                indication = null, // use [LocalIndication.current] to ignore the button's shape.
            )
            .then(
                if (text == null && icon != null) {
                    Modifier.defaultMinSize(42.dp)
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalContentAlpha provides contentColor.alpha) {
            ProvideTextStyle(value = textStyle) {
                Row(
                    Modifier
                        .defaultMinSize(
                            minWidth = 0.dp,
                            minHeight = 0.dp
                        )
                        .indication(interactionSource, LocalIndication.current)
                        .padding(padding)
                        .alpha(
                            if (!enabled && enabledEffect) {
                                if (backgroundColor == Color.Unspecified) 0.3f else 0.65f
                            } else {
                                1f
                            }
                        ),
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        if (text != null && icon != null) {
                            TextWithIcon(
                                text = text,
                                icon = icon, iconTint = iconTint, iconSize = iconSize,
                                space = space,
                                maxLines = maxLines, textOverflow = TextOverflow.Ellipsis
                            )
                        } else if (text != null) {
                            Text(text = text, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                        } else if (icon != null) {
                            PhoenixIcon(resourceId = icon, tint = iconTint)
                        }
                    }
                )
            }
        }
    }
}