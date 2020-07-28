package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.ctrl.ReceiveController
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance


// Facade, wont be needed when first class support for iOS is released
class AppDI(private val di: DI) {

    fun homeControllerInstance(): HomeController = di.direct.instance()
    fun receiveControllerInstance(): ReceiveController = di.direct.instance()

}
