import Foundation
import PhoenixShared

struct CardRequest {
	let piccData: Data
	let cmac: Data
	let invoice: Lightning_kmpBolt12Invoice
	let amount: Lightning_kmpMilliSatoshi
	
	static func fromOnionMessage(_ msg: Lightning_kmp_coreCardPaymentRequestReceived) -> CardRequest? {
		
		var piccStr: String? = nil
		var cmacStr: String? = nil
		
		let comps = msg.cardParams.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "&")
		for comp in comps {
			
			let keyValue = comp.split(separator: "=")
			if keyValue.count == 2 {
				let key = keyValue[0].lowercased()
				let value = keyValue[1].lowercased()
				
				if key == "picc_data" || key == "picc" || key == "piccdata" {
					piccStr = value
					
				} else if key == "cmac" {
					cmacStr = value
				}
			}
		}
		
		var piccData: Data? = nil
		var cmacData: Data? = nil
		
		if let piccStr {
			piccData = Data(fromHex: piccStr)
		}
		if let cmacStr {
			cmacData = Data(fromHex: cmacStr)
		}
		
		if let piccData, let cmacData, let amount = msg.invoice.amount {
			return CardRequest(piccData: piccData, cmac: cmacData, invoice: msg.invoice, amount: amount)
		} else {
			return nil
		}
	}
	
	func toWithdrawRequest() -> WithdrawRequest {
		return WithdrawRequest(
			piccData: self.piccData,
			cmac: self.cmac,
			method: .bolt12Invoice(invoice: self.invoice),
			amount: self.amount
		)
	}
}
