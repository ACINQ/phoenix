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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.MVI
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

open class MVIControllerViewModel<M : MVI.Model, I : MVI.Intent>(val controller: MVI.Controller<M, I>) : ViewModel() {
    val log: Logger = newLogger(LoggerFactory.default)

    init {
        log.debug { "initializing view-model for controller=$controller" }
    }

    override fun onCleared() {
        log.debug { "clearing view-model for controller=$controller" }
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
