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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.MVI
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class MVIControllerViewModel<M : MVI.Model, I : MVI.Intent>(val controller: MVI.Controller<M, I>) : ViewModel() {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    // Model is settable in case the controller is not flexible enough, even though it should be avoided when possible
    var model by mutableStateOf(controller.firstModel)

    private val unsubscribe: () -> Unit

    init {
        log.debug("initializing view-model for controller=$controller, subscribing to model changes")
        unsubscribe = controller.subscribe {
            model = it
        }
    }

    override fun onCleared() {
        log.debug("clearing view-model for controller=$controller with model=$model")
        unsubscribe()
        controller.stop()
    }

    class Factory<M : MVI.Model, I : MVI.Intent>(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> MVI.Controller<M, I>
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MVIControllerViewModel(controllerFactory.getController()) as T
        }
    }
}
