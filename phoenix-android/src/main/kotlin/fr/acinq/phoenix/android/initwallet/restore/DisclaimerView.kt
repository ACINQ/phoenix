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

package fr.acinq.phoenix.android.initwallet.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.Checkbox

@Composable
fun DisclaimerView(
    onClickNext: () -> Unit
) {
    var hasCheckedWarning by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(stringResource(R.string.restore_disclaimer_message_1_title), style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(R.string.restore_disclaimer_message_1_body))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.restore_disclaimer_message_2_title), style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(R.string.restore_disclaimer_message_2_body))
        }
        Checkbox(
            text = stringResource(R.string.utils_ack),
            checked = hasCheckedWarning,
            onCheckedChange = { hasCheckedWarning = it },
        )
        BorderButton(
            text = stringResource(id = R.string.restore_disclaimer_next),
            icon = R.drawable.ic_arrow_next,
            onClick = (onClickNext),
            enabled = hasCheckedWarning,
        )
    }
}