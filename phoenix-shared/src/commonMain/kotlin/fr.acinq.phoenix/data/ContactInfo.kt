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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Instant

data class ContactOffer(
    val offer: OfferTypes.Offer,
    val label: String?,
    val createdAt: Instant
)

data class ContactAddress(
    val address: String,
    val label: String?,
    val createdAt: Instant
) {
    fun addressHash() = ContactAddress.hash(address)

    companion object {
        fun hash(address: String): String {
            val input = address.lowercase().toByteArray(charset = Charsets.UTF_8)
            return Crypto.sha256(input).byteVector().toHex()
        }
    }
}

data class ContactInfo(
    val id: UUID,
    val name: String,
    val photoUri: String?,
    val useOfferKey: Boolean,
    val offers: List<ContactOffer>,
    val addresses: List<ContactAddress>,
    val publicKeys: List<PublicKey>,
) {
    constructor(
        id: UUID,
        name: String,
        photoUri: String?,
        useOfferKey: Boolean,
        offers: List<ContactOffer>,
        addresses: List<ContactAddress>
    ) : this(
        id = id,
        name = name,
        photoUri = photoUri,
        useOfferKey = useOfferKey,
        offers = offers,
        addresses = addresses,
        publicKeys = offers.map { it.offer.contactNodeIds }.flatten()
    )

    @Deprecated(
        message = "Contacts now support both offers & lightning addresses.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("If the contact has multiple options, the UI should prompt the user to choose.")
    )
    val mostRelevantOffer: OfferTypes.Offer? by lazy { offers.firstOrNull()?.offer }
}
