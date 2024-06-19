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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.ItemCard
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.data.ContactInfo

@Composable
fun ContactsListView(
    onContactClick: (ContactInfo) -> Unit,
    onExecuteOffer: (OfferTypes.Offer) -> Unit,
) {
    val contactsState = business.contactsManager.contactsList.collectAsState(null)
    contactsState.value?.let { contacts ->
        if (contacts.isEmpty()) {
            Text(text = "No contacts yet...", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.caption)
        } else {
            val listState = rememberLazyListState()
            LazyColumn(state = listState) {
                itemsIndexed(contacts) { index, contact ->
                    ItemCard(
                        index = index,
                        maxItemsCount = contacts.size,
                        onClick = {
                            // TODO order the offers listed by the group_concat in the sql query, take most recent one
                            contact.offers.firstOrNull()?.let { onExecuteOffer(it) }
                                ?: run { onContactClick(contact) }
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = Modifier.width(12.dp))
                            ContactPhotoView(image = contact.photo?.toByteArray(), name = contact.name, onChange = {}, imageSize = 32.dp, borderSize = 0.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = contact.name,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                icon = R.drawable.ic_edit,
                                onClick = { onContactClick(contact) },
                                padding = PaddingValues(12.dp)
                            )
                        }
                    }
                }
            }
        }
    } ?: ProgressView(text = "Fetching contacts...")
}