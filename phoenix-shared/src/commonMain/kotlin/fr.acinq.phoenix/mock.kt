package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.utils.screenProvider
import org.kodein.di.DI
import org.kodein.di.bind


class MockDIBuilder {
    var homeModel: Home.Model = Home.emptyModel
    var receiveModel: Receive.Model = Receive.Model.Generating
    var scanModel: Scan.Model = Scan.Model.NeedHeight

    fun apply(block: MockDIBuilder.() -> Unit): MockDIBuilder {
        this.block()
        return this
    }

    fun di() = DI {
        bind<HomeController>() with screenProvider { Home.MockController(homeModel) }
        bind<ReceiveController>() with screenProvider { Receive.MockController(receiveModel) }
        bind<ScanController>() with screenProvider { Scan.MockController(scanModel) }
    }
}
