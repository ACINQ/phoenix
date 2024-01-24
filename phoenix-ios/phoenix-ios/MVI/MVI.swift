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
	
	private var _initialModel: Model? = nil
	@Published private var _model: Model? = nil
	
	var model: Model {
		if let updatedModel = _model {
			return updatedModel
		} else {
			return _initialModel!
		}
	}
	
	private let _getController: ((ControllerFactory) -> MVIController<Model, Intent>)?
	private var _controller: MVIController<Model, Intent>?
	
	var controller: MVIController<Model, Intent>? {
		return _controller
	}
	
	private var unsub: (() -> Void)? = nil
	private var subCount: Int = 0

	init(_ getController: @escaping (ControllerFactory) -> MVIController<Model, Intent>) {
		_getController = getController
		_controller = nil
	}
	
	init(_ controller: MVIController<Model, Intent>) {
		_getController = nil
		_controller = controller
		_initialModel = controller.firstModel
	}

	deinit {
		let __unsub = unsub
		let __controller = _controller
		DispatchQueue.main.async {
			__unsub?()
			__controller?.stop()
		}
	}
	
	/// Called automatically when using MVIView.
	/// 
	fileprivate func initializeControllerIfNeeded(_ factory: ControllerFactory) -> Void {
		if _controller == nil {
			if let getController = _getController {
				let controller = getController(factory)
				_controller = controller
				_initialModel = controller.firstModel
				
				// Architecture note:
				// This method is called from MVIView.body, which is a ViewBuilder.
				// And if you attempt to update the @Published `_model` variable from here,
				// then you will get a runtime warning:
				//
				// > Publishing changes from within view updates is not allowed,
				// > this will cause undefined behavior.
				//
				// This is the reason we have both `_initialModel` && `_model`.
			}
		}
	}

	fileprivate func subscribe() {
		log.debug("subscribe()")
		if unsub == nil {
			unsub = _controller!.subscribe {[weak self](newModel: Model) in
				self?._model = newModel
			}
		}
		subCount += 1
		log.debug("subcount = \(self.subCount)")
	}

	fileprivate func unsubscribe() {
		log.debug("unsubscribe()")
		subCount -= 1
		log.debug("subcount = \(self.subCount)")
		if subCount < 0 { fatalError("subCount < 0") }
		if subCount == 0 {
			unsub!()
			unsub = nil
		}
	}

	func intent(_ intent: Intent) {
		_controller?.intent(intent: intent)
	}
}

// MARK: -

/// A view should implement the `MVIView` protocol when it has a `@StateObject var mvi` property.
/// The implementation looks something like this:
///
/// > struct MyView: MVIView {
/// >   @StateObject var mvi = MVIState({ $0.someFactoryMethodHere() })
/// >
/// >   @Environment(\.controllerFactory) var factoryEnv
/// >   var factory: ControllerFactory { return factoryEnv }
/// >
/// >   @ViewBuilder var view: some View {
/// >     // your view code here
/// >   }
/// > }
///
/// Note: instead of implementing `var body: some View`, you instead implement `var view: some View`.
///
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

// MARK: -

/// A view should implement the `MVISubView` protocol when it has an `@ObservedObject var mvi` property.
/// The implementation looks something like this:
///
/// > struct MyView: MVISubView {
/// >   @ObservedObject var mvi: MVIState<Something.Model, Something.Intent>
/// >
/// >   @ViewBuilder var view: some View {
/// >     // your view code here
/// >   }
/// > }
///
/// Note: instead of implementing `var body: some View`, you instead implement `var view: some View`.
///
/// It's important to adopt the MVISubView protocol when:
/// - The mvi owner (i.e. with `@StateObject var mvi`) may disappear
/// - The mvi non-owner (i.e. with `@ObservedObject var mvi`) still depends on mvi.model change notifications
///
/// This is because the owner will automatically unsubscribe from notifications in `onDisappear`.
/// So if the non-owner needs change notifications, it must perform the subscribe/unsubscribe calls.
/// This is done for you automatically via `MVIView` & `MVISubView`.
///
protocol MVISubView : View {
	
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
}

extension MVISubView {
	
	var body: some View {
		return view.onAppear {
			mvi.subscribe()
		}.onDisappear {
			mvi.unsubscribe()
		}
	}
}

// MARK: -

fileprivate struct ControllerFactoryKey: EnvironmentKey {
	static let defaultValue: ControllerFactory = FakeControllerFactory()
}

extension EnvironmentValues {
	var controllerFactory: ControllerFactory {
		get { self[ControllerFactoryKey.self] }
		set { self[ControllerFactoryKey.self] = newValue }
	}
}

/// To support closing the current wallet, we use a FakeControllerFactory as the defaultValue.
/// The real ControllerFactory gets injected via the GlobalEnvironment.
///
class FakeControllerFactory: ControllerFactory {

	func closeChannelsConfiguration() ->
		MVIController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func configuration() ->
		MVIController<Configuration.Model, Configuration.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func content() ->
		MVIController<Content.Model, Content.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func electrumConfiguration() ->
		MVIController<ElectrumConfiguration.Model, ElectrumConfiguration.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func forceCloseChannelsConfiguration() ->
		MVIController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func home() ->
		MVIController<Home.Model, Home.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func initialization() ->
		MVIController<Initialization.Model, Initialization.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func receive() ->
		MVIController<Receive.Model, Receive.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func restoreWallet() ->
		MVIController<RestoreWallet.Model, RestoreWallet.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}

	func scan(firstModel: Scan.Model) ->
		MVIController<Scan.Model, Scan.Intent> {
			fatalError("Missing @Environment: ControllerFactory")
		}
}
