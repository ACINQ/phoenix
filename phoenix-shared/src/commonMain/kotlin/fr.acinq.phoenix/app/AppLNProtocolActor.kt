package fr.acinq.phoenix.app

import fr.acinq.eklair.crypto.noise.*
import fr.acinq.eklair.io.LightningSession
import fr.acinq.phoenix.LNProtocolActor
import fr.acinq.phoenix.io.AppMainScope
import fr.acinq.phoenix.io.TCPSocket
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


private sealed class Event
private object Start: Event()
private object Connected: Event()
private class ReceivedBytes(val payload: ByteArray): Event()
private class ReceivedLNMessage(val payload: ByteArray): Event()
private object Disconnected: Event()
private object Timed: Event()

private sealed class Action
private class ConnectTo(val host: String, val port: Int): Action()
private class SendBytes(val payload: ByteArray, val flush: Boolean = true): Action()
private class ListenBytes(val count: Int): Action()
private class SendLNMessage(val payload: ByteArray, val session: LightningSession): Action()
private class ListenLNMessage(val session: LightningSession) : Action()
private class Show(val message: String): Action()
private object Restart : Action()
private class StartTimer(val milliseconds: Long = 0L) : Action()
private object StopTimer : Action()

private sealed class State {
    abstract fun process(event: Event): Pair<State, List<Action>>
}

private fun State.unexpectedEvent(event: Event): Nothing = error("State $this received unexpected event $event")

private val prefix = byteArrayOf(0x00)

private object Closed : State() {
    override fun process(event: Event): Pair<State, List<Action>> =
        when (event) {
            is Start -> {
                WaitingForConnection to listOf(ConnectTo("51.77.223.203", 19735), Show("Connecting..."))
            }
            else -> unexpectedEvent(event)
        }
}

@OptIn(ExperimentalStdlibApi::class)
private object WaitingForConnection : State() {
    override fun process(event: Event): Pair<State, List<Action>> =
        when (event) {
            is Disconnected -> Closed to listOf(Restart, Show("Disconnected!"))
            is Connected -> {
                val priv = ByteArray(32) { 0x01.toByte() }
                val pub = Secp256k1.pubkeyCreate(priv)
                val keyPair = Pair(pub, priv)
                val nodeId = Hex.decode("02413957815d05abb7fc6d885622d5cdc5b7714db1478cb05813a8474179b83c5c")
                val prologue = "lightning".encodeToByteArray()

                val writer = HandshakeState.initializeWriter(
                    handshakePatternXK,
                    prologue,
                    keyPair,
                    Pair(ByteArray(0), ByteArray(0)),
                    nodeId,
                    ByteArray(0),
                    Secp256k1DHFunctions,
                    Chacha20Poly1305CipherFunctions,
                    SHA256HashFunctions
                )
                val (reader, message, _) = writer.write(ByteArray(0))

                val expectedLength = when (reader.messages.size) {
                    3, 2 -> 50
                    1 -> 66
                    else -> throw RuntimeException("invalid state")
                }

                WaitForHandshake(reader) to listOf(
                    Show("Connected!"),
                    SendBytes(prefix, false), SendBytes(message, true),
                    Show("Starting handshake..."),
                    ListenBytes(expectedLength)
                )
            }
            else -> unexpectedEvent(event)
        }
}

private class WaitForHandshake(val reader: HandshakeStateReader) : State() {
    override fun process(event: Event): Pair<State, List<Action>> =
        when (event) {
            is Disconnected -> Closed to listOf(Restart, Show("Disconnected!"))
            is ReceivedBytes -> {
                val (writer, _, _) = reader.read(event.payload.drop(1).toByteArray())
                val (_, message, params) = writer.write(ByteArray(0))
                val (enc, dec, ck) = params!!
                val session = LightningSession(enc, dec, ck)
                WaitForInit(session) to listOf(
                    Show("Handshake done!"),
                    SendBytes(prefix, false), SendBytes(message, true),
                    SendLNMessage(Hex.decode("001000000002a8a0"), session),
                    Show("Sending init..."),
                    ListenLNMessage(session)
                )
            }
            else -> unexpectedEvent(event)
        }
}

private class WaitForInit(val session: LightningSession) : State() {
    override fun process(event: Event): Pair<State, List<Action>> =
        when (event) {
            is Disconnected -> Closed to listOf(Restart, Show("Disconnected!"))
            is ReceivedLNMessage -> MaintainingConnection(session) to listOf(
                Show("Init done: ${Hex.encode(event.payload)}"),
                StartTimer(2500),
                ListenLNMessage(session)
            )
            else -> unexpectedEvent(event)
        }
}

private class MaintainingConnection(val session: LightningSession) : State() {
    override fun process(event: Event): Pair<State, List<Action>> =
        when (event) {
            is Disconnected -> Closed to listOf(StopTimer, Restart, Show("Disconnected!"))
            is ReceivedLNMessage -> this to listOf(Show("Received: ${Hex.encode(event.payload)}"), ListenLNMessage(session))
            is Timed -> this to listOf(SendLNMessage(Hex.decode("0012000a0004deadbeef"), session), Show("Sending ping."), StartTimer(2500))
            else -> unexpectedEvent(event)
        }
}


@OptIn(ExperimentalCoroutinesApi::class)
internal class AppLNProtocolActor(private val socketFactory: TCPSocket.Builder, loggerFactory: LoggerFactory) : LNProtocolActor {

    private val logger = newLogger(loggerFactory)

    private lateinit var socket: TCPSocket

    private val channel = Channel<Event>(0)
    private val showChannel = BroadcastChannel<String>(Channel.BUFFERED)

    private var timerJob: Job? = null

    init {
        AppMainScope().launch {
            var state: State = Closed
            channel.consumeEach { event ->
                val (nextState, actions) = state.process(event)
                state = nextState

                actions.forEach { action ->
                    try {
                        when (action) {
                            is ConnectTo -> connect(action.host, action.port)
                            is SendBytes -> socket.send(action.payload, action.flush)
                            is SendLNMessage -> action.session.send(action.payload, socket::send)
                            is ListenBytes -> launch { channel.send(ReceivedBytes(socket.receive(action.count, action.count).unpack())) }
                            is ListenLNMessage -> launch { channel.send(ReceivedLNMessage(action.session.receive { count -> socket.receive(count, count).unpack() })) }
                            is Show -> showChannel.send(action.message)
                            is Restart -> launch { delay(1000) ; channel.send(Start) }
                            is StartTimer -> startTimer(action.milliseconds)
                            is StopTimer -> timerJob!!.cancel()
                        }
                    } catch (ex: Exception) {
                        logger.error(ex)
                        channel.send(Disconnected)
                    }
                }
            }
        }
    }

    private fun connect(host: String, port: Int) {
        AppMainScope().launch {
            try {
                socket = socketFactory.connect(host, port).unpack()
                channel.send(Connected)
            } catch (e: Throwable) {
                logger.warning { e.message ?: "Unknown error" }
                channel.send(Disconnected)
            }
        }
    }

    private fun startTimer(ms: Long) {
        timerJob = AppMainScope().launch {
            delay(ms)
            timerJob = null
            channel.send(Timed)
        }
    }

    override fun openSubscription(): ReceiveChannel<String> = showChannel.openSubscription()

    private var started = false
    override fun start() {
        require(!started)
        started = true

        AppMainScope().launch { channel.send(Start) }
    }
}
