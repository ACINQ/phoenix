package fr.acinq.phoenix.app.ctrl

import fr.acinq.phoenix.ctrl.LogController
import fr.acinq.phoenix.io.AppMainScope
import fr.acinq.phoenix.utils.Aggregator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppLogController(private val protocolLogs: Aggregator<String>) : LogController() {

    override fun subscribe(onModel: (Model) -> Unit): () -> Unit {
        val job = AppMainScope().launch {
            protocolLogs.openSubscription().consumeEach {
                onModel(Model(it))
            }
        }

        return ({ job.cancel() })
    }

    override fun process(intent: Unit) {}
}
