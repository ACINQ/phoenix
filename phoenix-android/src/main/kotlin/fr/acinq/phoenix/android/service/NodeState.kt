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

package fr.acinq.phoenix.android.service

/**
 * This class represent the state of the wallet in regard to the underlying LN node.
 * 4 main states:
 * - Off = the wallet is not started
 * - Bootstrap = the wallet is starting (e.g unlocking the seed)
 * - Started = the wallet is unlocked and the node will now try to connect and establish channels
 * - Error = the node could not start
 */
sealed class WalletState {

    val name: String = this.javaClass.simpleName

    /** Default state, the node is not started. */
    object Off : WalletState()

    /** This is an utility state that is used when the binding between the service holding the state and the consumers of that state is disconnected. */
    object Disconnected : WalletState()

    /** This is a transition state. The node is starting up and will soon either go to Started, or to Error. */
    sealed class Bootstrap : WalletState() {
        object Init : Bootstrap()
        object Tor : Bootstrap()
        object Node : Bootstrap()
    }

    /** The node is started and we should be able to access the kit/api for the legacy app, or the [PhoenixBusiness] class. */
    sealed class Started : WalletState() {
        // sealed class Legacy(internal val kit: Kit, internal val xpub: Xpub) : Started() {
        //     internal val _api: Eclair by lazy {
        //         EclairImpl(kit)
        //     }
        // }
        object Kmm : Started()
    }

    /** Startup has failed, the state contains the error details. */
    sealed class Error : WalletState() {
        data class Generic(val message: String) : Error()
        object UnreadableData : Error()
    }
}