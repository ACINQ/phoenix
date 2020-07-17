package fr.acinq.phoenix

import fr.acinq.phoenix.app.AppLNProtocolActor
import fr.acinq.phoenix.app.ctrl.AppLogController
import fr.acinq.phoenix.ctrl.LogController
import fr.acinq.phoenix.io.AppMainScope
import fr.acinq.phoenix.io.TcpSocket
import fr.acinq.phoenix.utils.Aggregator
import fr.acinq.phoenix.utils.screenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class Phoenix {

    val di = DI {
        bind<LoggerFactory>() with instance(LoggerFactory.default)
        bind<TcpSocket.Builder>() with singleton { TcpSocket.Builder() }
        bind<LNProtocolActor>() with singleton { AppLNProtocolActor(instance(), instance()) }

        bind(tag = "logs") from singleton { Aggregator(AppMainScope(), instance<LNProtocolActor>().openSubscription()) }

        bind<LogController>() with screenProvider { AppLogController(instance(tag = "logs")) }
    }

    private val logger = newLogger(di.direct.instance())

    init {
        val protocolActor by di.instance<LNProtocolActor>()
        AppMainScope().launch {
            protocolActor.openSubscription().consumeEach {
                logger.info { it }
            }
        }
        protocolActor.start()
    }
}
