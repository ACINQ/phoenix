import Foundation
import PhoenixShared
import Combine
import CryptoKit

extension PhoenixBusiness {
	
	func getPeer() -> Lightning_kmpPeer? {
		self.peerManager.peerState.value_ as? Lightning_kmpPeer
	}
}

extension Lightning_kmpWalletPayment: Identifiable {
	
	var identifiable: String {
		// @see LightningExtensions.kt: `fun WalletPayment.id()`
		return self.id()
	}
}

extension WalletPaymentId: Identifiable {
	
	var identifiable: String {
		return self.identifier
	}
}

extension WalletPaymentOrderRow: Identifiable {
	
	var identifiable: String {
		return self.identifier
	}
}

extension WalletPaymentInfo {
	
	func paymentDescription(includingUserDescription: Bool = true) -> String? {
		
		let sanitize = { (input: String?) -> String? in
			
			if let trimmedInput = input?.trimmingCharacters(in: .whitespacesAndNewlines) {
				if trimmedInput.count > 0 {
					return trimmedInput
				}
			}
			
			return nil
		}
		
		if includingUserDescription {
			if let description = sanitize(metadata.userDescription) {
				return description
			}
		}
		if let description = sanitize(metadata.lnurl?.description_) {
			return description
		}
		
		if let incomingPayment = payment as? Lightning_kmpIncomingPayment {
			
			if let invoice = incomingPayment.origin.asInvoice() {
				return sanitize(invoice.paymentRequest.description_)
			} else if let _ = incomingPayment.origin.asKeySend() {
				return NSLocalizedString("Donation", comment: "Payment description for received KeySend")
			} else if let swapIn = incomingPayment.origin.asSwapIn() {
				return sanitize(swapIn.address)
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let normal = outgoingPayment.details.asNormal() {
				return sanitize(normal.paymentRequest.desc())
			} else if let _ = outgoingPayment.details.asKeySend() {
				return NSLocalizedString("Donation", comment: "Payment description for received KeySend")
			} else if let swapOut = outgoingPayment.details.asSwapOut() {
				return sanitize(swapOut.address)
			} else if let _ = outgoingPayment.details.asChannelClosing() {
				return NSLocalizedString("Channel closing", comment: "Payment description for channel closing")
			}
		}
		
		return nil
	}
}

extension WalletPaymentMetadata {
	
	static func empty() -> WalletPaymentMetadata {
		return WalletPaymentMetadata(
			lnurl: nil,
			userDescription: nil,
			userNotes: nil,
			modifiedAt: nil
		)
	}
}

extension PaymentsManager {
	
	func getCachedPayment(
		row: WalletPaymentOrderRow,
		options: WalletPaymentFetchOptions
	) -> WalletPaymentInfo? {
		
		return fetcher.getCachedPayment(row: row, options: options)
	}
	
	func getCachedStalePayment(
		row: WalletPaymentOrderRow,
		options: WalletPaymentFetchOptions
	) -> WalletPaymentInfo? {
		
		return fetcher.getCachedStalePayment(row: row, options: options)
	}
	
	func getPayment(
		row: WalletPaymentOrderRow,
		options: WalletPaymentFetchOptions,
		completion: @escaping (WalletPaymentInfo?) -> Void
	) -> Void {
		
		fetcher.getPayment(row: row, options: options) { (result: WalletPaymentInfo?, _: Error?) in
			
			completion(result)
		}
	}
}

struct FetchQueueBatchResult {
	let rowids: [Int64]
	let rowidMap: [Int64: WalletPaymentId]
	let rowMap: [WalletPaymentId : WalletPaymentInfo]
	let metadataMap: [WalletPaymentId : KotlinByteArray]
	let incomingStats: CloudKitDb.MetadataStats
	let outgoingStats: CloudKitDb.MetadataStats
	
	func uniquePaymentIds() -> Set<WalletPaymentId> {
		return Set<WalletPaymentId>(rowidMap.values)
	}
	
	func rowidsMatching(_ query: WalletPaymentId) -> [Int64] {
		var results = [Int64]()
		for (rowid, paymentRowId) in rowidMap {
			if paymentRowId == query {
				results.append(rowid)
			}
		}
		return results
	}
	
	static func empty() -> FetchQueueBatchResult {
		
		return FetchQueueBatchResult(
			rowids: [],
			rowidMap: [:],
			rowMap: [:],
			metadataMap: [:],
			incomingStats: CloudKitDb.MetadataStats(),
			outgoingStats: CloudKitDb.MetadataStats()
		)
	}
}

extension CloudKitDb.FetchQueueBatchResult {
	
	func convertToSwift() -> FetchQueueBatchResult {
		
		// We are experiencing crashes like this:
		//
		// for (rowid, paymentRowId) in batch.rowidMap {
		//      ^^^^^
		// Crash: Could not cast value of type '__NSCFNumber' to 'PhoenixSharedLong'.
		//
		// This appears to be some kind of bug in Kotlin.
		// So we're going to make a clean migration.
		// And we need to do so without swift-style enumeration in order to avoid crashing.
		
		var _rowids = [Int64]()
		var _rowidMap = [Int64: WalletPaymentId]()
		
		for i in 0 ..< self.rowids.count { // cannot enumerate self.rowidMap
			
			let value_kotlin = rowids[i]
			let value_swift = value_kotlin.int64Value
			
			_rowids.append(value_swift)
			if let paymentRowId = self.rowidMap[value_kotlin] {
				_rowidMap[value_swift] = paymentRowId
			}
		}
		
		return FetchQueueBatchResult(
			rowids: _rowids,
			rowidMap: _rowidMap,
			rowMap: self.rowMap,
			metadataMap: self.metadataMap,
			incomingStats: self.incomingStats,
			outgoingStats: self.outgoingStats
		)
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

extension Lightning_kmpIncomingPayment.ReceivedWith: Identifiable {
	
	var identifiable: Int {
		return self.hash
	}
}

extension Lightning_kmpOutgoingPayment {
	
	var createdAtDate: Date {
		return Date(timeIntervalSince1970: (Double(createdAt) / Double(1_000)))
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

extension Lightning_kmpOutgoingPayment.StatusCompleted {
	
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

extension Bitcoin_kmpByteVector32 {
	
	static func random() -> Bitcoin_kmpByteVector32 {
		
		let key = SymmetricKey(size: .bits256) // 256 / 8 = 32
		
		let data = key.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		
		return Bitcoin_kmpByteVector32(bytes: data.toKotlinByteArray())
	}
}

extension ConnectionsManager {
	
	var currentValue: Connections {
		return connections.value_ as! Connections
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

extension FiatCurrency {
	
	var shortName: String {
		return name.uppercased()
	}
	
	var longName: String { switch self {
		case .aed  : return NSLocalizedString("United Arab Emirates Dirham",         comment: "Currency name: AED")
		case .afn  : return NSLocalizedString("Afghan Afghani",                      comment: "Currency name: AFN")
		case .all  : return NSLocalizedString("Albanian Lek",                        comment: "Currency name: ALL")
		case .amd  : return NSLocalizedString("Armenian Dram",                       comment: "Currency name: AMD")
		case .ang  : return NSLocalizedString("Netherlands Antillean Guilder",       comment: "Currency name: ANG")
		case .aoa  : return NSLocalizedString("Angolan Kwanza",                      comment: "Currency name: AOA")
		case .ars  : return NSLocalizedString("Argentine Peso",                      comment: "Currency name: ARS")
		case .aud  : return NSLocalizedString("Australian Dollar",                   comment: "Currency name: AUD")
		case .awg  : return NSLocalizedString("Aruban Florin",                       comment: "Currency name: AWG")
		case .azn  : return NSLocalizedString("Azerbaijani Manat",                   comment: "Currency name: AZN")
		case .bam  : return NSLocalizedString("Bosnia-Herzegovina Convertible Mark", comment: "Currency name: BAM")
		case .bbd  : return NSLocalizedString("Barbadian Dollar",                    comment: "Currency name: BBD")
		case .bdt  : return NSLocalizedString("Bangladeshi Taka",                    comment: "Currency name: BDT")
		case .bgn  : return NSLocalizedString("Bulgarian Lev",                       comment: "Currency name: BGN")
		case .bhd  : return NSLocalizedString("Bahraini Dinar",                      comment: "Currency name: BHD")
		case .bif  : return NSLocalizedString("Burundian Franc",                     comment: "Currency name: BIF")
		case .bmd  : return NSLocalizedString("Bermudan Dollar",                     comment: "Currency name: BMD")
		case .bnd  : return NSLocalizedString("Brunei Dollar",                       comment: "Currency name: BND")
		case .bob  : return NSLocalizedString("Bolivian Boliviano",                  comment: "Currency name: BOB")
		case .brl  : return NSLocalizedString("Brazilian Real",                      comment: "Currency name: BRL")
		case .bsd  : return NSLocalizedString("Bahamian Dollar",                     comment: "Currency name: BSD")
		case .btn  : return NSLocalizedString("Bhutanese Ngultrum",                  comment: "Currency name: BTN")
		case .bwp  : return NSLocalizedString("Botswanan Pula",                      comment: "Currency name: BWP")
		case .bzd  : return NSLocalizedString("Belize Dollar",                       comment: "Currency name: BZD")
		case .cad  : return NSLocalizedString("Canadian Dollar",                     comment: "Currency name: CAD")
		case .cdf  : return NSLocalizedString("Congolese Franc",                     comment: "Currency name: CDF")
		case .chf  : return NSLocalizedString("Swiss Franc",                         comment: "Currency name: CHF")
		case .clp  : return NSLocalizedString("Chilean Peso",                        comment: "Currency name: CLP")
		case .cny  : return NSLocalizedString("Chinese Yuan",                        comment: "Currency name: CNY")
		case .cop  : return NSLocalizedString("Colombian Peso",                      comment: "Currency name: COP")
		case .crc  : return NSLocalizedString("Costa Rican Colón",                   comment: "Currency name: CRC")
		case .cup  : return NSLocalizedString("Cuban Peso",                          comment: "Currency name: CUP")
		case .cve  : return NSLocalizedString("Cape Verdean Escudo",                 comment: "Currency name: CVE")
		case .czk  : return NSLocalizedString("Czech Koruna",                        comment: "Currency name: CZK")
		case .djf  : return NSLocalizedString("Djiboutian Franc",                    comment: "Currency name: DJF")
		case .dkk  : return NSLocalizedString("Danish Krone",                        comment: "Currency name: DKK")
		case .dop  : return NSLocalizedString("Dominican Peso",                      comment: "Currency name: DOP")
		case .dzd  : return NSLocalizedString("Algerian Dinar",                      comment: "Currency name: DZD")
		case .eek  : return NSLocalizedString("Estonian Kroon",                      comment: "Currency name: EEK")
		case .egp  : return NSLocalizedString("Egyptian Pound",                      comment: "Currency name: EGP")
		case .ern  : return NSLocalizedString("Eritrean Nakfa",                      comment: "Currency name: ERN")
		case .etb  : return NSLocalizedString("Ethiopian Birr",                      comment: "Currency name: ETB")
		case .eur  : return NSLocalizedString("Euro",                                comment: "Currency name: EUR")
		case .fjd  : return NSLocalizedString("Fijian Dollar",                       comment: "Currency name: FJD")
		case .fkp  : return NSLocalizedString("Falkland Islands Pound",              comment: "Currency name: FKP")
		case .gbp  : return NSLocalizedString("Great British Pound",                 comment: "Currency name: GBP")
		case .gel  : return NSLocalizedString("Georgian Lari",                       comment: "Currency name: GEL")
		case .ghs  : return NSLocalizedString("Ghanaian Cedi",                       comment: "Currency name: GHS")
		case .gip  : return NSLocalizedString("Gibraltar Pound",                     comment: "Currency name: GIP")
		case .gmd  : return NSLocalizedString("Gambian Dalasi",                      comment: "Currency name: GMD")
		case .gnf  : return NSLocalizedString("Guinean Franc",                       comment: "Currency name: GNF")
		case .gtq  : return NSLocalizedString("Guatemalan Quetzal",                  comment: "Currency name: GTQ")
		case .gyd  : return NSLocalizedString("Guyanaese Dollar",                    comment: "Currency name: GYD")
		case .hkd  : return NSLocalizedString("Hong Kong Dollar",                    comment: "Currency name: HKD")
		case .hnl  : return NSLocalizedString("Honduran Lempira",                    comment: "Currency name: HNL")
		case .hrk  : return NSLocalizedString("Croatian Kuna",                       comment: "Currency name: HRK")
		case .htg  : return NSLocalizedString("Haitian Gourde",                      comment: "Currency name: HTG")
		case .huf  : return NSLocalizedString("Hungarian Forint",                    comment: "Currency name: HUF")
		case .idr  : return NSLocalizedString("Indonesian Rupiah",                   comment: "Currency name: IDR")
		case .ils  : return NSLocalizedString("Israeli New Sheqel",                  comment: "Currency name: ILS")
		case .inr  : return NSLocalizedString("Indian Rupee",                        comment: "Currency name: INR")
		case .iqd  : return NSLocalizedString("Iraqi Dinar",                         comment: "Currency name: IQD")
		case .irr  : return NSLocalizedString("Iranian Rial",                        comment: "Currency name: IRR")
		case .isk  : return NSLocalizedString("Icelandic Króna",                     comment: "Currency name: isk")
		case .jep  : return NSLocalizedString("Jersey Pound",                        comment: "Currency name: JEP")
		case .jmd  : return NSLocalizedString("Jamaican Dollar",                     comment: "Currency name: JMD")
		case .jod  : return NSLocalizedString("Jordanian Dinar",                     comment: "Currency name: JOD")
		case .jpy  : return NSLocalizedString("Japanese Yen",                        comment: "Currency name: JPY")
		case .kes  : return NSLocalizedString("Kenyan Shilling",                     comment: "Currency name: KES")
		case .kgs  : return NSLocalizedString("Kyrgystani Som",                      comment: "Currency name: KGS")
		case .khr  : return NSLocalizedString("Cambodian Riel",                      comment: "Currency name: KHR")
		case .kmf  : return NSLocalizedString("Comorian Franc",                      comment: "Currency name: KMF")
		case .kpw  : return NSLocalizedString("North Korean Won",                    comment: "Currency name: KPW")
		case .krw  : return NSLocalizedString("South Korean Won",                    comment: "Currency name: KRW")
		case .kwd  : return NSLocalizedString("Kuwaiti Dinar",                       comment: "Currency name: KWD")
		case .kyd  : return NSLocalizedString("Cayman Islands Dollar",               comment: "Currency name: KYD")
		case .kzt  : return NSLocalizedString("Kazakhstani Tenge",                   comment: "Currency name: KZT")
		case .lak  : return NSLocalizedString("Laotian Kip",                         comment: "Currency name: LAK")
		case .lbp  : return NSLocalizedString("Lebanese Pound",                      comment: "Currency name: LBP")
		case .lkr  : return NSLocalizedString("Sri Lankan Rupee",                    comment: "Currency name: LKR")
		case .lrd  : return NSLocalizedString("Liberian Dollar",                     comment: "Currency name: LRD")
		case .lsl  : return NSLocalizedString("Lesotho Loti",                        comment: "Currency name: LSL")
		case .lyd  : return NSLocalizedString("Libyan Dinar",                        comment: "Currency name: LYD")
		case .mad  : return NSLocalizedString("Moroccan Dirham",                     comment: "Currency name: MAD")
		case .mdl  : return NSLocalizedString("Moldovan Leu",                        comment: "Currency name: MDL")
		case .mga  : return NSLocalizedString("Malagasy Ariary",                     comment: "Currency name: MGA")
		case .mkd  : return NSLocalizedString("Macedonian Denar",                    comment: "Currency name: MKD")
		case .mmk  : return NSLocalizedString("Myanmar Kyat",                        comment: "Currency name: MMK")
		case .mnt  : return NSLocalizedString("Mongolian Tugrik",                    comment: "Currency name: MNT")
		case .mop  : return NSLocalizedString("Macanese Pataca",                     comment: "Currency name: MOP")
		case .mtl  : return NSLocalizedString("Maltese Lira",                        comment: "Currency name: MTL")
		case .mur  : return NSLocalizedString("Mauritian Rupee",                     comment: "Currency name: MUR")
		case .mvr  : return NSLocalizedString("Maldivian Rufiyaa",                   comment: "Currency name: MVR")
		case .mwk  : return NSLocalizedString("Malawian Kwacha",                     comment: "Currency name: MWK")
		case .mxn  : return NSLocalizedString("Mexican Peso",                        comment: "Currency name: MXN")
		case .myr  : return NSLocalizedString("Malaysian Ringgit",                   comment: "Currency name: MYR")
		case .mzn  : return NSLocalizedString("Mozambican Metical",                  comment: "Currency name: MZN")
		case .nad  : return NSLocalizedString("Namibian Dollar",                     comment: "Currency name: NAD")
		case .ngn  : return NSLocalizedString("Nigerian Naira",                      comment: "Currency name: NGN")
		case .nio  : return NSLocalizedString("Nicaraguan Córdoba",                  comment: "Currency name: NIO")
		case .nok  : return NSLocalizedString("Norwegian Krone",                     comment: "Currency name: NOK")
		case .npr  : return NSLocalizedString("Nepalese Rupee",                      comment: "Currency name: NPR")
		case .nzd  : return NSLocalizedString("New Zealand Dollar",                  comment: "Currency name: NZD")
		case .omr  : return NSLocalizedString("Omani Rial",                          comment: "Currency name: OMR")
		case .pab  : return NSLocalizedString("Panamanian Balboa",                   comment: "Currency name: PAB")
		case .pen  : return NSLocalizedString("Peruvian Nuevo Sol",                  comment: "Currency name: PEN")
		case .pgk  : return NSLocalizedString("Papua New Guinean Kina",              comment: "Currency name: PGK")
		case .php  : return NSLocalizedString("Philippine Peso",                     comment: "Currency name: PHP")
		case .pkr  : return NSLocalizedString("Pakistani Rupee",                     comment: "Currency name: PKR")
		case .pln  : return NSLocalizedString("Polish Zloty",                        comment: "Currency name: PLN")
		case .pyg  : return NSLocalizedString("Paraguayan Guarani",                  comment: "Currency name: PYG")
		case .qar  : return NSLocalizedString("Qatari Rial",                         comment: "Currency name: QAR")
		case .ron  : return NSLocalizedString("Romanian Leu",                        comment: "Currency name: RON")
		case .rsd  : return NSLocalizedString("Serbian Dinar",                       comment: "Currency name: RSD")
		case .rub  : return NSLocalizedString("Russian Ruble",                       comment: "Currency name: RUB")
		case .rwf  : return NSLocalizedString("Rwandan Franc",                       comment: "Currency name: RWF")
		case .sar  : return NSLocalizedString("Saudi Riyal",                         comment: "Currency name: SAR")
		case .sbd  : return NSLocalizedString("Solomon Islands Dollar",              comment: "Currency name: SBD")
		case .scr  : return NSLocalizedString("Seychellois Rupee",                   comment: "Currency name: SCR")
		case .sdg  : return NSLocalizedString("Sudanese Pound",                      comment: "Currency name: SDG")
		case .sek  : return NSLocalizedString("Swedish Krona",                       comment: "Currency name: SEK")
		case .sgd  : return NSLocalizedString("Singapore Dollar",                    comment: "Currency name: SGD")
		case .shp  : return NSLocalizedString("Saint Helena Pound",                  comment: "Currency name: SHP")
		case .sll  : return NSLocalizedString("Sierra Leonean Leone",                comment: "Currency name: SLL")
		case .sos  : return NSLocalizedString("Somali Shilling",                     comment: "Currency name: SOS")
		case .srd  : return NSLocalizedString("Surinamese Dollar",                   comment: "Currency name: SRD")
		case .syp  : return NSLocalizedString("Syrian Pound",                        comment: "Currency name: SYP")
		case .szl  : return NSLocalizedString("Swazi Lilangeni",                     comment: "Currency name: SZL")
		case .thb  : return NSLocalizedString("Thai Baht",                           comment: "Currency name: THB")
		case .tjs  : return NSLocalizedString("Tajikistani Somoni",                  comment: "Currency name: TJS")
		case .tmt  : return NSLocalizedString("Turkmenistani Manat",                 comment: "Currency name: TMT")
		case .tnd  : return NSLocalizedString("Tunisian Dinar",                      comment: "Currency name: TND")
		case .top  : return NSLocalizedString("Tongan Paʻanga",                      comment: "Currency name: TOP")
		case .try_ : return NSLocalizedString("Turkish Lira",                        comment: "Currency name: TRY")
		case .ttd  : return NSLocalizedString("Trinidad and Tobago Dollar",          comment: "Currency name: TTD")
		case .twd  : return NSLocalizedString("New Taiwan Dollar",                   comment: "Currency name: TWD")
		case .tzs  : return NSLocalizedString("Tanzanian Shilling",                  comment: "Currency name: TZS")
		case .uah  : return NSLocalizedString("Ukrainian Hryvnia",                   comment: "Currency name: UAH")
		case .ugx  : return NSLocalizedString("Ugandan Shilling",                    comment: "Currency name: UGX")
		case .usd  : return NSLocalizedString("United States Dollar",                comment: "Currency name: USD")
		case .uyu  : return NSLocalizedString("Uruguayan Peso",                      comment: "Currency name: UYU")
		case .uzs  : return NSLocalizedString("Uzbekistan Som",                      comment: "Currency name: UZS")
		case .vnd  : return NSLocalizedString("Vietnamese Dong",                     comment: "Currency name: VND")
		case .vuv  : return NSLocalizedString("Vanuatu Vatu",                        comment: "Currency name: VUV")
		case .wst  : return NSLocalizedString("Samoan Tala",                         comment: "Currency name: WST")
		case .xaf  : return NSLocalizedString("CFA Franc BEAC",                      comment: "Currency name: XAF")
		case .xcd  : return NSLocalizedString("East Caribbean Dollar",               comment: "Currency name: XCD")
		case .xof  : return NSLocalizedString("CFA Franc BCEAO",                     comment: "Currency name: XOF")
		case .xpf  : return NSLocalizedString("CFP Franc",                           comment: "Currency name: XPF")
		case .yer  : return NSLocalizedString("Yemeni Rial",                         comment: "Currency name: YER")
		case .zar  : return NSLocalizedString("South African Rand",                  comment: "Currency name: ZAR")
		case .zmw  : return NSLocalizedString("Zambian Kwacha",                      comment: "Currency name: ZMW")
		default    : return self.shortName
	}}
	
	var flag: String { switch self {
		case .aed  : return "🇦🇪" // United Arab Emirates Dirham
		case .afn  : return "🇦🇫" // Afghan Afghani
		case .all  : return "🇦🇱" // Albanian Lek
		case .amd  : return "🇦🇲" // Armenian Dram
		case .ang  : return "🇳🇱" // Netherlands Antillean Guilder
		case .aoa  : return "🇦🇴" // Angolan Kwanza
		case .ars  : return "🇦🇷" // Argentine Peso
		case .aud  : return "🇦🇺" // Australian Dollar
		case .awg  : return "🇦🇼" // Aruban Florin
		case .azn  : return "🇦🇿" // Azerbaijani Manat
		case .bam  : return "🇧🇦" // Bosnia-Herzegovina Convertible Mark
		case .bbd  : return "🇧🇧" // Barbadian Dollar
		case .bdt  : return "🇧🇩" // Bangladeshi Taka
		case .bgn  : return "🇧🇬" // Bulgarian Lev
		case .bhd  : return "🇧🇭" // Bahraini Dinar
		case .bif  : return "🇧🇮" // Burundian Franc
		case .bmd  : return "🇧🇲" // Bermudan Dollar
		case .bnd  : return "🇧🇳" // Brunei Dollar
		case .bob  : return "🇧🇴" // Bolivian Boliviano
		case .brl  : return "🇧🇷" // Brazilian Real
		case .bsd  : return "🇧🇸" // Bahamian Dollar
		case .btn  : return "🇧🇹" // Bhutanese Ngultrum
		case .bwp  : return "🇧🇼" // Botswanan Pula
		case .bzd  : return "🇧🇿" // Belize Dollar
		case .cad  : return "🇨🇦" // Canadian Dollar
		case .cdf  : return "🇨🇩" // Congolese Franc
		case .chf  : return "🇨🇭" // Swiss Franc
		case .clp  : return "🇨🇱" // Chilean Peso
		case .cny  : return "🇨🇳" // Chinese Yuan
		case .cop  : return "🇨🇴" // Colombian Peso
		case .crc  : return "🇨🇷" // Costa Rican Colón
		case .cup  : return "🇨🇺" // Cuban Peso
		case .cve  : return "🇨🇻" // Cape Verdean Escudo
		case .czk  : return "🇨🇿" // Czech Koruna
		case .djf  : return "🇩🇯" // Djiboutian Franc
		case .dkk  : return "🇩🇰" // Danish Krone
		case .dop  : return "🇩🇴" // Dominican Peso
		case .dzd  : return "🇩🇿" // Algerian Dinar
		case .eek  : return "🇪🇪" // Estonian Kroon
		case .egp  : return "🇪🇬" // Egyptian Pound
		case .ern  : return "🇪🇷" // Eritrean Nakfa
		case .etb  : return "🇪🇹" // Ethiopian Birr
		case .eur  : return "🇪🇺" // Euro
		case .fjd  : return "🇫🇯" // Fijian Dollar
		case .fkp  : return "🇫🇰" // Falkland Islands Pound
		case .gbp  : return "🇬🇧" // British Pound Sterling
		case .gel  : return "🇬🇪" // Georgian Lari
		case .ghs  : return "🇬🇭" // Ghanaian Cedi
		case .gip  : return "🇬🇮" // Gibraltar Pound
		case .gmd  : return "🇬🇲" // Gambian Dalasi
		case .gnf  : return "🇬🇳" // Guinean Franc
		case .gtq  : return "🇬🇹" // Guatemalan Quetzal
		case .gyd  : return "🇬🇾" // Guyanaese Dollar
		case .hkd  : return "🇭🇰" // Hong Kong Dollar
		case .hnl  : return "🇭🇳" // Honduran Lempira
		case .hrk  : return "🇭🇷" // Croatian Kuna
		case .htg  : return "🇭🇹" // Haitian Gourde
		case .huf  : return "🇭🇺" // Hungarian Forint
		case .idr  : return "🇮🇩" // Indonesian Rupiah
		case .ils  : return "🇮🇱" // Israeli New Sheqel
		case .inr  : return "🇮🇳" // Indian Rupee
		case .iqd  : return "🇮🇶" // Iraqi Dinar
		case .irr  : return "🇮🇷" // Iranian Rial
		case .isk  : return "🇮🇸" // Icelandic Króna
		case .jep  : return "🇯🇪" // Jersey Pound
		case .jmd  : return "🇯🇲" // Jamaican Dollar
		case .jod  : return "🇯🇴" // Jordanian Dinar
		case .jpy  : return "🇯🇵" // Japanese Yen
		case .kes  : return "🇰🇪" // Kenyan Shilling
		case .kgs  : return "🇰🇬" // Kyrgystani Som
		case .khr  : return "🇰🇭" // Cambodian Riel
		case .kmf  : return "🇰🇲" // Comorian Franc
		case .kpw  : return "🇰🇵" // North Korean Won
		case .krw  : return "🇰🇷" // South Korean Won
		case .kwd  : return "🇰🇼" // Kuwaiti Dinar
		case .kyd  : return "🇰🇾" // Cayman Islands Dollar
		case .kzt  : return "🇰🇿" // Kazakhstani Tenge
		case .lak  : return "🇱🇦" // Laotian Kip
		case .lbp  : return "🇱🇧" // Lebanese Pound
		case .lkr  : return "🇱🇰" // Sri Lankan Rupee
		case .lrd  : return "🇱🇷" // Liberian Dollar
		case .lsl  : return "🇱🇸" // Lesotho Loti
		case .lyd  : return "🇱🇾" // Libyan Dinar
		case .mad  : return "🇲🇦" // Moroccan Dirham
		case .mdl  : return "🇲🇩" // Moldovan Leu
		case .mga  : return "🇲🇬" // Malagasy Ariary
		case .mkd  : return "🇲🇰" // Macedonian Denar
		case .mmk  : return "🇲🇲" // Myanmar Kyat
		case .mnt  : return "🇲🇳" // Mongolian Tugrik
		case .mop  : return "🇲🇴" // Macanese Pataca
		case .mtl  : return "🇲🇹" // Maltese Lira
		case .mur  : return "🇲🇺" // Mauritian Rupee
		case .mvr  : return "🇲🇻" // Maldivian Rufiyaa
		case .mwk  : return "🇲🇼" // Malawian Kwacha
		case .mxn  : return "🇲🇽" // Mexican Peso
		case .myr  : return "🇲🇾" // Malaysian Ringgit
		case .mzn  : return "🇲🇿" // Mozambican Metical
		case .nad  : return "🇳🇦" // Namibian Dollar
		case .ngn  : return "🇳🇬" // Nigerian Naira
		case .nio  : return "🇳🇮" // Nicaraguan Córdoba
		case .nok  : return "🇳🇴" // Norwegian Krone
		case .npr  : return "🇳🇵" // Nepalese Rupee
		case .nzd  : return "🇳🇿" // New Zealand Dollar
		case .omr  : return "🇴🇲" // Omani Rial
		case .pab  : return "🇵🇦" // Panamanian Balboa
		case .pen  : return "🇵🇪" // Peruvian Nuevo Sol
		case .pgk  : return "🇵🇬" // Papua New Guinean Kina
		case .php  : return "🇵🇭" // Philippine Peso
		case .pkr  : return "🇵🇰" // Pakistani Rupee
		case .pln  : return "🇵🇱" // Polish Zloty
		case .pyg  : return "🇵🇾" // Paraguayan Guarani
		case .qar  : return "🇶🇦" // Qatari Rial
		case .ron  : return "🇷🇴" // Romanian Leu
		case .rsd  : return "🇷🇸" // Serbian Dinar
		case .rub  : return "🇷🇺" // Russian Ruble
		case .rwf  : return "🇷🇼" // Rwandan Franc
		case .sar  : return "🇸🇦" // Saudi Riyal
		case .sbd  : return "🇸🇧" // Solomon Islands Dollar
		case .scr  : return "🇸🇨" // Seychellois Rupee
		case .sdg  : return "🇸🇩" // Sudanese Pound
		case .sek  : return "🇸🇪" // Swedish Krona
		case .sgd  : return "🇸🇬" // Singapore Dollar
		case .shp  : return "🇸🇭" // Saint Helena Pound
		case .sll  : return "🇸🇱" // Sierra Leonean Leone
		case .sos  : return "🇸🇴" // Somali Shilling
		case .srd  : return "🇸🇷" // Surinamese Dollar
		case .syp  : return "🇸🇾" // Syrian Pound
		case .szl  : return "🇸🇿" // Swazi Lilangeni
		case .thb  : return "🇹🇭" // Thai Baht
		case .tjs  : return "🇹🇯" // Tajikistani Somoni
		case .tmt  : return "🇹🇲" // Turkmenistani Manat
		case .tnd  : return "🇹🇳" // Tunisian Dinar
		case .top  : return "🇹🇴" // Tongan Paʻanga
		case .try_ : return "🇹🇷" // Turkish Lira
		case .ttd  : return "🇹🇹" // Trinidad and Tobago Dollar
		case .twd  : return "🇹🇼" // New Taiwan Dollar
		case .tzs  : return "🇹🇿" // Tanzanian Shilling
		case .uah  : return "🇺🇦" // Ukrainian Hryvnia
		case .ugx  : return "🇺🇬" // Ugandan Shilling
		case .usd  : return "🇺🇸" // United States Dollar
		case .uyu  : return "🇺🇾" // Uruguayan Peso
		case .uzs  : return "🇺🇿" // Uzbekistan Som
		case .vnd  : return "🇻🇳" // Vietnamese Dong
		case .vuv  : return "🇻🇺" // Vanuatu Vatu
		case .wst  : return "🇼🇸" // Samoan Tala
		case .xaf  : return "🇨🇲" // CFA Franc BEAC        - multiple options, chose country with highest GDP
		case .xcd  : return "🇱🇨" // East Caribbean Dollar - multiple options, chose country with highest GDP
		case .xof  : return "🇨🇮" // CFA Franc BCEAO       - multiple options, chose country with highest GDP
		case .xpf  : return "🇳🇨" // CFP Franc             - multiple options, chose country with highest GDP
		case .yer  : return "🇾🇪" // Yemeni Rial
		case .zar  : return "🇿🇦" // South African Rand
		case .zmw  : return "🇿🇲" // Zambian Kwacha
		default    : return "🏳️"
	}}
	
	fileprivate struct _Key {
		static var matchingLocales = 0
	}
	
	func matchingLocales() -> [Locale] {
		executeOnce(storageKey: &_Key.matchingLocales) {
			
			var matchingLocales = [Locale]()
			for identifier in Locale.availableIdentifiers {
			
				let locale = Locale(identifier: identifier)
				if let currencyCode = locale.currencyCode,
					currencyCode.caseInsensitiveCompare(self.name) == .orderedSame
				{
					matchingLocales.append(locale)
				}
			}
			
			return matchingLocales
		}
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
