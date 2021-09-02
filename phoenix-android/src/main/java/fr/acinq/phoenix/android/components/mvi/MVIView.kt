/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.components.mvi

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.MVI


/** Get a composable view that refreshes when the controller's model updates. Uses a generic view model. */
@Composable
fun <M : MVI.Model, I : MVI.Intent> MVIView(
    getController: ControllerFactory.() -> MVI.Controller<M, I>,
    children: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    // store controller in a view model to survive configuration changes
    val viewModel: MVIControllerViewModel<M, I> = viewModel(factory = MVIControllerViewModel.Factory(controllerFactory, getController))
    MVIView(viewModel, children)
}

/** Get a composable view that refreshes when the controller's model updates. You must provide a custom view model that contain a controller. */
@Composable
fun <M : MVI.Model, I : MVI.Intent> MVIView(
    viewModel: MVIControllerViewModel<M, I>,
    children: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    val logger = logger()
    val controller = viewModel.controller
    var model by remember { mutableStateOf(viewModel.controller.firstModel) }

    // Subscribes to the controller when the view becomes active (first display on screen),
    // Note that the controller will immediately send the latest available model upon subscription.
    DisposableEffect(key1 = controller, effect = {
        logger.debug { "subscribing to changes in model for controller=$controller" }
        val unsubscribe = controller.subscribe { model = it }
        onDispose {
            logger.debug { "disposing of view for controller=$controller" }
            unsubscribe()
        }
    })

    children(model) { controller.intent(it) }
}
