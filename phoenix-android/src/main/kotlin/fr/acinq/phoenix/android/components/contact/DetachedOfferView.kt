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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.data.ContactInfo
import kotlinx.coroutines.launch

@Composable
fun DetachedOfferView(
    offer: OfferTypes.Offer,
    onContactSaved: (ContactInfo) -> Unit
) {
    var showSaveContactDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val contactsManager = business.contactsManager

    val encoded = remember(offer) { offer.encode() }
    Clickable(onClick = { showSaveContactDialog = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.offset((-8).dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = encoded, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                PhoenixIcon(resourceId = R.drawable.ic_user, tint = mutedTextColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(id = R.string.contact_add_contact), style = MaterialTheme.typography.subtitle2)
            }
        }
    }

    if (showSaveContactDialog) {
        SaveNewContactDialog(
            initialOffer = offer,
            onDismiss = { showSaveContactDialog = false },
            onSaved = { onContactSaved(it) },
        )
    }
}