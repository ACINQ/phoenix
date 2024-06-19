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

import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.wire.OfferTypes

/**
 * @param defaultOffer The default offer for a node.
 * @param payerKey A private key attached to a node. It can be used to sign payments to offers of
 *      third parties and prove the origin of that payment. The recipient of that payment can then
 *      decide that this origin is trusted, and show/hide the `payerNote` attached to that payment.
 *
 */
data class OfferData(
    val defaultOffer: OfferTypes.Offer,
    val payerKey: PrivateKey
)