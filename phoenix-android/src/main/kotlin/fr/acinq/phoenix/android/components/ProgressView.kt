/*
 * Copyright 2022 ACINQ SAS
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProgressView(
    text: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    progressCircleSize: Dp = 20.dp,
    progressCircleWidth: Dp = 2.dp,
    space: Dp = 8.dp,
) {
    Row(
        modifier.padding(padding)
    ) {
        CircularProgressIndicator(Modifier.size(progressCircleSize), strokeWidth = progressCircleWidth)
        Spacer(Modifier.width(space))
        Text(text =text)
    }
}