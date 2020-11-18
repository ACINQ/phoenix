package fr.acinq.phoenix.ctrl

import fr.acinq.phoenix.data.BitcoinUnit


typealias ReceiveController = MVI.Controller<Receive.Model, Receive.Intent>

object Receive {

    sealed class Model : MVI.Model() {
        object Awaiting : Model()
        object Generating: Model()
        data class Generated(val request: String, val amount: Double?, val unit: BitcoinUnit, val desc: String?): Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Ask(val amount: Double?, val unit: BitcoinUnit, val desc: String?) : Intent()
    }

}
