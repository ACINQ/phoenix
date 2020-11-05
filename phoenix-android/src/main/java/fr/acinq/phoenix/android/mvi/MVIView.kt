package fr.acinq.phoenix.android.mvi

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.viewModel
import fr.acinq.phoenix.android.utils.di
import fr.acinq.phoenix.ctrl.MVI
import org.kodein.di.DI
import org.kodein.type.TypeToken
import org.kodein.type.generic


/**
 * Display some content according to the model being emited by an MVI controller.
 *
 * @param M Model
 * @param I Intent
 * @param controllerType Ttype token representing the type of controller we wish to use.
 * @param content Composable displaying content according to an [M] model.
 */
@Composable
fun <M : MVI.Model, I : MVI.Intent> mviView(
    controllerType: TypeToken<MVI.Controller<M, I>>,
    content: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    // Gets the MVI controller or create it if it does not already exist.
    // Note that the controller is stored inside a ViewModel, which means that it will survive configuration changes activity restarts.
    val controller: MVI.Controller<M, I> by viewModel(factory = MVIControllerViewModel.Factory(di(), controllerType))

    var model by remember { mutableStateOf(controller.firstModel) }

    // Subscribes to the controller when the view becomes active (first display on screen),
    // unsubscribes when the view becomes inactive.
    // Being subscribed to the controller means we will receive new models when the controller has a new model.
    // Note that the controller will immediately send the latest available model upon subscription.
    onActive {
        val unsubscribe = controller.subscribe { model = it }
        onDispose { unsubscribe() }
    }

    // Display content according to model.
    content(model) { controller.intent(it) }
}

/**
 * @see View
 */
inline class MVIContext<C : MVI.Controller<*, *>>(val controllerType: TypeToken<C>)

/**
 * Utility to allow the `mvi<Controller>().View` syntax.
 *
 * @param C Controller type
 */
inline fun <reified C : MVI.Controller<*, *>> mvi(): MVIContext<C> = MVIContext(generic())


/**
 * Utility to allow the `mvi<Controller>().View` syntax.
 *
 * @param content Composable displaying content according to an [M] model.
 */
@Composable
fun <M : MVI.Model, I : MVI.Intent, C : MVI.Controller<M, I>> MVIContext<C>.View(content: @Composable (model: M, postIntent: (I) -> Unit) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    mviView(controllerType as TypeToken<MVI.Controller<M, I>>, content)
}
