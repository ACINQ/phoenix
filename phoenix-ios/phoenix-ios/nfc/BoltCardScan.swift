import Foundation
import PhoenixShared
import CoreNFC

fileprivate let filename = "BoltCardScan"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

class BoltCardScan {
	
	static func parse(_ message: NFCNDEFMessage) -> Card? {
		
		var v1Results: [V1] = []
		var v2Results: [V2] = []
		
		message.records.forEach { payload in
			if let uri = payload.wellKnownTypeURIPayload() {
				log.debug("found uri = \(uri)")
				v1Results.append(V1(url: uri))
				
			} else if let text = payload.wellKnownTypeTextPayload().0 {
				log.debug("found text = \(text)")
				if let v2 = V2.parse(text) {
					v2Results.append(v2)
				}
			}
		}
		
		if let v2 = v2Results.first {
			return Card.v2(v2: v2)
		} else if let hybrid = v1Results.first(where: { $0.v2 != nil }) {
			return Card.v1(v1: hybrid)
		} else if let v1 = v1Results.first {
			return Card.v1(v1: v1)
		} else {
			return nil
		}
	}
	
	enum Card {
		case v1(v1: V1)
		case v2(v2: V2)
	}
	
	struct V1 {
		let url: URL
		
		var v2: V2? {
			
			guard var comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
				return nil
			}
			var queryItems = comps.queryItems ?? []
				
			let v2Items = queryItems.filter { $0.name.lowercased() == "v2" && $0.value != nil }
			guard !v2Items.isEmpty else {
				return nil
			}
			
			queryItems.removeAll(where: { $0.name.lowercased() == "v2" })
			comps.queryItems = queryItems
			let cardParams = comps.percentEncodedQuery ?? ""
			
			let v2List: [V2] = v2Items.compactMap { V2.parse($0.value ?? "", cardParams) }
			return v2List.first
		}
	}

	struct V2 {
		let baseText: String
		let parametersText: String
		
		let base: Format
		let parameters: [URLQueryItem]
		
		var text: String {
			return "\(baseText)?\(parametersText)"
		}
		
		enum Format {
			case offer(offer: Lightning_kmpOfferTypesOffer)
			case address(address: String)
		}
		
		fileprivate static func parse(_ text: String) -> V2? {
			
			let comps = text.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
			if comps.count == 2 {
				return parse(String(comps[0]), String(comps[1]))
			} else {
				return nil
			}
		}
		
		fileprivate static func parse(_ baseText: String, _ parametersText: String) -> V2? {
			
			var base: Format? = nil
			if baseText.starts(with: "lno") {
				let result: Bitcoin_kmpTry<Lightning_kmp_coreOfferTypesOffer> =
					Lightning_kmpOfferTypesOffer.companion.decode(s: baseText)
				
				if result.isSuccess {
					let offer: Lightning_kmpOfferTypesOffer = result.get()!
					base = Format.offer(offer: offer)
				}
				
			} else if baseText.starts(with: "₿") {
				
				let addr = baseText.substring(location: 1)
				if addr.isValidEmailAddress() {
					base = Format.address(address: addr)
				}
			}
			
			if let base {
				
				var parameters: [URLQueryItem] = []
				if let url = URL(string: "https://www.apple.com?\(parametersText)"),
					let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
				{
					parameters = components.queryItems ?? []
				}
				
				return V2(
					baseText: baseText,
					parametersText: parametersText,
					base: base,
					parameters: parameters
				)
			} else {
				return nil
			}
		}
	}
}
