import SwiftUI
import PhoenixShared

class ObservableControllerFactory : ObservableObject {
    let controllerFactory: ControllerFactory

    init(_ controllerFactory: ControllerFactory) {
        self.controllerFactory = controllerFactory
    }
}

class MockControllerFactory : ControllerFactory {
    func content() -> MVIController<Content.Model, Content.Intent> { MVIControllerMock(model: ContentView_Previews.mockModel) }
    func initialization() -> MVIController<Initialization.Model, Initialization.Intent> { MVIControllerMock(model: InitView_Previews.mockModel) }
    func home() -> MVIController<Home.Model, Home.Intent> { MVIControllerMock(model: HomeView_Previews.mockModel) }
    func receive() -> MVIController<Receive.Model, Receive.Intent> { MVIControllerMock(model: ReceiveView_Previews.mockModel) }
    func scan() -> MVIController<Scan.Model, Scan.Intent> { MVIControllerMock(model: ScanView_Previews.mockModel) }
    func restoreWallet() -> MVIController<RestoreWallet.Model, RestoreWallet.Intent> { MVIControllerMock(model: RestoreWalletView_Previews.mockModel) }
    func configuration() -> MVIController<Configuration.Model, Configuration.Intent> { MVIControllerMock(model: ConfigurationView_Previews.mockModel) }
    func electrumConfiguration() -> MVIController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent> { MVIControllerMock(model: ElectrumConfigurationView_Previews.mockModel) }
    func channelsConfiguration() -> MVIController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent> { MVIControllerMock(model: ChannelsConfigurationView_Previews.mockModel) }
    func logsConfiguration() -> MVIController<LogsConfiguration.Model, LogsConfiguration.Intent> { MVIControllerMock(model: LogsConfigurationView_Previews.mockModel) }
    func closeChannelsConfiguration() -> MVIController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent> {
		MVIControllerMock(model: CloseChannelsView_Previews.mockModel)
	}
}

func appView<V : View>(_ content: V) -> some View {
    content.environmentObject(ObservableControllerFactory(AppDelegate.get().business.controllers))
}

func mockView<V : View>(_ content: V, nav: NavigationBarItem.TitleDisplayMode? = nil) -> some View {
    let v: AnyView

    if let nav = nav {
        v = AnyView(NavigationView { content.navigationBarTitleDisplayMode(nav) })
    } else {
        v = AnyView(content)
    }

    return v.environmentObject(ObservableControllerFactory(MockControllerFactory()))
}
