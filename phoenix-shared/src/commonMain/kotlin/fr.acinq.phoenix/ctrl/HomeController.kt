package fr.acinq.phoenix.ctrl

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.WalletPaymentOrderRow


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val balance: MilliSatoshi,
        val incomingBalance: MilliSatoshi?,
        val paymentsCount: Long
    ) : MVI.Model()

    val emptyModel = Model(
        balance = MilliSatoshi(0),
        incomingBalance = null,
        paymentsCount = 0
    )

    sealed class Intent : MVI.Intent()
}
