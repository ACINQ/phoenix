package fr.acinq.phoenix

import fr.acinq.phoenix.app.AppLNProtocolActor
import fr.acinq.phoenix.app.ctrl.AppLogController
import fr.acinq.phoenix.ctrl.LogController
import fr.acinq.phoenix.io.Socket
import fr.acinq.phoenix.utils.Aggregator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


//@OptIn(ExperimentalCoroutinesApi::class)
//class Phoenix(vararg val modules: DI.Module) : DIAware {
//
//    companion object {
//        fun socketFactoryModule(socketFactory: Socket.Factory): DI.Module =
//            DI.Module("Platform sockets") {
//                bind<Socket.Factory>() with instance(socketFactory)
//            }
//    }
//
//    override val di = DI {
//        modules.forEach { import(it) }
//
//        bind<LNProtocolActor>() with singleton { ApplicationLNProtocolActor(instance(), instance()) }
//        bind<LoggerFactory>() with instance(LoggerFactory.default)
//    }
//
//    val protocolActor by instance<LNProtocolActor>()
//
//    val logger = newLogger(direct.instance())
//
//    init {
//        MainScope().launch {
//            protocolActor.subscribe().consumeEach {
//                logger.info { it }
//            }
//        }
//        protocolActor.start()
//    }
//}

@OptIn(ExperimentalCoroutinesApi::class)
class Phoenix(socketFactory: Socket.Factory) {

    val loggerFactory = LoggerFactory.default

    private val protocolActor: LNProtocolActor = AppLNProtocolActor(socketFactory, loggerFactory)

    private val protocolLogs = Aggregator(MainScope(), protocolActor.openSubscription())

    fun newLogController(): LogController = AppLogController(protocolLogs)

    val logger = newLogger(loggerFactory)

    init {
        MainScope().launch {
            protocolActor.openSubscription().consumeEach {
                logger.info { it }
            }
        }
        protocolActor.start()
    }
}
