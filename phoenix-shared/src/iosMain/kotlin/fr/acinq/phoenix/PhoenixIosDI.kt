package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.DemoController
import org.kodein.di.direct
import org.kodein.di.instance


// Facade, wont be needed when first class support for iOS is released
class PhoenixIosDI(phoenix: Phoenix) {
    private val di = phoenix.di.direct

    fun demoControllerInstance(): DemoController = di.instance()
}
