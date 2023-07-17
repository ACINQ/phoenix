import Foundation
import PhoenixShared


enum MinerFeePriority {
	case none
	case low
	case medium
	case high
}

struct MempoolRecommendedResponse: Equatable, Codable {
	let fastestFee: Double
	let halfHourFee: Double
	let hourFee: Double
	let economyFee: Double
	let minimumFee: Double
	let timestamp: Date = Date.now
	
	func feeForPriority(_ priority: MinerFeePriority) -> Double {
		switch priority {
			case .none   : return economyFee
			case .low    : return hourFee
			case .medium : return halfHourFee
			case .high   : return fastestFee
		}
	}
	
	enum FetchError: Error {
		case network(underlying: Error)
		case decoding(underlying: Error)
	}
	
	@MainActor
	static func fetch() async -> Result<MempoolRecommendedResponse, FetchError> {
		
		let url = URL(string: "https://mempool.space/api/v1/fees/recommended")!
		let request = URLRequest(url: url)
		
		let data: Data
		do {
			(data, _) = try await URLSession.shared.data(for: request)
		} catch {
			return .failure(.network(underlying: error))
		}
		
		let response: MempoolRecommendedResponse
		do {
			response = try JSONDecoder().decode(MempoolRecommendedResponse.self, from: data)
		} catch {
			return .failure(.decoding(underlying: error))
		}
		
		return .success(response)
	}
	
	func toKotlin() -> MempoolFeerate {
		
		return MempoolFeerate(
			fastest: Lightning_kmpFeeratePerByte(feerate: Bitcoin_kmpSatoshi(sat: Int64(fastestFee))),
			halfHour: Lightning_kmpFeeratePerByte(feerate: Bitcoin_kmpSatoshi(sat: Int64(halfHourFee))),
			hour: Lightning_kmpFeeratePerByte(feerate: Bitcoin_kmpSatoshi(sat: Int64(hourFee))),
			economy: Lightning_kmpFeeratePerByte(feerate: Bitcoin_kmpSatoshi(sat: Int64(economyFee))),
			minimum: Lightning_kmpFeeratePerByte(feerate: Bitcoin_kmpSatoshi(sat: Int64(minimumFee))),
			timestamp: timestamp.toMilliseconds()
		)
	}
	
	func swapEstimationFee(hasNoChannels: Bool) -> Bitcoin_kmpSatoshi {
		
		return self.toKotlin().swapEstimationFee(hasNoChannels: hasNoChannels)
	}
}
