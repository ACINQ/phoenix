import SwiftUI
import PhoenixShared


extension View {
	func mock(_ mock: CloseChannelsConfiguration.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Configuration.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Content.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: ElectrumConfiguration.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Home.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Initialization.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Receive.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: RestoreWallet.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
	func mock(_ mock: Scan.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
}

class MockControllerFactory : ControllerFactory {
	
	let base: ControllerFactory = Biz.business.controllers
	
	var mock_closeChannelsConfiguration: CloseChannelsConfiguration.Model? = nil
	init(_ mock: CloseChannelsConfiguration.Model) {
		mock_closeChannelsConfiguration = mock
	}
	func closeChannelsConfiguration() -> MVIController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent> {
		if let mock = mock_closeChannelsConfiguration {
			return MVIControllerMock(model: mock)
		} else {
			return base.closeChannelsConfiguration()
		}
	}
	
	var mock_configuration: Configuration.Model? = nil
	init(_ mock: Configuration.Model) {
		mock_configuration = mock
	}
	func configuration() -> MVIController<Configuration.Model, Configuration.Intent> {
		if let mock = mock_configuration {
			return MVIControllerMock(model: mock)
		} else {
			return base.configuration()
		}
	}
	
	var mock_content: Content.Model? = nil
	init(_ mock: Content.Model) {
		mock_content = mock
	}
	func content() -> MVIController<Content.Model, Content.Intent> {
		if let mock = mock_content {
			return MVIControllerMock(model: mock)
		} else {
			return base.content()
		}
	}
	
	var mock_electrumConfiguration: ElectrumConfiguration.Model? = nil
	init(_ mock: ElectrumConfiguration.Model) {
		mock_electrumConfiguration = mock
	}
	func electrumConfiguration() -> MVIController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent> {
		if let mock = mock_electrumConfiguration {
			return MVIControllerMock(model: mock)
		} else {
			return base.electrumConfiguration()
		}
	}
	
	func forceCloseChannelsConfiguration() -> MVIController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent> {
		if let mock = mock_closeChannelsConfiguration {
			return MVIControllerMock(model: mock)
		} else {
			return base.forceCloseChannelsConfiguration()
		}
	}
	
	var mock_home: Home.Model? = nil
	init(_ mock: Home.Model) {
		mock_home = mock
	}
	func home() -> MVIController<Home.Model, Home.Intent> {
		if let mock = mock_home {
			return MVIControllerMock(model: mock)
		} else {
			return base.home()
		}
	}
		
	var mock_initialization: Initialization.Model? = nil
	init(_ mock: Initialization.Model) {
		mock_initialization = mock
	}
	func initialization() -> MVIController<Initialization.Model, Initialization.Intent> {
		if let mock = mock_initialization {
			return MVIControllerMock(model: mock)
		} else {
			return base.initialization()
		}
	}
	
	var mock_receive: Receive.Model? = nil
	init(_ mock: Receive.Model) {
		mock_receive = mock
	}
	func receive() -> MVIController<Receive.Model, Receive.Intent> {
		if let mock = mock_receive {
			return MVIControllerMock(model: mock)
		} else {
			return base.receive()
		}
	}
	
	var mock_restoreWallet: RestoreWallet.Model? = nil
	init(_ mock: RestoreWallet.Model) {
		mock_restoreWallet = mock
	}
	func restoreWallet() -> MVIController<RestoreWallet.Model, RestoreWallet.Intent> {
		if let mock = mock_restoreWallet {
			return MVIControllerMock(model: mock)
		} else {
			return base.restoreWallet()
		}
	}
	
	var mock_scan: Scan.Model? = nil
	init(_ mock: Scan.Model) {
		mock_scan = mock
	}	
	func scan(firstModel: Scan.Model) -> MVIController<Scan.Model, Scan.Intent> {
		if let mock = mock_scan {
			return MVIControllerMock(model: mock)
		} else {
			return base.scan(firstModel: firstModel)
		}
	}
}
