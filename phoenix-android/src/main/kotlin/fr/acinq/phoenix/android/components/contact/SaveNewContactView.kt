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

package fr.acinq.phoenix.android.components.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.TextInput


/**
 * A contact detail is a bottom sheet dialog that display the contact's name, photo, and
 * associated offers/lnids.
 *
 * The contact can be edited and deleted from that screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveNewContactDialog(
    offer: OfferTypes.Offer,
    onDismiss: () -> Unit,
    onSave: (String, ByteArray?, OfferTypes.Offer) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        scrimColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Attach a contact to this invoice", style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            ContactPhotoView(image = photo, name = name, onChange = { photo = it })
            Spacer(modifier = Modifier.height(24.dp))
            TextInput(
                text = name,
                onTextChange = { name = it },
                textStyle = MaterialTheme.typography.h3,
                staticLabel = stringResource(id = R.string.contact_name_label),
            )

            TextInput(
                text = offer.encode(),
                onTextChange = {},
                enabled = false,
                enabledEffect = false,
                staticLabel = stringResource(id = R.string.contact_offer_label),
                maxLines = 4,
                showResetButton = false
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledButton(
                text = stringResource(id = R.string.contact_add_contact),
                icon = R.drawable.ic_check,
                onClick = { onSave(name, photo, offer) },
                shape = CircleShape,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
