import Combine
import PhoenixShared

/**
 * The `KotlinPassthroughSubject` & `KotlinCurrentValueSubject` publishers allow us to work
 * with Kotlin's `Flow` & `StateFlow` types, but using native Publisher types.
 *
 * Both `KotlinPassthroughSubject` & `KotlinCurrentValueSubject` are defined with 2 generic types:
 *
 * - Input: This is the exported type from Kotlin, and will be an objective-c type (AnyObject)
 * - Output: This is the type we want to consume in Swift
 *
 * The conversion will be performed in this manner:
 * ```
 * kotlinFlow.watch {[weak self](value: Input?) in
 *   if let value = value as? Output {
 *     self?.publisher.send(value)
 *   }
 * }
 * ```
 *
 * By defining the Ouput type, you control conversions, and whether or not to allow nil values.
 * ```
 * // This will convert from NSDictionary, to native Swift Dictionary type.
 * // It will also filter out any nil values emitted from the underlying Kotlin StateFlow.
 * KotlinPassthroughSubject<NSDictionary, [String: Lightning_kmpMilliSatoshi]>
 *
 * // This will convert from NSString to native Swift String type.
 * // It will allow optional values to be emitted from the underlying Kotlin StateFlow.
 * KotlinCurrentValueSubject<NSString, String?>
 * ```
 *
 * Note that `SwiftFlow` is defined in:
 * phoenix-shared/src/commonMain/kotlin/fr.acinq.phoenix/utils/SwiftFlow.kt
*/

class KotlinPassthroughSubject<Input: AnyObject, Output: Any>: Publisher {
	
	typealias Failure = Never
	
	private let wrapped: PassthroughSubject<Output, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	convenience init(_ flow: Kotlinx_coroutines_coreFlow) {
		
		self.init(SwiftFlow(origin: flow))
	}
	
	init(_ swiftFlow: SwiftFlow<Input>) {
		
		// There's no need to retain the SwiftFlow instance variable.
		// Because the SwiftFlow instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		wrapped = PassthroughSubject<Output, Failure>()
		
		watcher = swiftFlow.watch {[weak self](value: Input?) in
			if let value = value as? Output {
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

class KotlinCurrentValueSubject<Input: AnyObject, Output: Any>: Publisher {
	
	typealias Failure = Never
	
	private let wrapped: CurrentValueSubject<Output, Failure>
	private var watcher: Ktor_ioCloseable? = nil
	
	convenience init(_ stateFlow: Kotlinx_coroutines_coreStateFlow) {
		
		self.init(SwiftStateFlow(origin: stateFlow))
	}
	
	init(_ swiftStateFlow: SwiftStateFlow<Input>) {
		
		// There's no need to retain the SwiftStateFlow instance variable.
		// Because the SwiftStateFlow instance itself doesn't maintain any state.
		// All state is encapsulated in the watch method.
		
		let initialValue = swiftStateFlow.value_ as! Output
		wrapped = CurrentValueSubject(initialValue)
		
		watcher = swiftStateFlow.watch {[weak self](value: Input?) in
			if let value = value as? Output {
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
