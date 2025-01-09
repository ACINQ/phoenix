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
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.managers.CurrencyManager
import fr.acinq.phoenix.utils.extensions.toByteArray
import fracinqphoenixdb.Cloudkit_payments_metadata
import fracinqphoenixdb.Cloudkit_payments_queue
import fracinqphoenixdb.Link_lightning_outgoing_payment_parts
import fracinqphoenixdb.On_chain_txs
import fracinqphoenixdb.Payments_incoming
import fracinqphoenixdb.Payments_metadata
import fracinqphoenixdb.Payments_outgoing


fun createSqlitePaymentsDb(driver: SqlDriver, currencyManager: CurrencyManager): SqlitePaymentsDb {
    return SqlitePaymentsDb(
        driver = driver,
        database = PaymentsDatabase(
            driver = driver,
            payments_incomingAdapter = Payments_incoming.Adapter(UUIDAdapter, ByteVector32Adapter, TxIdAdapter, IncomingPaymentAdapter),
            payments_outgoingAdapter = Payments_outgoing.Adapter(UUIDAdapter, ByteVector32Adapter, TxIdAdapter, OutgoingPaymentAdapter),
            link_lightning_outgoing_payment_partsAdapter = Link_lightning_outgoing_payment_parts.Adapter(UUIDAdapter, UUIDAdapter),
            on_chain_txsAdapter = On_chain_txs.Adapter(UUIDAdapter, TxIdAdapter),
            payments_metadataAdapter = Payments_metadata.Adapter(UUIDAdapter, EnumColumnAdapter(), EnumColumnAdapter(), EnumColumnAdapter()),
            cloudkit_payments_queueAdapter = Cloudkit_payments_queue.Adapter(UUIDAdapter),
            cloudkit_payments_metadataAdapter = Cloudkit_payments_metadata.Adapter(UUIDAdapter),
        ),
        currencyManager = currencyManager,
    )
}

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
