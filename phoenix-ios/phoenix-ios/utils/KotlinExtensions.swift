import Foundation
import PhoenixShared
import Combine
import CryptoKit


extension PaymentsManager {
	
	func incomingSwapsPublisher() -> AnyPublisher<[String: Lightning_kmpMilliSatoshi], Never> {
		
		// Transforming from Kotlin:
		// ```
		// incomingSwaps: StateFlow<Map<String, MilliSatoshi>>
		// ```
		return KotlinCurrentValueSubject<NSDictionary, [String: Lightning_kmpMilliSatoshi]>(
			AppDelegate.get().business.paymentsManager.incomingSwaps
		)
		.eraseToAnyPublisher()
	}
	
	func lastCompletedPaymentPublisher() -> AnyPublisher<Lightning_kmpWalletPayment, Never> {
		
		// Transforming from Kotlin:
		// ```
		// lastCompletedPayment: StateFlow<WalletPayment?>
		// ```
		return KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
			AppDelegate.get().business.paymentsManager.lastCompletedPayment
		)
		.compactMap { $0 }
		.eraseToAnyPublisher()
	}
	
	func lastIncomingPaymentPublisher() -> AnyPublisher<Lightning_kmpIncomingPayment, Never> {
		
		// Transforming from Kotlin:
		// ```
		// lastCompletedPayment: StateFlow<WalletPayment?>
		// ```
		return KotlinCurrentValueSubject<Lightning_kmpWalletPayment, Lightning_kmpWalletPayment?>(
			AppDelegate.get().business.paymentsManager.lastCompletedPayment
		)
		.compactMap {
			return $0 as? Lightning_kmpIncomingPayment
		}
		.eraseToAnyPublisher()
  }
}

extension AppConfigurationManager {
	
	// Transforming from Kotlin:
	// ```
	// chainContext: StateFlow<WalletContext.V0.ChainContext?>
	// ```
	func chainContextPublisher() -> AnyPublisher<WalletContext.V0ChainContext, Never> {
		
		return KotlinCurrentValueSubject<WalletContext.V0ChainContext, WalletContext.V0ChainContext?>(
			AppDelegate.get().business.appConfigurationManager.chainContext
		)
		.compactMap { $0 }
		.eraseToAnyPublisher()
	}
}

extension Lightning_kmpIncomingPayment {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
	}
}

extension Lightning_kmpIncomingPayment.Received {
	
	var receivedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(receivedAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.StatusCompleted {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.Part {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.PartStatusSucceeded {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpOutgoingPayment.PartStatusFailed {
	
	var completedAtDate: Date {
		return Date(timeIntervalSince1970: (Double(completedAt) / Double(1_000)))
	}
}

extension Lightning_kmpPaymentRequest {
	
	var timestampDate: Date {
		return Date(timeIntervalSince1970: Double(timestampSeconds))
	}
}

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
