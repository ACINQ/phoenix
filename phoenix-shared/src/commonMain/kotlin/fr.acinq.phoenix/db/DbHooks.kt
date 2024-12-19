package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface

/**
 * Implement this function to execute platform specific code when a payment is saved to the database.
 * For example, on iOS this is used to enqueue the (encrypted) payment for upload to CloudKit.
 *
 * This function is invoked inside the same transaction used to add/modify the row.
 * This means any database operations performed in this function are atomic,
 * with respect to the referenced row.
 */
expect fun didSaveWalletPayment(id: UUID, database: PaymentsDatabase)

/**
 * Implement this function to execute platform specific code when a payment is deleted.
 * For example, on iOS this is used to enqueue an operation to delete the payment from CloudKit.
 */
expect fun didDeleteWalletPayment(id: UUID, database: PaymentsDatabase)

/**
 * Implement this function to execute platform specific code when a payment's metadata is updated.
 * For example: the user modifies the payment description.
 *
 * This function is invoked inside the same transaction used to add/modify the row.
 * This means any database operations performed in this function are atomic,
 * with respect to the referenced row.
 */
expect fun didUpdateWalletPaymentMetadata(id: UUID, database: PaymentsDatabase)

/**
 * Implement this function to execute platform specific code when a contact is saved to the database.
 * For example, on iOS this is used to enqueue the (encrypted) contact for upload to CloudKit.
 *
 * This function is invoked inside the same transaction used to add/modify the row.
 * This means any database operations performed in this function are atomic,
 * with respect to the referenced row.
 */
expect fun didSaveContact(contactId: UUID, database: AppDatabase)

/**
 * Implement this function to execute platform specific code when a contact is deleted.
 * For example, on iOS this is used to enqueue an operation to delete the contact from CloudKit.
 */
expect fun didDeleteContact(contactId: UUID, database: AppDatabase)

/**
 * Implemented on Apple platforms with support for CloudKit.
 */
expect fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface?
