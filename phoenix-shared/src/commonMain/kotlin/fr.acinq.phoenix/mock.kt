package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.provider


class MockDIBuilder {
    var contentModel: Content.Model = Content.Model.NeedInitialization
    var initModel: Init.Model = Init.Model.Initialization
    var homeModel: Home.Model = Home.emptyModel
    var receiveModel: Receive.Model = Receive.Model.Generating
    var scanModel: Scan.Model = Scan.Model.Ready
    var restoreWalletModel: RestoreWallet.Model = RestoreWallet.Model.Ready
    var configurationModel: Configuration.Model = Configuration.Model.SimpleMode
    var displayConfigurationModel: DisplayConfiguration.Model = DisplayConfiguration.Model()
    var electrumConfigurationModel: ElectrumConfiguration.Model = ElectrumConfiguration.Model()
    var channelsConfigurationModel: ChannelsConfiguration.Model = ChannelsConfiguration.emptyModel

    fun apply(block: MockDIBuilder.() -> Unit): MockDIBuilder {
        this.block()
        return this
    }

    fun di() = DI {
        bind<ContentController>() with provider { Content.MockController(contentModel) }
        bind<InitController>() with provider { Init.MockController(initModel) }
        bind<HomeController>() with provider { Home.MockController(homeModel) }
        bind<ReceiveController>() with provider { Receive.MockController(receiveModel) }
        bind<ScanController>() with provider { Scan.MockController(scanModel) }
        bind<RestoreWalletController>() with provider { RestoreWallet.MockController(restoreWalletModel) }
        bind<ConfigurationController>() with provider { Configuration.MockController(configurationModel) }
        bind<DisplayConfigurationController>() with provider { DisplayConfiguration.MockController(displayConfigurationModel) }
        bind<ElectrumConfigurationController>() with provider { ElectrumConfiguration.MockController(electrumConfigurationModel) }
        bind<ChannelsConfigurationController>() with provider { ChannelsConfiguration.MockController(channelsConfigurationModel) }
    }
}
