import SwiftUI
import PhoenixShared

class ObservableControllerFactory : ObservableObject {
    let controllerFactory: ControllerFactory

    init(_ controllerFactory: ControllerFactory) {
        self.controllerFactory = controllerFactory
    }
}

class MockControllerFactory : ControllerFactory {
	
    func channelsConfiguration() -> MVIController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent> {
        MVIControllerMock(model: ChannelsConfigurationView_Previews.mockModel)
    }

    func configuration() -> MVIController<Configuration.Model, Configuration.Intent> {
        MVIControllerMock(model: ConfigurationView_Previews.mockModel)
    }

    func content() -> MVIController<Content.Model, Content.Intent> {
        MVIControllerMock(model: ContentView_Previews.mockModel)
    }

    func electrumConfiguration() -> MVIController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent> {
        MVIControllerMock(model: ElectrumConfigurationView_Previews.mockModel)
    }

    func home() -> MVIController<Home.Model, Home.Intent> {
        MVIControllerMock(model: HomeView_Previews.mockModel)
    }

    func initialization() -> MVIController<Initialization.Model, Initialization.Intent> {
        MVIControllerMock(model: InitView_Previews.mockModel)
    }

    func receive() -> MVIController<Receive.Model, Receive.Intent> {
        MVIControllerMock(model: ReceiveView_Previews.mockModel)
    }

    func restoreWallet() -> MVIController<RestoreWallet.Model, RestoreWallet.Intent> {
        MVIControllerMock(model: RestoreWalletView_Previews.mockModel)
    }

    func scan() -> MVIController<Scan.Model, Scan.Intent> {
        MVIControllerMock(model: ScanView_Previews.mockModel)
    }
}

func appView<V : View>(_ content: V) -> some View {
    content.environmentObject(ObservableControllerFactory(PhoenixApplicationDelegate.get().business.controllers))
}

func mockView<V : View>(_ content: V) -> some View {
    content.environmentObject(ObservableControllerFactory(MockControllerFactory()))
}
