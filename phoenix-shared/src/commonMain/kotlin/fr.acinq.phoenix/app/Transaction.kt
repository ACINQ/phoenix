package fr.acinq.phoenix.app

data class Transaction(
    val amountSat: Long,
    val desc: String, // Swift does not support fields named description
    val success: Boolean,
    val paymentHash: String,
    val paymentRequest: String,
    val paymentPreimage: String,
    val creationTimestamp: Long,
    val expirationTimestamp: Long,
    val completionTimestamp: Long
)