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

package fr.acinq.phoenix.android.components.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.TextWithIcon

@Composable
fun FeedbackMessage(
    header: String,
    icon: Int,
    iconColor: Color,
    modifier: Modifier = Modifier,
    details: CharSequence? = null,
    iconSize: Dp = 20.dp,
    space: Dp = 8.dp,
    headerStyle: TextStyle = MaterialTheme.typography.body2,
    detailsStyle: TextStyle = MaterialTheme.typography.subtitle2,
    padding: PaddingValues = PaddingValues(16.dp),
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier.padding(padding),
        horizontalAlignment = alignment
    ) {
        TextWithIcon(
            text = header,
            textStyle = headerStyle,
            icon = icon,
            iconSize = iconSize,
            iconTint = iconColor,
            space = space,
            modifier = if (alignment == Alignment.CenterHorizontally) Modifier.widthIn(max = 250.dp) else Modifier,
        )
        if (details != null) {
            Spacer(modifier = Modifier.height(2.dp))
            val mod = if (alignment == Alignment.Start) {
                Modifier.padding(start = space + iconSize)
            } else {
                Modifier
            }
            when (details) {
                is AnnotatedString -> {
                    Text(
                        text = details,
                        modifier = mod,
                        style = detailsStyle,
                        textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
                    )
                }
                else -> {
                    Text(
                        text = details.toString(),
                        modifier = mod,
                        style = detailsStyle,
                        textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
                    )
                }
            }
        }
    }
}