import Foundation
import PhoenixShared

struct CurrencyAmount: Equatable, Hashable {
	let currency: Currency
	let amount: Double
	
	func toSpendingLimit() -> SpendingLimit {
		switch currency {
		case .bitcoin(let bitcoinUnit):
			return SpendingLimit(currency: bitcoinUnit as CurrencyUnit, amount: amount)
		case .fiat(let fiatCurrency):
			return SpendingLimit(currency: fiatCurrency as CurrencyUnit, amount: amount)
		}
	}
}

extension SpendingLimit {
	func toCurrencyAmount() -> CurrencyAmount {
		return CurrencyAmount(
			currency: Currency.fromKotlin(self.currency),
			amount: self.amount
		)
	}
}
