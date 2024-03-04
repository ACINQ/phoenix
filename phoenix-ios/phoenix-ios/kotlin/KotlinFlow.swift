import Foundation
import Combine
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

class KotlinPassthroughSubject<T: AnyObject>: Publisher {
	
	typealias Output = T?
	typealias Failure = Never
	
	private let wrapped: PassthroughSubject<T?, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	init(_ flowWrapper: SwiftFlow<T>) {
		
		// There's no need to retain the SwiftFlow instance variable,
		// because the instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		wrapped = PassthroughSubject<T?, Failure>()
		
		watcher = flowWrapper.watch {[weak self](value: T?) in
			self?.wrapped.send(value)
		}
	}
	
	convenience init(_ flow: PhoenixShared.SkieKotlinFlow<T>) {
		
		self.init(SwiftFlow<T>(origin: flow))
	}
	
	convenience init(_ flow: PhoenixShared.SkieKotlinOptionalFlow<T>) {
		
		self.init(SwiftFlow<T>(origin: flow))
	}
	
	convenience init(_ flow: PhoenixShared.SkieKotlinSharedFlow<T>) {
		
		self.init(SwiftFlow<T>(origin: flow))
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

class KotlinCurrentValueSubject<T: AnyObject>: Publisher {
	
	typealias Output = T?
	typealias Failure = Never
	
	private let wrapped: CurrentValueSubject<T?, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	init(_ stateFlowWrapper: SwiftStateFlow<T>) {
		
		// There's no need to retain the SwiftStateFlow instance variable,
		// because the instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		let initialValue = stateFlowWrapper.value
		wrapped = CurrentValueSubject(initialValue)
		
		watcher = stateFlowWrapper.watch {[weak self](value: T?) in
			self?.wrapped.send(value)
		}
	}
	
	convenience init(_ stateFlow: PhoenixShared.SkieKotlinStateFlow<T>) {
		
		self.init(SwiftStateFlow<T>(origin: stateFlow))
	}
	
	convenience init(_ stateFlow: PhoenixShared.SkieKotlinOptionalStateFlow<T>) {
		
		self.init(SwiftStateFlow<T>(origin: stateFlow))
	}
	
	convenience init(_ stateFlow: PhoenixShared.SkieKotlinOptionalMutableStateFlow<T>) {
		
		self.init(SwiftStateFlow<T>(origin: stateFlow))
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
