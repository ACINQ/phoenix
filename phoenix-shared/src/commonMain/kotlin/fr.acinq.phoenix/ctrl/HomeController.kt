package fr.acinq.phoenix.ctrl

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val balance: MilliSatoshi,
        val incomingBalance: MilliSatoshi?,
        val payments: List<WalletPayment>
    ) : MVI.Model()

    val emptyModel = Model(MilliSatoshi(0), null, emptyList())

    sealed class Intent : MVI.Intent()

}
