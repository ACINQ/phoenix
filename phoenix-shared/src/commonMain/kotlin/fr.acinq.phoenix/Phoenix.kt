package fr.acinq.phoenix

import fr.acinq.phoenix.app.ApplicationLNProtocolActor
import fr.acinq.phoenix.io.Socket
import org.kodein.di.*


class Phoenix(vararg val modules: DI.Module) : DIAware {

    companion object {
        fun socketFactoryModule(socketFactory: Socket.Factory): DI.Module =
            DI.Module("Platform sockets") {
                bind<Socket.Factory>() with instance(socketFactory)
            }
    }

    override val di = DI {
        modules.forEach { import(it) }

        bind<LNProtocolActor>() with eagerSingleton { ApplicationLNProtocolActor(di) }
    }

}
