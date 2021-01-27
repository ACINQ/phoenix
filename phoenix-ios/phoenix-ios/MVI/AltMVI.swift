import SwiftUI
import PhoenixShared
import Combine
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "AltMVI"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class AltMVI<Model: MVI.Model, Intent: MVI.Intent>: ObservableObject {
	
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
	
	fileprivate func initializeControllerIfNeeded() -> Void {
		if controller == nil {
			let factory = AppDelegate.get().business.controllers
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

protocol AltMviView : View {
	
	associatedtype AltMviModel: MVI.Model
	associatedtype AltMviIntent: MVI.Intent
	
	var mvi: AltMVI<AltMviModel, AltMviIntent> { get }
	
	/// The type of view represented in `view()`.
	///
	/// When you create a custom view, Swift infers this type from your
	/// implementation of the required `view()` function.
	associatedtype ViewBody : View
	
	/// The content and behavior of the view.
	@ViewBuilder var view: Self.ViewBody { get }
}

extension AltMviView {
	
	var body: some View {
		mvi.initializeControllerIfNeeded()
		return view.onAppear {
			mvi.subscribe()
		}.onDisappear {
			mvi.unsubscribe()
		}
	}
}

