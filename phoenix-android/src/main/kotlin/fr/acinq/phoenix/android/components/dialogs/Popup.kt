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

package fr.acinq.phoenix.android.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.WebLink
import fr.acinq.phoenix.android.utils.mutedTextColor


@Composable
fun IconTextPopup(
    text: String,
    icon: Int,
    iconTint: Color = MaterialTheme.colors.primary,
    iconSize: Dp = 16.dp,
    iconPadding: Dp = 5.dp,
    textStyle: TextStyle,
    popupMessage: String,
    popupLink: Pair<String, String>? = null,
) {
    var showPopup by remember { mutableStateOf(false) }
    Clickable(
        onClick = { showPopup = true },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (showPopup) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                border = BorderStroke(width = 1.dp, color = if (showPopup) MaterialTheme.colors.primary else iconTint)
            ) {
                Box(modifier = Modifier.padding(iconPadding)) {
                    PhoenixIcon(icon, modifier = Modifier.size(iconSize), tint = if (showPopup) MaterialTheme.colors.onPrimary else iconTint)
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(text = text, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (showPopup) {
                PopupDialog(onDismiss = { showPopup = false }, message = popupMessage, button = popupLink?.let { (text, link) ->
                    { WebLink(text = text, url = link, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) }
                })
            }
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var showPopup by remember { mutableStateOf(false) }
    spaceLeft?.let { Spacer(Modifier.requiredWidth(it)) }
    BorderButton(
        icon = icon,
        iconTint = if (showPopup) MaterialTheme.colors.onPrimary else colorAtRest,
        backgroundColor = if (showPopup) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        borderColor = if (showPopup) MaterialTheme.colors.primary else colorAtRest,
        padding = PaddingValues(iconPadding),
        modifier = modifier.requiredSize(iconSize),
        interactionSource = interactionSource,
        onClick = { showPopup = true },
    )
    if (showPopup) {
        PopupDialog(onDismiss = { showPopup = false }, message = popupMessage, button = popupLink?.let { (text, link) ->
            { WebLink(text = text, url = link, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) }
        })
    }
    spaceRight?.let { Spacer(Modifier.requiredWidth(it)) }
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