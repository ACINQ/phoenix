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

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes

data class ContactInfo(
    val id: UUID,
    val name: String,
    val photoUri: String?,
    val offers: List<OfferTypes.Offer>,
    val publicKeys: List<PublicKey>,
) {
    constructor(id: UUID, name: String, photoUri: String?, offers: List<OfferTypes.Offer>) : this(
        id = id,
        name = name,
        photoUri = photoUri,
        offers = offers,
        publicKeys = offers.map { it.contactNodeIds }.flatten()
    )

    // TODO: order the offers listed by the group_concat in the sql query, and take the most recently added one
    val mostRelevantOffer: OfferTypes.Offer? by lazy { offers.firstOrNull() }
}
