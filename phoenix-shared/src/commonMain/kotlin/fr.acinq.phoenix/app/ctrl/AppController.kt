package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.ctrl.MVI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
abstract class AppController<M : MVI.Model, I : MVI.Intent>(loggerFactory: LoggerFactory, firstModel: M) : MVI.Controller<M, I>(firstModel), CoroutineScope {

    private val job = Job()

    override val coroutineContext = MainScope().coroutineContext + job

    protected val logger = loggerFactory.newLogger(this::class)

    internal val models = MutableStateFlow(firstModel)

    private val modelChanges = Channel<M.() -> M>()

    init {

        fun truncateLog(m: M): String {
            val s = m.toString().lines().joinToString(" ")
            return if (s.length > 100) {
                "${s.take(100)} (truncated)"
            } else {
                s
            }
        }

        logger.info { "initial model=${truncateLog(firstModel)}" }

        launch {
            modelChanges.consumeEach { change ->
                val newModel = models.value.change()
                logger.info { "model=${truncateLog(newModel)}" }
                models.value = newModel
            }
        }
    }

    final override fun subscribe(onModel: (M) -> Unit): () -> Unit {
        val subscription = launch {
            models.collect { onModel(it) }
        }

        return ({ subscription.cancel() })
    }

    protected suspend fun model(change: M.() -> M) {
        modelChanges.send(change)
    }

    protected suspend fun model(model: M) {
        modelChanges.send { model }
    }

    protected abstract fun process(intent: I)

    final override fun intent(intent: I) {
        logger.info { "intent=$intent" }
        process(intent)
    }

    final override fun stop() {
        job.cancel()
    }

}