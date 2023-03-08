import UserNotifications
import PhoenixShared


extension UNMutableNotificationContent {
	
	func fillForReceivedPayments(
		payments: [WalletPaymentInfo],
		bitcoinUnit: BitcoinUnit,
		exchangeRate: ExchangeRate.BitcoinPriceRate?
	) {
		
		var msat: Int64 = 0
		for paymentInfo in payments {
			msat += paymentInfo.payment.amount.msat
		}
		
		let bitcoinAmt = Utils.formatBitcoin(msat: msat, bitcoinUnit: bitcoinUnit)
		
		var fiatAmt: FormattedAmount? = nil
		if let exchangeRate {
			fiatAmt = Utils.formatFiat(msat: msat, exchangeRate: exchangeRate)
		}
		
		var amount = bitcoinAmt.string
		if let fiatAmt {
			amount += " (≈\(fiatAmt.string))"
		}
		
		if payments.count == 1 {
			self.title = NSLocalizedString("Received payment", comment: "Push notification title")
			
			if !GroupPrefs.shared.discreetNotifications {
				let payment = payments.first!
				if let desc = payment.paymentDescription(), desc.count > 0 {
					self.body = "\(amount): \(desc)"
				} else {
					self.body = amount
				}
			}
			
		} else {
			self.title = NSLocalizedString("Received multiple payments", comment: "Push notification title")
			
			if !GroupPrefs.shared.discreetNotifications {
				self.body = amount
			}
		}
		
		// The user can independently enable/disable:
		// - alerts
		// - badges
		// So we may only be able to badge the app icon, and that's it.
		
		GroupPrefs.shared.badgeCount += payments.count
		self.badge = NSNumber(value: GroupPrefs.shared.badgeCount)
	}
}
