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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class ContactOffer(
    val id: ByteVector32,
    val offer: OfferTypes.Offer,
    val label: String?,
    val createdAt: Instant
) {
    constructor(offer: OfferTypes.Offer, label: String?, createdAt: Instant? = null) : this(
        id = offer.offerId, // see note below
        offer = offer,
        label = label,
        createdAt = createdAt ?: Clock.System.now()
    )

    // We purposefully store the calculated `offer.offerId` as a property,
    // because the `offerId` property itself is actually a result of hashing and
    // other calculations. In other words it's not cheap to compute.
    // And it's a value we reference regularly within the UI.
}

data class ContactAddress(
    val id: ByteVector32,
    val address: String,
    val label: String?,
    val createdAt: Instant
) {
    constructor(address: String, label: String?, createdAt: Instant? = null) : this(
        id = ContactAddress.hash(address), // see note below
        address = address,
        label = label,
        createdAt = createdAt ?: Clock.System.now()
    )

    // We purposefully store the calculated `hash(address)` as a property,
    // because the value is a result of hashing. So it's not cheap to compute.
    // And it's a value we reference regularly within the UI.

    companion object {
        fun hash(address: String): ByteVector32 {
            val input = address.lowercase().toByteArray(charset = Charsets.UTF_8)
            return Crypto.sha256(input).byteVector32()
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
        publicKeys = offers.map { it.offer.contactInfos.map { it.nodeId } }.flatten()
    //  publicKeys = offers.map { it.offer.contactNodeIds }.flatten()
    )
}
