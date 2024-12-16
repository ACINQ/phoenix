/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.home.releasenotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.internalData
import kotlinx.coroutines.launch


@Composable
fun ReleaseNoteDialog(sinceCode: Int) {
    val scope = rememberCoroutineScope()
    val internalData = internalData
    Dialog(onDismiss = {
        scope.launch { internalData.saveShowReleaseNoteSinceCode(null) }
    }) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sinceCode <= 98) {
                Text(text = stringResource(id = R.string.notes_code_99_title), style = MaterialTheme.typography.h4)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.notes_code_99_body))
            }
        }
    }
}
