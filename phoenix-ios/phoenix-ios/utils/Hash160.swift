import Foundation
import PhoenixShared

enum Hash160Error: Error, CustomStringConvertible {
	case nodeIdNotValidHex
	case nodeIdInvalidLength
	case nodeIdNotValidPublicKey
	
	var description: String {
		switch self {
		case .nodeIdNotValidHex:
			"nodeId is not valid hexadecimal"
		case .nodeIdInvalidLength:
			"nodeId.count != 33"
		case .nodeIdNotValidPublicKey:
			"nodeId is not valid publicKey"
		}
	}
}

func hash160(nodeId: String) -> Result<String, Hash160Error> {
	
	guard let data = Data(fromHex: nodeId) else {
		return .failure(.nodeIdNotValidHex)
	}
	guard data.count == 33 else {
		return .failure(.nodeIdInvalidLength)
	}
	
	let pubKey = Bitcoin_kmpPublicKey(data: data.toKotlinByteArray())
	guard pubKey.isValid() else {
		return .failure(.nodeIdNotValidPublicKey)
	}
	
	let result = pubKey.hash160().toSwiftData().toHex(.lowerCase)
	return .success(result)
}
