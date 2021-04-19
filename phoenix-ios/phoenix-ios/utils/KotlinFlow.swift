import Combine
import PhoenixShared

/**
 * The `KotlinPassthroughSubject` & `KotlinCurrentValueSubject` publishers allow us to work
 * with Kotlin's `Flow` & `StateFlow` types, but using native Publisher types.
 *
 * Generally, if you have Kotlin `StateFlow`, you would map that to a `KotlinCurrentValueSubject`.
 * Otherwise, you can map a generic Kotlin `Flow` to a `KotlinPassthroughSubject`.
 *
 * Note that `SwiftFlow` is defined in:
 * phoenix-shared/src/commonMain/kotlin/fr.acinq.phoenix/utils/SwiftFlow.kt
*/

/**
 * Caveat:
 * The current class definitions don't support optional types.
 * If needed, we would probably support it using something like:
 * `class KotlinPassthroughSubject<Inner: AnyObject, Outer: T>: Publisher`
 *
 * where Inner is the Kotlin type, and Outer is the Swift type.
 * So something like:
 * `= KotlinPassthroughSubject<NSDictionary, Dictionary[String: Int]?>`
*/

class KotlinPassthroughSubject<Output: AnyObject>: Publisher {
	
	typealias Failure = Never
	
	private let wrapped: PassthroughSubject<Output, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	convenience init(_ flow: Kotlinx_coroutines_coreFlow) {
		
		self.init(SwiftFlow(origin: flow))
	}
	
	init(_ swiftFlow: SwiftFlow<Output>) {
		
		// There's no need to retain the SwiftFlow instance variable.
		// Because the SwiftFlow instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		wrapped = PassthroughSubject<Output, Failure>()
		
		watcher = swiftFlow.watch {[weak self](value: Output?) in
			if let value = value {
				self?.wrapped.send(value)
			}
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

class KotlinCurrentValueSubject<Output: AnyObject>: Publisher {
	
	typealias Failure = Never
	
	private let wrapped: CurrentValueSubject<Output, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	convenience init(_ stateFlow: Kotlinx_coroutines_coreStateFlow) {
		
		self.init(SwiftStateFlow(origin: stateFlow))
	}
	
	init(_ swiftStateFlow: SwiftStateFlow<Output>) {
		
		// There's no need to retain the SwiftStateFlow instance variable.
		// Because the SwiftStateFlow instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		let initialValue = swiftStateFlow.value!
		wrapped = CurrentValueSubject(initialValue)
		
		watcher = swiftStateFlow.watch {[weak self](value: Output?) in
			if let value = value {
				self?.wrapped.send(value)
			}
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
	
	var value: Output {
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
