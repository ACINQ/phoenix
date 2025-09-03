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

package fr.acinq.phoenix.android.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/** For annotated string text param. */
@Composable
fun TextWithIcon(
    text: AnnotatedString,
    icon: Int,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    textOverflow: TextOverflow = TextOverflow.Clip,
    iconTint: Color = MaterialTheme.colors.onSurface,
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
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    Row(
        modifier = modifier.padding(padding),
        verticalAlignment = verticalAlignment
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = "icon for $text",
            modifier = Modifier
                .size(iconSize)
                .then(if (verticalAlignment == Alignment.Top) Modifier.offset(y = 2.dp) else Modifier),
            colorFilter = iconTint?.let { ColorFilter.tint(it) }
        )
        Spacer(Modifier.width(space))
        Text(text, style = textStyle, maxLines = maxLines, overflow = textOverflow)
    }
}

@Composable
fun PhoenixIcon(
    resourceId: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colors.onSurface
) {
    Image(
        painter = painterResource(id = resourceId),
        contentDescription = "icon",
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}