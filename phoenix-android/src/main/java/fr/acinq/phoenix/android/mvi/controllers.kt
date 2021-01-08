package fr.acinq.phoenix.android.mvi

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ContextAmbient
import fr.acinq.phoenix.android.MockModelInitialization
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*


typealias CF = ControllerFactory

val ControllerFactoryAmbient = staticAmbientOf<ControllerFactory?>(null)

@Composable
val controllerFactory: ControllerFactory get() = ControllerFactoryAmbient.current ?: error("No controller factory set. Please use appView or mockView.")

@Composable
fun AppView(children: @Composable () -> Unit) {
    val application = ContextAmbient.current.applicationContext as? PhoenixApplication
        ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")

    Providers(ControllerFactoryAmbient provides application.business.controllers) {
        children()
    }
}

@Suppress("UNREACHABLE_CODE")
val MockControllers = object : ControllerFactory {
    override fun initialization(): InitializationController = MVI.Controller.Mock(MockModelInitialization)
    override fun content(): ContentController = MVI.Controller.Mock(TODO())
    override fun home(): HomeController = MVI.Controller.Mock(TODO())
    override fun receive(): ReceiveController = MVI.Controller.Mock(TODO())
    override fun scan(): ScanController = MVI.Controller.Mock(TODO())
    override fun restoreWallet(): RestoreWalletController = MVI.Controller.Mock(TODO())
    override fun configuration(): ConfigurationController = MVI.Controller.Mock(TODO())
    override fun electrumConfiguration(): ElectrumConfigurationController = MVI.Controller.Mock(TODO())
    override fun channelsConfiguration(): ChannelsConfigurationController = MVI.Controller.Mock(TODO())
    override fun logsConfiguration(): LogsConfigurationController = MVI.Controller.Mock(TODO())
    override fun closeChannelsConfiguration(): CloseChannelsConfigurationController = MVI.Controller.Mock(TODO())
}

@Composable
fun MockView(children: @Composable () -> Unit) {
    Providers(ControllerFactoryAmbient provides MockControllers) {
        children()
    }
}
