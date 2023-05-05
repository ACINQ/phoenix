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

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.negativeColor

@Composable
fun ErrorMessage(
    errorHeader: String,
    errorDetails: String? = null,
    errorColor: Color = negativeColor,
    padding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier.padding(padding),
        horizontalAlignment = alignment
    ) {
        TextWithIcon(
            text = errorHeader,
            icon = R.drawable.ic_alert_triangle,
            iconTint = errorColor,
            maxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
        if (errorDetails != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.component_error_message_details, errorDetails),
                modifier = Modifier.padding(start = 24.dp),
                style = MaterialTheme.typography.caption,
                textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
            )
        }
    }
}

// Same as above, but with annotated-string details that supports html markups.
@Composable
fun ErrorMessage(
    errorHeader: String,
    annotatedDetails: AnnotatedString,
    padding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier.padding(padding),
        horizontalAlignment = alignment
    ) {
        TextWithIcon(
            text = errorHeader,
            icon = R.drawable.ic_alert_triangle,
            iconTint = negativeColor,
            maxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = annotatedStringResource(id = R.string.component_error_message_details, annotatedDetails),
            modifier = Modifier.padding(start = 24.dp),
            style = MaterialTheme.typography.caption,
            textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
        )
    }
}