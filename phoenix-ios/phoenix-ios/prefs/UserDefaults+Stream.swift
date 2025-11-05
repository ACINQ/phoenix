import Foundation

fileprivate let filename = "UserDefaults+Stream"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension UserDefaults: @retroactive @unchecked Sendable {}
extension UserDefaults {
	
	func observeKey<Value: Sendable>(_ key: String, valueType _: Value.Type) -> AsyncStream<Value?> {
		
		return AsyncStream<Value?>(bufferingPolicy: .bufferingNewest(1)) { continuation in
			
			let observer = KVOObserver { newValue in
				continuation.yield(newValue)
			}
			continuation.onTermination = { [weak self] termination in
				log.trace("UserDefaults.observeKey('\(key)') sequence terminated. Reason: \(termination)")
				if let self {
					// Referencing `observer` here retains it.
					self.removeObserver(observer, forKeyPath: key)
				}
			}
			self.addObserver(observer, forKeyPath: key, options: [.initial, .new], context: nil)
		}
	}
}

private final class KVOObserver<Value: Sendable>: NSObject, Sendable {
	let send: @Sendable (Value?) -> Void

	init(send: @escaping @Sendable (Value?) -> Void) {
		self.send = send
	}

	deinit {
		log.trace("KVOObserver: deinit")
	}

	override func observeValue(
		forKeyPath keyPath: String?,
		of object: Any?,
		change: [NSKeyValueChangeKey: Any]?,
		context: UnsafeMutableRawPointer?
	) {
		let newValue = change![.newKey]!
		switch newValue {
		case let typed as Value:
			send(typed)
		case nil as Value?:
			send(nil)
		default:
			log.error(
				"""
				UserDefaults value at keyPath '\(keyPath!)' \
				has unexpected type \(type(of: newValue)), \
				expected \(Value.self)
				""")
		}
	}
}
