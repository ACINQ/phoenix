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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.components.dialogs.IconPopup

@Composable
fun Card(
    modifier: Modifier = Modifier,
    externalPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    internalPadding: PaddingValues = PaddingValues(0.dp),
    shape: Shape = RoundedCornerShape(10.dp),
    withBorder: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    backgroundColor: Color = MaterialTheme.colors.surface,
    maxWidth: Dp = 500.dp,
    onClick: (() -> Unit)? = null,
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
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        role = Role.Button,
                        onClickLabel = null,
                    )
                } else {
                    Modifier
                }
            )
            .background(backgroundColor)
            .padding(internalPadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

/** Used for items in a lazy column, in order to mimic the [Card] look. */
@Composable
fun ItemCard(
    index: Int,
    maxItemsCount: Int,
    externalPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    internalPadding: PaddingValues = PaddingValues(0.dp),
    roundCornerSize: Dp = 10.dp,
    maxWidth: Dp = 500.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable () -> Unit
) {
    Surface(modifier = Modifier
        .padding(externalPadding)
        .widthIn(max = maxWidth)
        .clip(when {
            index == 0 && maxItemsCount == 1 -> RoundedCornerShape(roundCornerSize)
            index == 0 -> RoundedCornerShape(topStart = roundCornerSize, topEnd = roundCornerSize)
            index == maxItemsCount - 1 -> RoundedCornerShape(bottomStart = roundCornerSize, bottomEnd = roundCornerSize)
            else -> RectangleShape
        })
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick, role = Role.Button, onClickLabel = onClickLabel)
            } else {
                Modifier
            }
        )
        .background(MaterialTheme.colors.surface)
        .padding(internalPadding),
    ) {
        content()
    }
}

@Composable
fun CardHeader(
    modifier: Modifier = Modifier,
    text: String,
    padding: PaddingValues = PaddingValues(horizontal = 28.dp)
) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = text.uppercase(),
        modifier = modifier
            .fillMaxWidth()
            .padding(padding),
        style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, color = MaterialTheme.colors.primary)
    )
}

@Composable
fun CardHeaderWithHelp(
    text: String,
    helpMessage: String,
    padding: PaddingValues = PaddingValues(start = 28.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .indication(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            )
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.subtitle1.copy(fontSize = 12.sp, color = MaterialTheme.colors.primary)
        )
        IconPopup(popupMessage = helpMessage)
    }
}