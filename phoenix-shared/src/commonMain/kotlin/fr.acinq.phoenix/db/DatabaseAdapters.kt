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

package fr.acinq.phoenix.db


import app.cash.sqldelight.ColumnAdapter
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.utils.extensions.toByteArray


object UUIDAdapter : ColumnAdapter<UUID, ByteArray> {
    override fun decode(databaseValue: ByteArray): UUID = UUID.fromBytes(databaseValue)

    override fun encode(value: UUID): ByteArray = value.toByteArray()
}

object ByteVector32Adapter : ColumnAdapter<ByteVector32, ByteArray> {
    override fun decode(databaseValue: ByteArray) = ByteVector32(databaseValue)

    override fun encode(value: ByteVector32): ByteArray = value.toByteArray()
}

object TxIdAdapter : ColumnAdapter<TxId, ByteArray> {
    override fun decode(databaseValue: ByteArray) = TxId(databaseValue)

    override fun encode(value: TxId): ByteArray = value.value.toByteArray()
}

object IncomingPaymentAdapter : ColumnAdapter<IncomingPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): IncomingPayment = Serialization.deserialize(databaseValue).getOrThrow() as IncomingPayment

    override fun encode(value: IncomingPayment): ByteArray = Serialization.serialize(value)
}

object OutgoingPaymentAdapter : ColumnAdapter<OutgoingPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): OutgoingPayment = Serialization.deserialize(databaseValue).getOrThrow() as OutgoingPayment

    override fun encode(value: OutgoingPayment): ByteArray = Serialization.serialize(value)
}

object WalletPaymentAdapter : ColumnAdapter<WalletPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): WalletPayment = Serialization.deserialize(databaseValue).getOrThrow()

    override fun encode(value: WalletPayment): ByteArray = Serialization.serialize(value)
}
