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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.borderColor
import fr.acinq.phoenix.android.mutedBgColor
import fr.acinq.phoenix.android.mutedTextColor


@Composable
fun ScreenHeader(
    title: String? = null,
    subtitle: String? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = mutedBgColor(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BackButton(onClick = onBackClick)
        Column(
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            title?.run { Text(text = this) }
            subtitle?.run {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = this, style = TextStyle(color = mutedTextColor(), fontSize = 14.sp))
            }
        }
    }
}

@Composable
fun ScreenBody(
    modifier: Modifier = Modifier.padding(PaddingValues(start = 50.dp, top = 16.dp, bottom = 16.dp, end = 24.dp)),
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
    ) {
        HSeparator()
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
        HSeparator()
    }
}

/** Button for navigation purpose, with the back arrow. */
@Composable
fun BackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(16.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Unspecified,
            disabledBackgroundColor = Color.Unspecified,
            contentColor = LocalContentColor.current,
            disabledContentColor = mutedTextColor(),
        ),
        elevation = null,
        modifier = Modifier.size(50.dp)
    ) {
        PhoenixIcon(resourceId = R.drawable.ic_arrow_back)
    }
}

@Composable
fun Dialog(
    onDismiss: () -> Unit,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    buttons: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = properties) {
        Column(
            Modifier
                .padding(vertical = 50.dp) // vertical padding for tall dialogs
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colors.surface)
        ) {
            // optional title
            title?.run {
                Text(text = title, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.subtitle2.copy(fontSize = 20.sp))
            }
            // content, must set the padding etc...
            content()
            // buttons
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (buttons != null) {
                    buttons()
                } else {
                    Button(onClick = onDismiss, text = stringResource(id = R.string.btn_ok), padding = PaddingValues(16.dp))
                }
            }
        }
    }
}

@Composable
fun HSeparator(
    padding: PaddingValues = PaddingValues(0.dp)
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(padding)
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

fun Modifier.enableOrFade(enabled: Boolean): Modifier = this.then(Modifier.alpha(if (enabled) 1f else 0.3f))
