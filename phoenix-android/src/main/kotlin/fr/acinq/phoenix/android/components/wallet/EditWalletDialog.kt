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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.auth.pincode.PinDialogTitle
import fr.acinq.phoenix.android.components.auth.screenlock.CheckScreenLockPinFlow
import fr.acinq.phoenix.android.components.buttons.Checkbox
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.buttons.SwitchView
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.mutedTextColor
import kotlinx.coroutines.launch

@Composable
fun EditWalletDialog(
    walletId: WalletId,
    metadata: UserWalletMetadata,
    onDismiss: () -> Unit,
) {
    val userPrefs = LocalUserPrefs.current ?: return

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
        val defaultWalletInPref by globalPrefs.getDefaultWallet.collectAsState(null)
        val isDefaultWalletInPref = remember(defaultWalletInPref) { defaultWalletInPref == walletId }
        var isDefaultWalletInput by remember(isDefaultWalletInPref) { mutableStateOf(isDefaultWalletInPref) }

        var isHiddenWallet by remember { mutableStateOf(metadata.isHidden) }
        val isCustomPinLockEnabled by userPrefs.getLockPinEnabled.collectAsState(null)

        var showEnableHiddenWalletDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            AvatarPicker(
                avatar = avatarInput,
                onAvatarChange = { avatarInput = it },
            )
            Spacer(Modifier.height(24.dp))
            TextInput(
                text = nameInput,
                onTextChange = { nameInput = it.take(32) /* stealthy max chars */ },
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
                // this input is disabled if the wallet is not already the default, and it is hidden
                enabled = !(isHiddenWallet && !isDefaultWalletInPref),
                checked = isDefaultWalletInput,
                onCheckedChange = { isDefaultWalletInput = !isDefaultWalletInput }
            )
            Spacer(Modifier.height(8.dp))
            SwitchView(
                text = stringResource(R.string.wallet_edit_secret_label),
                description = if (isCustomPinLockEnabled == false) stringResource(R.string.wallet_edit_secret_description_disabled) else stringResource(R.string.wallet_edit_secret_description),
                checked = isHiddenWallet,
                enabled = isCustomPinLockEnabled == true,
                onCheckedChange = {
                    if (!isHiddenWallet) {
                        showEnableHiddenWalletDialog = true
                    } else {
                        isHiddenWallet = false
                    }
                }
            )
        }

        val saveChanges: (Boolean) -> Unit = { alsoDismiss ->
            scope.launch {
                globalPrefs.saveAvailableWalletMeta(walletId = walletId, name = nameInput.takeIf { it.isNotBlank() }, avatar = avatarInput, isHidden = isHiddenWallet)
                when {
                    // we only update the default preferences when relevant: goes from default to not ; or goes from not default to default wallet.
                    isDefaultWalletInPref && !isDefaultWalletInput -> globalPrefs.clearDefaultWallet()
                    // if the wallet is hidden, it cannot be default
                    isDefaultWalletInPref && isHiddenWallet -> globalPrefs.clearDefaultWallet()
                    isDefaultWalletInput -> globalPrefs.saveDefaultWallet(walletId)
                }
                if (alsoDismiss) {
                    onDismiss()
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).align(Alignment.End)) {
            FilledButton(
                text = stringResource(R.string.btn_save),
                icon = R.drawable.ic_check,
                enabled = nameInput != (metadata.name ?: "") || avatarInput != metadata.avatar || isDefaultWalletInput != isDefaultWalletInPref || isHiddenWallet != metadata.isHidden,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = { saveChanges(true) },
            )

            Spacer(Modifier.height(16.dp))
            TransparentFilledButton(
                text = stringResource(R.string.btn_close),
                textStyle = MaterialTheme.typography.caption,
                icon = R.drawable.ic_cross,
                iconTint = MaterialTheme.typography.caption.color,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (showEnableHiddenWalletDialog) {
            EnabledHiddenOptionDialog(
                walletId = walletId,
                onConfirm = { isHiddenWallet = true ; saveChanges(false) },
                onDismiss = { showEnableHiddenWalletDialog = false },
            )
        }
    }
}

@Composable
private fun EnabledHiddenOptionDialog(walletId: WalletId, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colors.background,
        internalPadding = PaddingValues(0.dp),
        skipPartiallyExpanded = true,
        scrimAlpha = .4f
    ) {
        var isCheckingLockPin by remember { mutableStateOf(false) }

        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = stringResource(R.string.wallet_hide_instructions_1))
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.wallet_hide_instructions_2))
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.wallet_hide_instructions_3))
            Spacer(Modifier.height(16.dp))

            var hasAckedCheckbox by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = mutedBgColor) {
                Checkbox(text = stringResource(R.string.utils_ack), checked = hasAckedCheckbox, onCheckedChange = { hasAckedCheckbox = it }, padding = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))
            FilledButton(text = stringResource(R.string.wallet_hide_confirm_button), onClick = { isCheckingLockPin = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = hasAckedCheckbox)
            Spacer(Modifier.height(12.dp))
            TransparentFilledButton(text = stringResource(R.string.wallet_hide_cancel_button), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }

        if (isCheckingLockPin) {
            CheckScreenLockPinFlow(
                walletId = walletId,
                acceptHiddenPin = false,
                onCancel = { isCheckingLockPin = false },
                onPinValid = { onConfirm() ; onDismiss() },
                prompt = { PinDialogTitle(text = stringResource(id = R.string.pincode_check_disabling_title)) }
            )
        }
    }
}
