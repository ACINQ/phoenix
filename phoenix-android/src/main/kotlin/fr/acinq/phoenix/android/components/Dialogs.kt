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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.mutedTextColor

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

@OptIn(ExperimentalComposeUiApi::class)
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
        iconTint = if (showHelpPopup) MaterialTheme.colors.onPrimary else mutedTextColor,
        backgroundColor = if (showHelpPopup) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        borderColor = if (showHelpPopup) MaterialTheme.colors.primary else mutedTextColor,
        padding = PaddingValues(2.dp),
        modifier = Modifier.size(20.dp),
        onClick = { showHelpPopup = true }
    )
}