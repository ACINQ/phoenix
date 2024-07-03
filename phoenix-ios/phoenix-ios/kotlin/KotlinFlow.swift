import Combine
import Foundation
import PhoenixShared

/**
 * The `KotlinPassthroughSubject` & `KotlinCurrentValueSubject` publishers allow us to work
 * with Kotlin's `Flow` & `StateFlow` types, but using native Publisher types.
 *
 * Both `KotlinPassthroughSubject` & `KotlinCurrentValueSubject` are defined with a generic type:
 *
 * - Output: This is the exported type from Kotlin, and will be an objective-c type (AnyObject)
 *
 * The published type is always optional, i.e. `Output?`
 *
 * Note that `SwiftFlow` is defined in:
 * phoenix-shared/src/commonMain/kotlin/fr.acinq.phoenix/utils/SwiftFlow.kt
*/

class KotlinPassthroughSubject<ObjCType: AnyObject>: Publisher {
	
	typealias Output = ObjCType?
	typealias Failure = Never
	
	private let wrapped: PassthroughSubject<ObjCType?, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	init(_ flow: Kotlinx_coroutines_coreFlow) {
		
		let flowWrapper = SwiftFlow<ObjCType>(origin: flow)
		
		// There's no need to retain the SwiftFlow instance variable,
		// because the instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		wrapped = PassthroughSubject<ObjCType?, Failure>()
		
		watcher = flowWrapper.watch {[weak self](value: ObjCType?) in
			self?.wrapped.send(value)
		}
	}

	deinit {
	//	Swift.print("KotlinPassthroughSubject: deinit")
		let _watcher = watcher
		DispatchQueue.main.async {
			// have witnessed crashes when invoking `watcher?.close()` from  a non-main thread
			_watcher?.close()
		}
	}
	
	func receive<Downstream: Subscriber>(subscriber: Downstream)
		where Failure == Downstream.Failure, Output == Downstream.Input
	{
		wrapped.subscribe(subscriber)
	}
}

class KotlinCurrentValueSubject<ObjCType: AnyObject>: Publisher {
	
	typealias Output = ObjCType?
	typealias Failure = Never
	
	private let wrapped: CurrentValueSubject<ObjCType?, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	init(_ stateFlow: Kotlinx_coroutines_coreStateFlow) {
		
		let stateFlowWrapper = SwiftStateFlow<ObjCType>(origin: stateFlow)
		
		// There's no need to retain the SwiftStateFlow instance variable,
		// because the instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		let initialValue = stateFlowWrapper.value
		wrapped = CurrentValueSubject(initialValue)
		
		watcher = stateFlowWrapper.watch {[weak self](value: ObjCType?) in
			self?.wrapped.send(value)
		}
	}
	
	deinit {
	//	Swift.print("KotlinCurrentValueSubject: deinit")
		let _watcher = watcher
		DispatchQueue.main.async {
			// have witnessed crashes when invoking `watcher?.close()` from  a non-main thread
			_watcher?.close()
		}
	}
	
	var value: Output? {
		get {
			return wrapped.value
		}
	}

	func receive<Downstream: Subscriber>(subscriber: Downstream)
		where Failure == Downstream.Failure, Output == Downstream.Input
	{
		wrapped.subscribe(subscriber)
	}
}
