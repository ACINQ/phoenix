/*
 * Copyright 2023 ACINQ SAS
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.mutedTextColor


@Composable
fun Dialog(
    onDismiss: () -> Unit,
    title: String? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    isScrollable: Boolean = true,
    buttonsTopMargin: Dp = 24.dp,
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
                Spacer(Modifier.height(buttonsTopMargin))
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
            .padding(vertical = 16.dp, horizontal = 16.dp) // min padding for tall/wide dialogs
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
    title: String?,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        Text(text = message)
    }
}

@Composable
fun ConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            Button(text = stringResource(id = R.string.btn_cancel), onClick = onDismiss)
            Button(text = stringResource(id = R.string.btn_confirm), onClick = onConfirm)
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = if (title == null) 24.dp else 0.dp)) {
            content()
        }
    }
}

@Composable
fun FullScreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun RowScope.IconPopup(
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.ic_help,
    iconSize: Dp = 20.dp,
    iconPadding: Dp = 2.dp,
    colorAtRest: Color = mutedTextColor,
    popupMessage: String,
    popupLink: Pair<String, String>? = null,
    spaceLeft: Dp? = 8.dp,
    spaceRight: Dp? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var showPopup by remember { mutableStateOf(false) }
    spaceLeft?.let { Spacer(Modifier.width(it)) }
    Box {
        BorderButton(
            icon = icon,
            iconTint = if (showPopup) MaterialTheme.colors.onPrimary else colorAtRest,
            backgroundColor = if (showPopup) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            borderColor = if (showPopup) MaterialTheme.colors.primary else colorAtRest,
            padding = PaddingValues(iconPadding),
            modifier = modifier.size(iconSize),
            interactionSource = interactionSource,
            onClick = { showPopup = true }
        )
        if (showPopup) {
            PopupDialog(onDismiss = { showPopup = false }, message = popupMessage, button = popupLink?.let { (text, link) ->
                { WebLink(text = text, url = link, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) }
            })
        }
    }
    spaceRight?.let { Spacer(Modifier.width(it)) }
}

@Composable
fun PopupDialog(
    onDismiss: () -> Unit,
    message: String,
    button: @Composable (() -> Unit)? = null,
) {
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(x = 0, y = 58),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 280.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.primary),
            elevation = 6.dp,
        ) {
            Column() {
                Text(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
                    text = message,
                    style = MaterialTheme.typography.body1.copy(fontSize = 14.sp)
                )
                button?.let {
                    it()
                } ?: run {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
