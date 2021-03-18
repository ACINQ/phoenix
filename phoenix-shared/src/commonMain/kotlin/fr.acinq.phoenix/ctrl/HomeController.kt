package fr.acinq.phoenix.ctrl

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.WalletPayment


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val balance: MilliSatoshi,
        val payments: List<WalletPayment>,
        val lastPayment: WalletPayment?
    ) : MVI.Model()

    val emptyModel = Model(MilliSatoshi(0), emptyList(), null)

    sealed class Intent : MVI.Intent()

}
