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

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import fr.acinq.phoenix.android.R

@Composable
fun InfoMessage(
    header: String,
    details: String,
    headerStyle: TextStyle = MaterialTheme.typography.body2,
    detailsStyle: TextStyle = MaterialTheme.typography.subtitle2,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    FeedbackMessage(
        header = header,
        details = details,
        headerStyle = headerStyle,
        detailsStyle = detailsStyle,
        icon = R.drawable.ic_info,
        iconColor = MaterialTheme.colors.primary,
        modifier = modifier,
        alignment = alignment,
    )
}