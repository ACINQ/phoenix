package fr.acinq.phoenix.controllers.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.controllers.MVI

object Scan {

    sealed class BadRequestReason {
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?): BadRequestReason()
        object IsLnUrl: BadRequestReason()
        object IsBitcoinAddress: BadRequestReason()
        object UnknownFormat: BadRequestReason()
        object AlreadyPaidInvoice: BadRequestReason()
    }

    sealed class DangerousRequestReason {
        object IsAmountlessInvoice: DangerousRequestReason()
        object IsOwnInvoice: DangerousRequestReason()
    }

    sealed class Model : MVI.Model() {
        object Ready: Model()
        data class BadRequest(
            val reason: BadRequestReason
        ): Model()
        data class DangerousRequest(
            val reason: DangerousRequestReason,
            val request: String
        ): Model()
        data class Validate(
            val request: String,
            val amountMsat: Long?,
            val expiryTimestamp: Long?, // since unix epoch
            val requestDescription: String?,
            val balanceMsat: Long
        ): Model()
        object Sending: Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Parse(val request: String) : Intent()
        data class ConfirmDangerousRequest(val request: String) : Intent()
        data class Send(val request: String, val amount: MilliSatoshi) : Intent()
    }

}
