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

package fr.acinq.phoenix.android.components.wallet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import kotlinx.coroutines.launch

@Composable
fun EditWalletDialog(
    nodeId: String,
    metadata: UserWalletMetadata,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colors.background,
        internalPadding = PaddingValues(0.dp),
        skipPartiallyExpanded = true,
    ) {
        val scope = rememberCoroutineScope()
        val globalPrefs = globalPrefs
        var nameInput by remember { mutableStateOf(metadata.name ?: "") }

        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = "Wallet Details", style = MaterialTheme.typography.h4, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            TextInput(
                text = nameInput,
                onTextChange = { nameInput = it },
                maxLines = 1,
                singleLine = true,
                placeholder = { Text(text = "Enter a name for your wallet", style = MaterialTheme.typography.caption) },
                staticLabel = "Name"
            )
            Spacer(Modifier.height(16.dp))
            TextInput(
                text = nodeId,
                onTextChange = { },
                maxLines = 4,
                enabled = false,
                enabledEffect = false,
                staticLabel = "NodeId",
                textStyle = MaterialTheme.typography.caption,
                showResetButton = false
            )
            Spacer(Modifier.height(24.dp))
            FilledButton(
                text = stringResource(R.string.btn_save),
                icon = R.drawable.ic_check,
                onClick = {
                    scope.launch {
                        globalPrefs.saveAvailableWalletMeta(name = nameInput.takeIf { it.isNotBlank() }, nodeId = nodeId)
                        onDismiss()
                    }
                },
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
