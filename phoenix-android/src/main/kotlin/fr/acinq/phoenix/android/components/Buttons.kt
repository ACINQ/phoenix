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

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.borderColor
import fr.acinq.phoenix.android.mutedTextColor


/** A rounded button with a solid background and a muted outline. */
@Composable
fun BorderButton(
    modifier: Modifier = Modifier,
    text: Int? = null,
    icon: Int? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.button,
    space: Dp = 16.dp,
    padding: PaddingValues = PaddingValues(12.dp)
) {
    Button(
        text = text?.run { stringResource(this) },
        icon = icon,
        enabled = enabled,
        space = space,
        onClick = onClick,
        shape = CircleShape,
        backgroundColor = MaterialTheme.colors.surface,
        border = BorderStroke(ButtonDefaults.OutlinedBorderSize, borderColor()),
        textStyle = textStyle,
        padding = padding,
        modifier = modifier
    )
}

/** A rounded button with a solid background. */
@Composable
fun FilledButton(
    modifier: Modifier = Modifier,
    text: Int? = null,
    icon: Int? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    space: Dp = 16.dp,
    textStyle: TextStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary),
    padding: PaddingValues = PaddingValues(12.dp),
    backgroundColor: Color = MaterialTheme.colors.primary,
) {
    Button(
        text = text?.run { stringResource(this) },
        icon = icon,
        iconTint = textStyle.color, // icon has the same colors as the text
        enabled = enabled,
        space = space,
        onClick = onClick,
        shape = CircleShape,
        backgroundColor = backgroundColor,
        textStyle = textStyle,
        padding = padding,
        modifier = modifier
    )
}

@Composable
fun IconWithText(icon: Int, text: String, iconTint: Color = LocalContentColor.current, space: Dp = 16.dp) {
    PhoenixIcon(icon, Modifier.size(ButtonDefaults.IconSize), iconTint)
    Spacer(Modifier.width(space))
    Text(text)
}

@Composable
fun PhoenixIcon(
    resourceId: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Image(
        painter = painterResource(id = resourceId),
        contentDescription = "icon",
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

@Composable
fun SettingButton(
    text: Int,
    icon: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        text = stringResource(id = text),
        icon = icon,
        iconTint = MaterialTheme.colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    )
}

/**
 * A very customisable button, by default using a square shape with a transparent bg.
 *
 * Most of the code is directly taken from Material's implementation of contained button: cf: [androidx.compose.material.ButtonKt.Button]
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    space: Dp = 16.dp,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.button,
    padding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RectangleShape,
    border: BorderStroke? = null, // ButtonDefaults.outlinedBorder,
    elevation: ButtonElevation? = null,
    backgroundColor: Color = Color.Unspecified, // transparent by default!
    disabledBackgroundColor: Color = MaterialTheme.colors.background,
) {
    val colors = ButtonDefaults.buttonColors(
        backgroundColor = backgroundColor,
        disabledBackgroundColor = disabledBackgroundColor,
        contentColor = LocalContentColor.current,
        disabledContentColor = mutedTextColor(),
    )
    val contentColor by colors.contentColor(enabled)
    Surface(
        shape = shape,
        color = colors.backgroundColor(enabled).value,
        contentColor = contentColor.copy(1f),
        border = border,
        elevation = elevation?.elevation(enabled, interactionSource)?.value ?: 0.dp,
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
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
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = 0.dp
                        )
                        .indication(interactionSource, LocalIndication.current)
                        .padding(padding),
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        if (text != null && icon != null) {
                            IconWithText(icon, text, iconTint, space)
                        } else if (text != null) {
                            Text(text)
                        } else if (icon != null) {
                            PhoenixIcon(icon, tint = iconTint)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun Clickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.button,
    backgroundColor: Color = Color.Unspecified, // transparent by default!
    content: @Composable () -> Unit,
) {
    val colors = ButtonDefaults.buttonColors(
        backgroundColor = backgroundColor,
        contentColor = LocalContentColor.current,
    )
    val contentColor by colors.contentColor(true)
    Surface(
        shape = RectangleShape,
        contentColor = contentColor,
        elevation = 0.dp,
        modifier = modifier
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
    ) {
        CompositionLocalProvider(LocalContentAlpha provides contentColor.alpha) {
            ProvideTextStyle(value = textStyle) {
                content()
            }
        }
    }
}