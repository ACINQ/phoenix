package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.phoenix.utils.newLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import org.kodein.di.DI
import org.kodein.di.DIAware


@OptIn(ExperimentalCoroutinesApi::class)
abstract class AppController<M : MVI.Model, I : MVI.Intent>(override val di: DI, firstModel: M) : MVI.Controller<M, I>(firstModel), DIAware, CoroutineScope {

    private val job = Job()

    override val coroutineContext = MainScope().coroutineContext + job

    protected val logger = newLogger()

    private val models = ConflatedBroadcastChannel(firstModel)

    private val modelChanges = Channel<M.() -> M>()

    init {
        fun Any.oneLineString() = toString().lines().map { it.trim() } .joinToString(" ")

        logger.info { "${this::class.simpleName} First Model: ${firstModel.oneLineString()}" }

        launch {
            modelChanges.consumeEach { change ->
                val newModel = models.value.change()
                logger.info { "${this::class.simpleName} Model: ${newModel.oneLineString()}" }
                models.send(newModel)
            }
        }
    }

    final override fun subscribe(onModel: (M) -> Unit): () -> Unit {
        val subscription = launch {
            models.openSubscription().consumeEach { onModel(it) }
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
        logger.info { "${this::class.simpleName} Intent: $intent" }
        process(intent)
    }

    final override fun stop() {
        job.cancel()
    }

}