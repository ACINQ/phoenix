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

import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.utils.mutedTextFieldColors
import fr.acinq.phoenix.data.ContactInfo

@Composable
fun ContactsListView(
    onContactClick: (ContactInfo) -> Unit,
    isOnSurface: Boolean,
) {
    val contactsList by business.databaseManager.contactsList.collectAsState(null)

    contactsList?.let { contacts ->
        var nameFilterInput by remember { mutableStateOf("") }
        TextInput(
            text = nameFilterInput,
            staticLabel = null,
            leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_inspect, tint = MaterialTheme.typography.caption.color) },
            placeholder = { Text(text = stringResource(id = R.string.contact_search_name)) },
            singleLine = true,
            onTextChange = { nameFilterInput = it },
            textFieldColors = mutedTextFieldColors(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        ContactsList(
            onContactClick = onContactClick,
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
    onContactClick: (ContactInfo) -> Unit,
    contacts: List<ContactInfo>,
    isOnSurface: Boolean,
) {
    val listState = rememberLazyListState()

    if (contacts.isEmpty()) {
        Text(
            text = stringResource(id = R.string.contact_none),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
        )
    } else {
        LazyColumn(state = listState) {
            itemsIndexed(contacts) { index, contact ->
                if (isOnSurface) {
                    Clickable(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = { onContactClick(contact) },
                    ) {
                        ContactRow(contact = contact)
                    }
                } else {
                    ItemCard(
                        index = index,
                        maxItemsCount = contacts.size,
                        onClick = { onContactClick(contact) }
                    ) {
                        ContactRow(contact = contact)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(IntrinsicSize.Min),
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Surface(modifier = Modifier.padding(vertical = 5.dp)) {
            ContactPhotoView(photoUri = contact.photoUri, name = contact.name, onChange = null, imageSize = 38.dp, borderSize = 1.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = contact.name,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
            style = MaterialTheme.typography.body1.copy(fontSize = 18.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}