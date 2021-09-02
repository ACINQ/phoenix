package fr.acinq.phoenix.controllers.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.controllers.MVI

object Receive {

    sealed class Model : MVI.Model() {
        object Awaiting : Model()
        object Generating: Model()
        data class Generated(val request: String, val paymentHash: String, val amount: MilliSatoshi?, val desc: String?): Model()
        sealed class SwapIn: Model() {
            object Requesting: SwapIn()
            data class Generated(val address: String): SwapIn()
        }

    }

    sealed class Intent : MVI.Intent() {
        data class Ask(val amount: MilliSatoshi?, val desc: String?) : Intent()
        object RequestSwapIn : Intent()
    }

}
