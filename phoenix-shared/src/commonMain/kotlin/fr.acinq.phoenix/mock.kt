package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.utils.screenProvider
import org.kodein.di.DI
import org.kodein.di.bind

fun mockControllers(
    homeModel: Home.Model = Home.emptyModel
) = DI {
    bind<HomeController>() with screenProvider { Home.MockController(homeModel) }
}
