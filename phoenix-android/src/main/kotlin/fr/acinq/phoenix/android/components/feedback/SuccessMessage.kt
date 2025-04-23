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
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.positiveColor

@Composable
fun SuccessMessage(
    header: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    alignment: Alignment.Horizontal = Alignment.Start,
    padding: PaddingValues = PaddingValues(16.dp),
) {
    FeedbackMessage(
        header = header,
        details = details,
        icon = R.drawable.ic_check_circle,
        iconColor = positiveColor,
        modifier = modifier,
        alignment = alignment,
        padding = padding
    )
}