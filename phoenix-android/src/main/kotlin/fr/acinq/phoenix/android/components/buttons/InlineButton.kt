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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Button that looks like an inline link, should fit in with regular text views. */
@Composable
fun InlineButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    fontSize: TextUnit = MaterialTheme.typography.body1.fontSize,
    iconSize: Dp = ButtonDefaults.IconSize,
    padding: PaddingValues = PaddingValues(horizontal = 2.dp, vertical = 1.dp),
    space: Dp = 6.dp,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Button(
        text = text,
        icon = icon,
        iconSize = iconSize,
        modifier = modifier,
        padding = padding,
        space = space,
        onClickLabel = onClickLabel,
        onClick = onClick,
        onLongClick = onLongClick,
        backgroundColor = Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.primary, fontSize = fontSize, textDecoration = TextDecoration.Underline),
        horizontalArrangement = Arrangement.Start,
    )
}