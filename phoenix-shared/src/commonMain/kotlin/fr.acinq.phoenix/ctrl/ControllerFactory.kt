package fr.acinq.phoenix.ctrl

import fr.acinq.phoenix.ctrl.config.ChannelsConfigurationController
import fr.acinq.phoenix.ctrl.config.ConfigurationController
import fr.acinq.phoenix.ctrl.config.DisplayConfigurationController
import fr.acinq.phoenix.ctrl.config.ElectrumConfigurationController
import fr.acinq.phoenix.ctrl.config.RecoveryPhraseConfigurationController


interface ControllerFactory {
    fun content(): ContentController
    fun initialization(): InitializationController
    fun home(): HomeController
    fun receive(): ReceiveController
    fun scan(): ScanController
    fun restoreWallet(): RestoreWalletController
    fun configuration(): ConfigurationController
    fun displayConfiguration(): DisplayConfigurationController
    fun electrumConfiguration(): ElectrumConfigurationController
    fun channelsConfiguration(): ChannelsConfigurationController
    fun recoveryPhraseConfiguration(): RecoveryPhraseConfigurationController
}
