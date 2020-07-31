package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.ctrl.ReceiveController
import fr.acinq.phoenix.utils.screenProvider
import org.kodein.di.DI
import org.kodein.di.bind


class MockDIBuilder {
    var homeModel: Home.Model = Home.emptyModel
    var receiveModel: Receive.Model = Receive.Model.Generating

    fun apply(block: MockDIBuilder.() -> Unit): MockDIBuilder {
        this.block()
        return this
    }

    fun di() = DI {
        bind<HomeController>() with screenProvider { Home.MockController(homeModel) }
        bind<ReceiveController>() with screenProvider { Receive.MockController(receiveModel) }
    }
}
