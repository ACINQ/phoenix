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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import fr.acinq.phoenix.android.components.buttons.SwitchView
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.mutedTextColor
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
        var avatarInput by remember { mutableStateOf(metadata.avatar) }
        val defaultWalletPref by globalPrefs.getDefaultNodeId.collectAsState(null)
        val isDefaultWalletInPref = remember(defaultWalletPref) { defaultWalletPref == nodeId }
        var isDefaultWalletInput by remember(isDefaultWalletInPref) { mutableStateOf(isDefaultWalletInPref) }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            AvatarPicker(
                avatar = avatarInput,
                onAvatarChange = { avatarInput = it },
            )
            Spacer(Modifier.height(24.dp))
            TextInput(
                text = nameInput,
                onTextChange = { nameInput = it.take(48) /* stealthy max chars */ },
                textStyle = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center),
                placeholder = { Text(text = stringResource(R.string.contact_name_hint), style = MaterialTheme.typography.h3.copy(textAlign = TextAlign.Center, color = mutedTextColor), modifier = Modifier.fillMaxWidth()) },
                staticLabel = null,
                showResetButton = true,
            )
        }

        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            SwitchView(
                text = stringResource(R.string.wallet_edit_default_label),
                description = stringResource(R.string.wallet_edit_default_description),
                checked = isDefaultWalletInput,
                onCheckedChange = { isDefaultWalletInput = !isDefaultWalletInput }
            )
        }
        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            SwitchView(
                text = stringResource(R.string.wallet_edit_secret_label),
                description = stringResource(R.string.wallet_edit_secret_description),
                checked = false,
                onCheckedChange = { }
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp).align(Alignment.End)) {
            FilledButton(
                text = stringResource(R.string.btn_save),
                icon = R.drawable.ic_check,
                enabled = nameInput != (metadata.name ?: "") || avatarInput != metadata.avatar || isDefaultWalletInput != isDefaultWalletInPref,
                onClick = {
                    scope.launch {
                        globalPrefs.saveAvailableWalletMeta(nodeId = nodeId, name = nameInput.takeIf { it.isNotBlank() }, avatar = avatarInput)
                        when {
                            // we only update the default preferences when relevant: goes from default to not ; or goes from not default to default wallet.
                            defaultWalletPref == nodeId && !isDefaultWalletInput -> globalPrefs.clearDefaultNodeId()
                            isDefaultWalletInput -> globalPrefs.saveDefaultNodeId(nodeId)
                        }
                        onDismiss()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            TransparentFilledButton(
                text = stringResource(R.string.btn_cancel),
                textStyle = MaterialTheme.typography.caption,
                icon = R.drawable.ic_cross,
                iconTint = MaterialTheme.typography.caption.color,
                onClick = onDismiss,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
