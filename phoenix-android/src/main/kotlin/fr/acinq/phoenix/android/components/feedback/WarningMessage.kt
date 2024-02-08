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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.orange

@Composable
fun WarningMessage(
    header: String,
    details: String,
    modifier: Modifier = Modifier,
    headerStyle: TextStyle = MaterialTheme.typography.body2,
    detailsStyle: TextStyle = MaterialTheme.typography.subtitle2,
    space: Dp = 8.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    FeedbackMessage(
        header = header,
        details = details,
        headerStyle = headerStyle,
        detailsStyle = detailsStyle,
        icon = R.drawable.ic_alert_triangle,
        iconColor = orange,
        space = space,
        padding = padding,
        modifier = modifier,
        alignment = alignment,
    )
}