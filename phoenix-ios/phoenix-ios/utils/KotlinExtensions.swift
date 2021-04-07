import Foundation
import PhoenixShared
import Combine
import CryptoKit

extension Lightning_kmpConnection {
	
	func localizedText() -> String {
		switch self {
		case .closed       : return NSLocalizedString("Offline", comment: "Connection state")
		case .establishing : return NSLocalizedString("Connecting...", comment: "Connection state")
		case .established  : return NSLocalizedString("Connected", comment: "Connection state")
		default            : return NSLocalizedString("Unknown", comment: "Connection state")
		}
	}
}

extension KotlinByteArray {
	
	static func fromSwiftData(_ data: Data) -> KotlinByteArray {
		
		let kba = KotlinByteArray(size: Int32(data.count))
		for (idx, byte) in data.enumerated() {
			kba.set(index: Int32(idx), value: Int8(bitPattern: byte))
		}
		return kba
	}
	
	func toSwiftData() -> Data {

		let size = self.size
		var data = Data(count: Int(size))
		for idx in 0 ..< size {
			let byte: Int8 = self.get(index: idx)
			data[Int(idx)] = UInt8(bitPattern: byte)
		}
		return data
	}
}

extension Bitcoin_kmpByteVector32 {
	
	static func random() -> Bitcoin_kmpByteVector32 {
		
		let key = SymmetricKey(size: .bits256) // 256 / 8 = 32
		
		let data = key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		
		return Bitcoin_kmpByteVector32(bytes: KotlinByteArray.fromSwiftData(data))
	}
}

extension ConnectionsMonitor {
	
	var currentValue: Connections {
		return connections.value as! Connections
	}
	
	var publisher: CurrentValueSubject<Connections, Never> {

		let publisher = CurrentValueSubject<Connections, Never>(currentValue)

		let swiftFlow = SwiftFlow<Connections>(origin: connections)
		swiftFlow.watch {[weak publisher](connections: Connections?) in
			publisher?.send(connections!)
		}

		return publisher
	}
}

class ObservableConnectionsMonitor: ObservableObject {
	
	@Published var connections: Connections
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let monitor = AppDelegate.get().business.connectionsMonitor
		connections = monitor.currentValue
		
		let swiftFlow = SwiftFlow<Connections>(origin: monitor.connections)
		
		watcher = swiftFlow.watch {[weak self](newConnections: Connections?) in
			self?.connections = newConnections!
		}
	}
	
	#if DEBUG // For debugging UI: Force connection state
	init(fakeConnections: Connections) {
		self.connections = fakeConnections
	}
	#endif
	
	deinit {
		watcher?.close()
	}
}

class ObservableLastIncomingPayment: ObservableObject {
	
	@Published var value: Lightning_kmpWalletPayment? = nil
	
	private var watcher: Ktor_ioCloseable? = nil
	
	init() {
		let lastIncomingPaymentFlow = AppDelegate.get().business.paymentsManager.lastIncomingPayment
		value = lastIncomingPaymentFlow.value as? Lightning_kmpWalletPayment
		
		let swiftFlow = SwiftFlow<Lightning_kmpWalletPayment>(origin: lastIncomingPaymentFlow)
		
		watcher = swiftFlow.watch {[weak self](payment: Lightning_kmpWalletPayment?) in
			self?.value = payment
		}
	}
	
	deinit {
		watcher?.close()
	}
}

//class ObservableBitcoinRates: ObservableObject {
//
//    @Published var value: Array<BitcoinPriceRate> = []
//
//    private var watcher: Ktor_ioCloseable? = nil
//
//    init() {
//        let ratesFlow = AppDelegate.get().business.currencyManager.ratesFlow
//
//        let swiftFlow = SwiftFlow<Array<BitcoinPriceRate>>(origin: ratesFlow)
//        swiftFlow.watch {[weak self](rates: Array<BitcoinPriceRate>?) in
//            self?.value = rates
//        }
//    }
//
//    deinit {
//        watcher?.close()
//    }
//}

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
		watcher?.close()
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
			self?.wrapped.send(value!)
		}
	}
	
	deinit {
	//	Swift.print("KotlinCurrentValueSubject: deinit")
		watcher?.close()
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

extension FiatCurrency {
	
	var shortName: String {
		return name.uppercased()
	}
	
	var longName: String {
		
		switch self {
			case FiatCurrency.aud : return NSLocalizedString("Australian Dollar",    comment: "Currency name: AUD")
			case FiatCurrency.brl : return NSLocalizedString("Brazilian Real",       comment: "Currency name: BRL")
			case FiatCurrency.cad : return NSLocalizedString("Canadian Dollar",      comment: "Currency name: CAD")
			case FiatCurrency.chf : return NSLocalizedString("Swiss Franc",          comment: "Currency name: CHF")
			case FiatCurrency.clp : return NSLocalizedString("Chilean Peso",         comment: "Currency name: CLP")
			case FiatCurrency.cny : return NSLocalizedString("Chinese Yuan",         comment: "Currency name: CNY")
			case FiatCurrency.dkk : return NSLocalizedString("Danish Krone",         comment: "Currency name: DKK")
			case FiatCurrency.eur : return NSLocalizedString("Euro",                 comment: "Currency name: EUR")
			case FiatCurrency.gbp : return NSLocalizedString("Great British Pound",  comment: "Currency name: GBP")
			case FiatCurrency.hkd : return NSLocalizedString("Hong Kong Dollar",     comment: "Currency name: HKD")
			case FiatCurrency.inr : return NSLocalizedString("Indian Rupee",         comment: "Currency name: INR")
			case FiatCurrency.isk : return NSLocalizedString("Icelandic Kròna",      comment: "Currency name: ISK")
			case FiatCurrency.jpy : return NSLocalizedString("Japanese Yen",         comment: "Currency name: JPY")
			case FiatCurrency.krw : return NSLocalizedString("Korean Won",           comment: "Currency name: KRW")
			case FiatCurrency.mxn : return NSLocalizedString("Mexican Peso",         comment: "Currency name: MXN")
			case FiatCurrency.nzd : return NSLocalizedString("New Zealand Dollar",   comment: "Currency name: NZD")
			case FiatCurrency.pln : return NSLocalizedString("Polish Zloty",         comment: "Currency name: PLN")
			case FiatCurrency.rub : return NSLocalizedString("Russian Ruble",        comment: "Currency name: RUB")
			case FiatCurrency.sek : return NSLocalizedString("Swedish Krona",        comment: "Currency name: SEK")
			case FiatCurrency.sgd : return NSLocalizedString("Singapore Dollar",     comment: "Currency name: SGD")
			case FiatCurrency.thb : return NSLocalizedString("Thai Baht",            comment: "Currency name: THB")
			case FiatCurrency.twd : return NSLocalizedString("Taiwan New Dollar",    comment: "Currency name: TWD")
			case FiatCurrency.usd : return NSLocalizedString("United States Dollar", comment: "Currency name: USD")
			default               : break
		}
		
		return self.name
	}
}

extension BitcoinUnit {
	
	var shortName: String {
		return name.lowercased()
	}
	
	var explanation: String {
		
		let s = FormattedAmount.fractionGroupingSeparator // narrow no-break space
		switch (self) {
			case BitcoinUnit.sat  : return "0.000\(s)000\(s)01 BTC"
			case BitcoinUnit.bit  : return "0.000\(s)001 BTC"
			case BitcoinUnit.mbtc : return "0.001 BTC"
			case BitcoinUnit.btc  : return ""
			default               : break
		}
		
		return self.name
	}
}
