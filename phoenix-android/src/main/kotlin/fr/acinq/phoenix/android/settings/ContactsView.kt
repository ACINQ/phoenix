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

package fr.acinq.phoenix.android.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.IconPopup
import fr.acinq.phoenix.android.components.contact.ContactDetailsView
import fr.acinq.phoenix.android.components.contact.ContactsListView
import fr.acinq.phoenix.android.components.contact.SaveNewContactDialog
import fr.acinq.phoenix.data.ContactInfo

@Composable
fun SettingsContactsView(
    onBackClick: () -> Unit,
    immediatelyShowAddContactDialog: Boolean,
) {
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var isAddingNewContact by remember { mutableStateOf(immediatelyShowAddContactDialog) }

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            content = {
                Text(text = stringResource(id = R.string.settings_contacts))
                IconPopup(popupMessage = stringResource(id = R.string.settings_contacts_help))
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    icon = R.drawable.ic_plus,
                    onClick = { isAddingNewContact = true },
                    shape = CircleShape
                )
            }
        )
        ContactsListView(onEditContact = { selectedContact = it }, canEditContact = true, isOnSurface = false)
    }

    selectedContact?.let {
        ContactDetailsView(
            contact = it,
            currentOffer = null,
            onDismiss = { selectedContact = null },
            onContactChange = {},
        )
    } ?: run {
        if (isAddingNewContact) {
            SaveNewContactDialog(initialOffer = null, onDismiss = { isAddingNewContact = false }, onSaved = { isAddingNewContact = false })
        }
    }
}