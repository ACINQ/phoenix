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

@file:OptIn(ExperimentalStdlibApi::class)

package fr.acinq.phoenix.db


import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.sqldelight.*
import fr.acinq.phoenix.managers.ContactsManager
import fr.acinq.phoenix.managers.CurrencyManager
import fr.acinq.phoenix.utils.extensions.toByteArray

fun createSqliteChannelsDb(driver: SqlDriver): SqliteChannelsDb {
    return SqliteChannelsDb(
        driver = driver,
        database = ChannelsDatabase(
            driver = driver,
            htlc_infosAdapter = Htlc_infos.Adapter(ByteVector32Adapter, ByteVector32Adapter),
            local_channelsAdapter = Local_channels.Adapter(ByteVector32Adapter, PersistedChannelStateAdapter)
        )
    )
}

fun createSqlitePaymentsDb(driver: SqlDriver, contactsManager: ContactsManager?, currencyManager: CurrencyManager?): SqlitePaymentsDb {
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
        contactsManager = contactsManager,
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

object PersistedChannelStateAdapter : ColumnAdapter<PersistedChannelState, ByteArray> {
    override fun decode(databaseValue: ByteArray): PersistedChannelState = when(val res = fr.acinq.lightning.serialization.channel.Serialization.deserialize(databaseValue)) {
        is fr.acinq.lightning.serialization.channel.Serialization.DeserializationResult.Success -> res.state
        is fr.acinq.lightning.serialization.channel.Serialization.DeserializationResult.UnknownVersion -> error("unknown channel version ${res.version}")
    }

    override fun encode(value: PersistedChannelState): ByteArray = fr.acinq.lightning.serialization.channel.Serialization.serialize(value)
}

object IncomingPaymentAdapter : ColumnAdapter<IncomingPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): IncomingPayment =
        fr.acinq.lightning.serialization.payment.Serialization.deserialize(databaseValue).getOrNull()
            ?.let { it as IncomingPayment }
            ?: println("cannot deserialize ${databaseValue.toHexString()}").let {
                throw RuntimeException("cannot deserialize ${databaseValue.toHexString()}")
            }

    override fun encode(value: IncomingPayment): ByteArray = fr.acinq.lightning.serialization.payment.Serialization.serialize(value)
}

object OutgoingPaymentAdapter : ColumnAdapter<OutgoingPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): OutgoingPayment =
        fr.acinq.lightning.serialization.payment.Serialization.deserialize(databaseValue).getOrNull()
            ?.let { it as OutgoingPayment }
            ?: throw RuntimeException("cannot deserialize ${databaseValue.toHexString()}")

    override fun encode(value: OutgoingPayment): ByteArray = fr.acinq.lightning.serialization.payment.Serialization.serialize(value)
}

object WalletPaymentAdapter : ColumnAdapter<WalletPayment, ByteArray> {
    override fun decode(databaseValue: ByteArray): WalletPayment =
        fr.acinq.lightning.serialization.payment.Serialization.deserialize(databaseValue).getOrNull()
            ?: throw RuntimeException("cannot deserialize ${databaseValue.toHexString()}")

    override fun encode(value: WalletPayment): ByteArray = fr.acinq.lightning.serialization.payment.Serialization.serialize(value)
}