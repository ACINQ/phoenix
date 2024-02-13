package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import fr.acinq.lightning.logging.warning
import fr.acinq.tor.Tor
import fr.acinq.tor.TorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn


object TorHelper {
    fun torLogger(loggerFactory: LoggerFactory): (Tor.LogLevel, String) -> Unit {
        val logger = loggerFactory.newLogger("Tor")
        return { level, message ->
            when (level) {
                Tor.LogLevel.DEBUG -> logger.debug { message }
                Tor.LogLevel.NOTICE -> logger.info { message }
                Tor.LogLevel.WARN -> logger.warning { message }
                Tor.LogLevel.ERR -> logger.error { message }
            }
        }
    }

    suspend fun StateFlow<TorState>.connectionState(scope: CoroutineScope) = flow<Connection> {
        collect { torState ->
            val newState = when (torState) {
                TorState.STARTING -> Connection.ESTABLISHING
                TorState.RUNNING -> Connection.ESTABLISHED
                TorState.STOPPED -> Connection.CLOSED(null)
            }
            emit(newState)
        }
    }.stateIn(scope)
}