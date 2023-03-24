import Foundation
import PhoenixShared

struct MsatRange {
	let min: Lightning_kmpMilliSatoshi
	let max: Lightning_kmpMilliSatoshi
	
	init(min: Lightning_kmpMilliSatoshi, max: Lightning_kmpMilliSatoshi) {
		self.min = min
		self.max = max
	}
	
	init(min: Int64, max: Int64) {
		self.min = Lightning_kmpMilliSatoshi(msat: min)
		self.max = Lightning_kmpMilliSatoshi(msat: max)
	}
	
	func contains(msat: Lightning_kmpMilliSatoshi) -> Bool {
		return contains(msat: msat.msat)
	}
	
	func contains(msat: Int64) -> Bool {
		return msat >= min.msat && msat <= max.msat
	}
}

enum FlowType {
	case pay(range: MsatRange)
	case withdraw(range: MsatRange)
	
	var range: MsatRange {
		switch self {
		case .pay(let range):
			return range
		case .withdraw(let range):
			return range
		}
	}
}
