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


import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import fr.acinq.phoenix.android.LocalControllerFactory
import fr.acinq.phoenix.controllers.*
import fr.acinq.phoenix.controllers.config.*
import fr.acinq.phoenix.controllers.payments.Scan

@Suppress("UNREACHABLE_CODE")
val MockControllers = object : ControllerFactory {
    override fun initialization(): InitializationController = MVI.Controller.Mock(TODO())
    override fun content(): ContentController = MVI.Controller.Mock(TODO())
    override fun home(): HomeController = MVI.Controller.Mock(TODO())
    override fun receive(): ReceiveController = MVI.Controller.Mock(TODO())
    override fun scan(firstModel: Scan.Model): ScanController = MVI.Controller.Mock(TODO())
    override fun restoreWallet(): RestoreWalletController = MVI.Controller.Mock(TODO())
    override fun configuration(): ConfigurationController = MVI.Controller.Mock(TODO())
    override fun electrumConfiguration(): ElectrumConfigurationController = MVI.Controller.Mock(TODO())
    override fun channelsConfiguration(): ChannelsConfigurationController = MVI.Controller.Mock(TODO())
//    override fun recoveryPhraseConfiguration(): RecoveryPhraseConfigurationController {
//        TODO("Not yet implemented")
//    }
    override fun logsConfiguration(): LogsConfigurationController = MVI.Controller.Mock(TODO())
    override fun closeChannelsConfiguration(): CloseChannelsConfigurationController = MVI.Controller.Mock(TODO())
    override fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController = MVI.Controller.Mock(TODO())
}

@Composable
fun MockView(children: @Composable () -> Unit) {
    CompositionLocalProvider(LocalControllerFactory provides MockControllers) {
        children()
    }
}
