/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase

class LinkTxToPaymentQueries(val database: PaymentsDatabase) {
    private val linkTxQueries = database.linkTxToPaymentQueries

    fun listWalletPaymentIdsForTx(txId: ByteVector32): List<WalletPaymentId> {
        return linkTxQueries.getPaymentIdForTx(tx_id = txId.toByteArray()).executeAsList()
            .mapNotNull { WalletPaymentId.create(it.type, it.id) }
    }

    fun linkTxToPayment(txId: ByteVector32, walletPaymentId: WalletPaymentId) {
        linkTxQueries.linkTxToPayment(tx_id = txId.toByteArray(), type = walletPaymentId.dbType.value, id = walletPaymentId.dbId)
    }

    fun setConfirmed(txId: ByteVector32, confirmedAt: Long) {
        linkTxQueries.setConfirmed(tx_id = txId.toByteArray(), confirmed_at = confirmedAt)
    }

    fun setLocked(txId: ByteVector32, lockedAt: Long) {
        linkTxQueries.setLocked(tx_id = txId.toByteArray(), locked_at = lockedAt)
    }
}