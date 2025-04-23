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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.components.SplashClickableContent
import fr.acinq.phoenix.data.ContactInfo


/**
 * A contact compact view is a clickable element that shows the contact's name and photo.
 * Clicking on that view will display the ContactDetailsView of that contact.
 *
 * The contact can be edited and deleted from that screen. Sending a payment can also be
 * triggered from that view.
 */
@Composable
fun ContactCompactView(
    contact: ContactInfo,
    onContactChange: (ContactInfo?) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    SplashClickableContent(onClick = { showSheet = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactPhotoView(photoUri = contact.photoUri, name = contact.name, onChange = null, imageSize = 32.dp, borderSize = 1.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showSheet) {
        ContactDetailsView(
            contact = contact,
            onDismiss = { showSheet = false },
            onContactChange = onContactChange,
        )
    }
}
