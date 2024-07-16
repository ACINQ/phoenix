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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.mutedTextFieldColors
import fr.acinq.phoenix.data.ContactInfo

@Composable
fun ContactsListView(
    canEditContact: Boolean,
    onEditContact: (ContactInfo) -> Unit,
    isOnSurface: Boolean,
) {
    val contactsState = if (canEditContact) {
        business.contactsManager.contactsList.collectAsState(null)
    } else {
        business.contactsManager.contactsWithOfferList.collectAsState(null)
    }

    contactsState.value?.let { contacts ->
        var nameFilterInput by remember { mutableStateOf("") }
        TextInput(
            text = nameFilterInput,
            staticLabel = null,
            leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_inspect, tint = MaterialTheme.typography.caption.color) },
            placeholder = { Text(text = stringResource(id = R.string.contact_search_name)) },
            singleLine = true,
            onTextChange = { nameFilterInput = it },
            textFieldColors = mutedTextFieldColors(),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        ContactsList(
            canEditContact = canEditContact,
            onEditContact = onEditContact,
            contacts = if (nameFilterInput.isNotBlank()) {
                contacts.filter { it.name.contains(nameFilterInput, ignoreCase = true) }
            } else {
                contacts
            },
            isOnSurface = isOnSurface
        )
    } ?: ProgressView(text = stringResource(id = R.string.utils_loading_data))
}

@Composable
private fun ContactsList(
    canEditContact: Boolean,
    onEditContact: (ContactInfo) -> Unit,
    contacts: List<ContactInfo>,
    isOnSurface: Boolean,
) {
    val navController = navController
    val listState = rememberLazyListState()

    if (contacts.isEmpty()) {
        Text(
            text = stringResource(id = R.string.contact_none),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
        )
    } else {
        LazyColumn(state = listState) {
            itemsIndexed(contacts) { index, contact ->
                val onClick = {
                    contact.mostRelevantOffer?.let {
                        navController.navigate("${Screen.ScanData.route}?input=${it.encode()}")
                    } ?: run { if (canEditContact) { onEditContact(contact) } }
                }
                if (isOnSurface) {
                    Clickable(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = onClick,
                    ) {
                        ContactRow(contact = contact, canEditContact = canEditContact, onEditContact = onEditContact)
                    }
                } else {
                    ItemCard(
                        index = index,
                        maxItemsCount = contacts.size,
                        onClick = onClick
                    ) {
                        ContactRow(contact = contact, canEditContact = canEditContact, onEditContact = onEditContact)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo, canEditContact: Boolean, onEditContact: (ContactInfo) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        ContactPhotoView(photoUri = contact.photoUri, name = contact.name, onChange = null, imageSize = 32.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = contact.name,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
            style = MaterialTheme.typography.body1.copy(fontSize = 18.sp)
        )
        if (canEditContact) {
            Button(
                icon = R.drawable.ic_edit,
                onClick = { onEditContact(contact) },
                padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}