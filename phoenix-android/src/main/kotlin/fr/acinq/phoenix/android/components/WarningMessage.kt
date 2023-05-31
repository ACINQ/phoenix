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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.orange

@Composable
fun WarningMessage(
    header: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextWithIcon(
            text = header,
            textStyle = MaterialTheme.typography.body2,
            space = 12.dp,
            icon = R.drawable.ic_alert_triangle,
            iconTint = orange,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = details,
            style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
            modifier = Modifier.padding(start = 30.dp)
        )
    }
}