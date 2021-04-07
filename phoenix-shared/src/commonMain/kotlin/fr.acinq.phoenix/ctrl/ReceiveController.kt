package fr.acinq.phoenix.ctrl

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.BitcoinUnit


typealias ReceiveController = MVI.Controller<Receive.Model, Receive.Intent>

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
