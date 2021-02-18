import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MVI"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class MVIState<Model: MVI.Model, Intent: MVI.Intent>: ObservableObject {
	
	@Published private var _model: Model? = nil
	
	var model: Model {
		return _model!
	}
	
	private let getController: (ControllerFactory) -> MVIController<Model, Intent>
	private var controller: MVIController<Model, Intent>? = nil
	
	private var unsub: (() -> Void)? = nil
	private var subCount: Int = 0

	init(_ getController: @escaping (ControllerFactory) -> MVIController<Model, Intent>) {
		self.getController = getController
	}

	deinit {
		let _unsub = unsub
		let _controller = controller
		DispatchQueue.main.async {
			_unsub?()
			_controller?.stop()
		}
	}
	
	fileprivate func initializeControllerIfNeeded(_ factory: ControllerFactory) -> Void {
		if controller == nil {
			controller = getController(factory)
			_model = controller!.firstModel
		}
	}

	fileprivate func subscribe() {
		if unsub == nil {
			unsub = controller!.subscribe {[weak self](newModel: Model) in
				self?._model = newModel
			}
		}
		subCount += 1
	}

	fileprivate func unsubscribe() {
		subCount -= 1
		if subCount < 0 { fatalError("subCount < 0") }
		if subCount == 0 {
			unsub!()
			unsub = nil
		}
	}

	func intent(_ intent: Intent) {
		controller?.intent(intent: intent)
	}
}

protocol MVIView : View {
	
	associatedtype MVIModel: MVI.Model
	associatedtype MVIIntent: MVI.Intent
	
	var mvi: MVIState<MVIModel, MVIIntent> { get }
	
	/// The type of view represented in `view()`.
	///
	/// When you create a custom view, Swift infers this type from your
	/// implementation of the required `view` getter.
	associatedtype ViewBody : View
	
	/// The content and behavior of the view.
	@ViewBuilder var view: Self.ViewBody { get }
	
	/// Implement this via the following code:
	/// > @Environment(\.controllerFactory) var factoryEnv
	/// > var factory: ControllerFactory { return factoryEnv }
	///
	var factory: ControllerFactory { get }
}

extension MVIView {
	
	var body: some View {
		mvi.initializeControllerIfNeeded(self.factory)
		return view.onAppear {
			mvi.subscribe()
		}.onDisappear {
			mvi.unsubscribe()
		}
	}
}

fileprivate struct ControllerFactoryKey: EnvironmentKey {
	static let defaultValue: ControllerFactory = AppDelegate.get().business.controllers
}

extension EnvironmentValues {
	var controllerFactory: ControllerFactory {
		get { self[ControllerFactoryKey.self] }
		set { self[ControllerFactoryKey.self] = newValue }
	}
}

extension View {
	func mock(_ mock: ChannelsConfiguration.Model) -> some View {
		environment(\.controllerFactory, MockControllerFactory(mock))
	}
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
	func mock(_ mock: LogsConfiguration.Model) -> some View {
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
	
	let base: ControllerFactory = AppDelegate.get().business.controllers
	
	var mock_channelsConfiguration: ChannelsConfiguration.Model? = nil
	init(_ mock: ChannelsConfiguration.Model) {
		mock_channelsConfiguration = mock
	}
	func channelsConfiguration() -> MVIController<ChannelsConfiguration.Model, ChannelsConfiguration.Intent> {
		if let mock = mock_channelsConfiguration {
			return MVIControllerMock(model: mock)
		} else {
			return base.channelsConfiguration()
		}
	}
	
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
	
	var mock_logsConfiguration: LogsConfiguration.Model? = nil
	init(_ mock: LogsConfiguration.Model) {
		mock_logsConfiguration = mock
	}
	func logsConfiguration() -> MVIController<LogsConfiguration.Model, LogsConfiguration.Intent> {
		if let mock = mock_logsConfiguration {
			return MVIControllerMock(model: mock)
		} else {
			return base.logsConfiguration()
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
	func scan() -> MVIController<Scan.Model, Scan.Intent> {
		if let mock = mock_scan {
			return MVIControllerMock(model: mock)
		} else {
			return base.scan()
		}
	}
}

