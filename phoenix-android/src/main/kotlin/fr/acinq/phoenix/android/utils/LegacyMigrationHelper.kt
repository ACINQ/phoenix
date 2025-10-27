/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import fr.acinq.lightning.db.*
import fr.acinq.lightning.io.Peer
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.managers.phoenixSwapInWallet


/** Returns true if the payment is a channel-close made by the legacy app to the node's swap-in address. */
fun WalletPayment.isLegacyMigration(metadata: WalletPaymentMetadata): Boolean? {
    return when {
        this !is ChannelCloseOutgoingPayment -> false
        metadata.userDescription == "kmp-migration-override" -> true
        else -> false
    }
}
