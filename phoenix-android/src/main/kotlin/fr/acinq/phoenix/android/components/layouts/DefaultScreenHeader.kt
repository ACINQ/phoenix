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

package fr.acinq.phoenix.android.components.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.buttons.BackButton
import fr.acinq.phoenix.android.components.dialogs.IconPopup

/** The default header of a screen contains a back button and an optional title. */
@Composable
fun DefaultScreenHeader(
    title: String? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
) {
    DefaultScreenHeader(
        content = {
            title?.let { Text(text = it) }
        },
        onBackClick = onBackClick,
        backgroundColor = backgroundColor
    )
}

/** A header with a back button, a title, and a help button. */
@Composable
fun DefaultScreenHeader(
    title: String,
    helpMessage: String,
    helpMessageLink: Pair<String, String>? = null,
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
) {
    DefaultScreenHeader(
        content = {
            Row {
                Text(text = title)
                Spacer(modifier = Modifier.width(4.dp))
                IconPopup(popupMessage = helpMessage, popupLink = helpMessageLink)
            }
        },
        onBackClick = onBackClick,
        backgroundColor = backgroundColor
    )
}

/** The default header of a screen contains a back button and some content. */
@Composable
fun DefaultScreenHeader(
    onBackClick: () -> Unit,
    backgroundColor: Color = Color.Transparent,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 0.dp, top = 2.dp, bottom = 2.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBackClick)
        content()
    }
}