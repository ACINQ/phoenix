/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.services


/**
 * This class represent the state of the node service.
 * - Off = the service has not started the node
 * - Init = the wallet is starting
 * - Started = the wallet is unlocked and the node will now try to connect and establish channels
 * - Error = the node could not start
 */
sealed class NodeServiceState {

    val name: String by lazy { this.javaClass.simpleName }

    /** Default state, the node is not started. */
    data object Off : NodeServiceState()

    /** This is an utility state that is used when the binding between the service holding the state and the consumers of that state is disconnected. */
    data object Disconnected : NodeServiceState()
    data object Init : NodeServiceState()
    data object Running : NodeServiceState()
    data class Error(val cause: Throwable) : NodeServiceState()
}