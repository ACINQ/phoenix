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

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.utils.BlockchainExplorer


/** A rounded button with a solid surface background and a muted outline. */
@Composable
fun BorderButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = MaterialTheme.colors.surface,
    borderColor: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true,
    space: Dp = 12.dp,
    textStyle: TextStyle = MaterialTheme.typography.button,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onClick: () -> Unit,
) {
    Button(
        text = text,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        space = space,
        onClick = onClick,
        shape = CircleShape,
        backgroundColor = backgroundColor,
        border = BorderStroke(ButtonDefaults.OutlinedBorderSize, borderColor),
        textStyle = textStyle,
        padding = padding,
        modifier = modifier
    )
}

/** A rounded button with a solid background using app theme's primary color. */
@Composable
fun FilledButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Int? = null,
    iconTint: Color = MaterialTheme.colors.onPrimary,
    enabled: Boolean = true,
    space: Dp = 12.dp,
    shape: Shape = CircleShape,
    textStyle: TextStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary),
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    backgroundColor: Color = MaterialTheme.colors.primary,
    onClick: () -> Unit,
) {
    Button(
        text = text,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        space = space,
        onClick = onClick,
        shape = shape,
        backgroundColor = backgroundColor,
        textStyle = textStyle,
        padding = padding,
        modifier = modifier
    )
}

@Composable
fun InlineButton(
    text: String,
    icon: Int,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    space: Dp = 6.dp,
    onClick: () -> Unit,
) {
    Button(
        text = text,
        icon = icon,
        modifier = modifier,
        padding = padding,
        space = space,
        onClick = onClick,
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary, fontSize = 14.sp, textDecoration = TextDecoration.Underline)
    )
}

/** For annotated string text param. */
@Composable
fun TextWithIcon(
    text: AnnotatedString,
    icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    textOverflow: TextOverflow = TextOverflow.Clip,
    iconTint: Color = LocalContentColor.current,
    iconSize: Dp = ButtonDefaults.IconSize,
    padding: PaddingValues = PaddingValues(0.dp),
    space: Dp = 6.dp,
    alignBaseLine: Boolean = false
) {
    Row(
        modifier = modifier.padding(padding),
        verticalAlignment = if (alignBaseLine) Alignment.Top else Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = "icon for $text",
            modifier = Modifier
                .size(iconSize)
                .then(if (alignBaseLine) Modifier.alignBy(FirstBaseline) else Modifier),
            colorFilter = ColorFilter.tint(iconTint)
        )
        Spacer(Modifier.width(space))
        Text(text, style = textStyle, modifier = if (alignBaseLine) Modifier.alignBy(FirstBaseline) else Modifier, maxLines = maxLines, overflow = textOverflow)
    }
}

@Composable
fun TextWithIcon(
    text: String,
    icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    textOverflow: TextOverflow = TextOverflow.Clip,
    iconTint: Color? = null,
    iconSize: Dp = ButtonDefaults.IconSize,
    padding: PaddingValues = PaddingValues(0.dp),
    space: Dp = 6.dp,
    alignBaseLine: Boolean = false
) {
    Row(
        modifier = modifier.padding(padding),
        verticalAlignment = if (alignBaseLine) Alignment.Top else Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = "icon for $text",
            modifier = Modifier
                .size(iconSize)
                .then(if (alignBaseLine) Modifier.alignBy(FirstBaseline) else Modifier),
            colorFilter = iconTint?.let { ColorFilter.tint(it) }
        )
        Spacer(Modifier.width(space))
        Text(text, style = textStyle, modifier = if (alignBaseLine) Modifier.alignBy(FirstBaseline) else Modifier, maxLines = maxLines, overflow = textOverflow)
    }
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
    space: Dp = 12.dp,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.button,
    padding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RectangleShape,
    border: BorderStroke? = null,
    elevation: ButtonElevation? = null,
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
            .clickable(
                onClick = onClick,
                onClickLabel = onClickLabel,
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
                            minWidth = 42.dp,
                            minHeight = 0.dp
                        )
                        .indication(interactionSource, LocalIndication.current)
                        .padding(padding)
                        .alpha(
                            if (!enabled && enabledEffect) {
                                if (backgroundColor == Color.Unspecified) 0.3f else 0.8f
                            } else {
                                1f
                            }
                        ),
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        if (text != null && icon != null) {
                            TextWithIcon(text = text, icon = icon, iconTint = iconTint, space = space, alignBaseLine = true)
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
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.button,
    backgroundColor: Color = Color.Unspecified, // transparent by default!
    clickDescription: String = "",
    internalPadding: PaddingValues = PaddingValues(0.dp),
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
                enabled = enabled,
                role = Role.Button,
                onClickLabel = clickDescription,
            )
            .padding(internalPadding)
    ) {
        CompositionLocalProvider(LocalContentAlpha provides contentColor.alpha) {
            ProvideTextStyle(value = textStyle) {
                content()
            }
        }
    }
}

@Composable
fun WebLink(
    text: String,
    url: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current
    Text(
        modifier = modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(id = R.string.accessibility_link),
            onClick = { openLink(context, url) }
        ),
        text = text,
        overflow = overflow,
        maxLines = maxLines,
        style = MaterialTheme.typography.body1.copy(textDecoration = TextDecoration.Underline, color = MaterialTheme.colors.primary)
    )
}

@Composable
fun txUrl(txId: String): String {
    return business.blockchainExplorer.txUrl(txId = txId, website = BlockchainExplorer.Website.MempoolSpace)
}

fun openLink(context: Context, link: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
}
