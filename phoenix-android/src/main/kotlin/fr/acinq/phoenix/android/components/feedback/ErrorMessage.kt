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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.negativeColor

@Composable
fun ErrorMessage(
    header: String,
    details: CharSequence? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    FeedbackMessage(
        header = header,
        details = when (details) {
            is AnnotatedString -> annotatedStringResource(id = R.string.component_error_message_details, details)
            else -> stringResource(id = R.string.component_error_message_details)
        },
        icon = R.drawable.ic_alert_triangle,
        iconColor = negativeColor,
        modifier = modifier,
        padding = padding,
        alignment = alignment,
    )
}
