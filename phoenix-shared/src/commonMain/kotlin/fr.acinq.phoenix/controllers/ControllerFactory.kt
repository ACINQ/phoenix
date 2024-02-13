package fr.acinq.phoenix.controllers

import fr.acinq.phoenix.controllers.config.*
import fr.acinq.phoenix.controllers.init.Initialization
import fr.acinq.phoenix.controllers.init.RestoreWallet
import fr.acinq.phoenix.controllers.main.Content
import fr.acinq.phoenix.controllers.main.Home
import fr.acinq.phoenix.controllers.payments.Receive
import fr.acinq.phoenix.controllers.payments.Scan

typealias ContentController = MVI.Controller<Content.Model, Content.Intent>
typealias HomeController = MVI.Controller<Home.Model, Home.Intent>
typealias InitializationController = MVI.Controller<Initialization.Model, Initialization.Intent>
typealias ReceiveController = MVI.Controller<Receive.Model, Receive.Intent>
typealias ScanController = MVI.Controller<Scan.Model, Scan.Intent>
typealias RestoreWalletController = MVI.Controller<RestoreWallet.Model, RestoreWallet.Intent>

typealias CloseChannelsConfigurationController = MVI.Controller<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
typealias ConfigurationController = MVI.Controller<Configuration.Model, Configuration.Intent>
typealias ElectrumConfigurationController = MVI.Controller<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>

/** Lets us define different implementation for the controllers, which is useful for mocks. */
interface ControllerFactory {
    fun content(): ContentController
    fun initialization(): InitializationController
    fun home(): HomeController
    fun receive(): ReceiveController
    fun scan(firstModel: Scan.Model = Scan.Model.Ready): ScanController
    fun restoreWallet(): RestoreWalletController
    fun configuration(): ConfigurationController
    fun electrumConfiguration(): ElectrumConfigurationController
    fun closeChannelsConfiguration(): CloseChannelsConfigurationController
    fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController
}
