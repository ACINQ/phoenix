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
		if unsub == nil {
			unsub = _controller!.subscribe {[weak self](newModel: Model) in
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
		_controller?.intent(intent: intent)
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
