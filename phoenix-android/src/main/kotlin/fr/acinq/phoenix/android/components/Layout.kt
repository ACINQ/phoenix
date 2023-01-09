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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.mutedTextColor


/** Button for navigation purpose, with the back arrow. */
@Composable
fun BackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = 0.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Unspecified,
            disabledBackgroundColor = Color.Unspecified,
            contentColor = MaterialTheme.colors.onSurface,
            disabledContentColor = mutedTextColor(),
        ),
        elevation = null,
        modifier = Modifier.size(width = 58.dp, height = 52.dp)
    ) {
        PhoenixIcon(resourceId = R.drawable.ic_arrow_back, Modifier.width(24.dp))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Dialog(
    onDismiss: () -> Unit,
    title: String? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    isScrollable: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = { Button(onClick = onDismiss, text = stringResource(id = R.string.btn_ok), padding = PaddingValues(16.dp)) },
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = properties) {
        DialogBody(isScrollable) {
            // optional title
            title?.run {
                Text(text = title, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.h4)
            }
            // content, must set the padding etc...
            content()
            // buttons
            if (buttons != null) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                ) {
                    buttons()
                }
            }
        }
    }
}

@Composable
fun DialogBody(
    isScrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .padding(vertical = 50.dp, horizontal = 16.dp) // min padding for tall/wide dialogs
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colors.surface)
            .widthIn(max = 600.dp)
            .then(
                if (isScrollable) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

@Composable
fun ConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismiss = onDismiss,
        buttons = {
            Button(text = stringResource(id = R.string.btn_cancel), onClick = onDismiss)
            Button(text = stringResource(id = R.string.btn_confirm), onClick = onConfirm)
        }
    ) {
        Text(text = message, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun RowScope.HelpPopup(
    message: String
) {
    var showHelpPopup by remember { mutableStateOf(false) }
    if (showHelpPopup) {
        Popup(
            onDismissRequest = { showHelpPopup = false },
            offset = IntOffset(x = 0, y = 68)
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 140.dp, max = 250.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = 4.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
                )
            }
        }
    }
    Spacer(Modifier.width(8.dp))
    BorderButton(
        icon = R.drawable.ic_help,
        iconTint = if (showHelpPopup) MaterialTheme.colors.onPrimary else mutedTextColor(),
        backgroundColor = if (showHelpPopup) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        borderColor = if (showHelpPopup) MaterialTheme.colors.primary else mutedTextColor(),
        padding = PaddingValues(2.dp),
        modifier = Modifier.size(20.dp),
        onClick = { showHelpPopup = true }
    )
}

/** The default screen is a full-height, full-width column with the material theme's background color. It is scrollable by default. */
@Composable
fun DefaultScreenLayout(
    isScrollable: Boolean = true,
    backgroundColor: Color = MaterialTheme.colors.background,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(backgroundColor)
            .then(if (isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        content()
    }
}

/** The default header of a screen contains a back button and an optional title. */
@Composable
fun DefaultScreenHeader(
    title: String? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colors.background,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 0.dp, top = 2.dp, bottom = 2.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBackClick)
        title?.let {
            Text(text = it)
        }
    }
}

@Composable
fun HSeparator(
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    Box(
        (width?.run { modifier.width(width) } ?: modifier.fillMaxWidth())
            .height(1.dp)
            .background(color = borderColor())
    )
}

@Composable
fun VSeparator(
    padding: PaddingValues = PaddingValues(0.dp)
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .padding(padding)
            .background(color = borderColor())
    )
}

@Composable
fun PrimarySeparator(
    modifier: Modifier = Modifier, width: Dp = 50.dp, height: Dp = 8.dp
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colors.primary,
        modifier = modifier
            .width(width)
            .height(height)
    ) { }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    externalPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    internalPadding: PaddingValues = PaddingValues(0.dp),
    shape: Shape = RoundedCornerShape(24.dp),
    withBorder: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxWidth: Dp = 500.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .padding(externalPadding)
            .widthIn(max = maxWidth)
            .clip(shape)
            .then(
                if (withBorder) Modifier.border(BorderStroke(ButtonDefaults.OutlinedBorderSize, MaterialTheme.colors.primary), shape) else Modifier
            )
            .background(MaterialTheme.colors.surface)
            .padding(internalPadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

fun Modifier.enableOrFade(enabled: Boolean): Modifier = this.then(Modifier.alpha(if (enabled) 1f else 0.3f))
