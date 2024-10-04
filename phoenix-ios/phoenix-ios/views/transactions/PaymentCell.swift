import SwiftUI
import PhoenixShared

fileprivate let filename = "PaymentCell"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct PaymentCell : View {
	
	static let fetchOptions = WalletPaymentFetchOptions.companion.Descriptions.plus(
		other: WalletPaymentFetchOptions.companion.OriginalFiat
	).plus(
		other: WalletPaymentFetchOptions.companion.Contact
	)
	
	private let paymentsManager = Biz.business.paymentsManager
	
	let row: WalletPaymentOrderRow
	let didAppearCallback: ((WalletPaymentOrderRow) -> Void)?
	let didDisappearCallback: ((WalletPaymentOrderRow) -> Void)?
	
	@State var fetched: WalletPaymentInfo?
	@State var fetchedIsStale: Bool
	
	@ScaledMetric var textScaling: CGFloat = 100
	
	@Environment(\.dynamicTypeSize) var dynamicTypeSize: DynamicTypeSize
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	@EnvironmentObject var currencyPrefs: CurrencyPrefs

	init(
		row: WalletPaymentOrderRow,
		didAppearCallback: ((WalletPaymentOrderRow) -> Void)?,
		didDisappearCallback: ((WalletPaymentOrderRow) -> Void)? = nil
	) {
		self.row = row
		self.didAppearCallback = didAppearCallback
		self.didDisappearCallback = didDisappearCallback
		
		var result = paymentsManager.fetcher.getCachedPayment(row: row, options: PaymentCell.fetchOptions)
		if let _ = result {
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: false)
		} else {
			
			result = paymentsManager.fetcher.getCachedStalePayment(row: row, options: PaymentCell.fetchOptions)
			
			self._fetched = State(initialValue: result)
			self._fetchedIsStale = State(initialValue: true)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		Group {
			// Accessibility Notes:
			// Every cell should use the same layout.
			// So it's preferred to make a layout decision based on device type & state.
			
			if dynamicTypeSize.isAccessibilitySize && deviceInfo.isIPhone {
				body_accessibility()
			} else {
				body_standard()
			}
		}
		.padding([.top, .bottom], 14)
		.padding([.leading, .trailing], 12)
		.onAppear {
			onAppear()
		}
		.onDisappear {
			onDisappear()
		}
	}
	
	@ViewBuilder
	func body_standard() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			paymentImage()

			VStack(alignment: HorizontalAlignment.leading) {
				Text(line1())
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.primaryForeground)

				Text(line2())
					.font(.caption)
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.secondary)
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(.leading, 12)
			.padding(.trailing, 6)
			
			paymentAmount()
			
		} // </HStack>
	}
	
	@ViewBuilder
	func body_accessibility() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			paymentImage()
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Text(line1())
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.primaryForeground)
				
				Text(line2())
					.font(.caption)
					.lineLimit(1)
					.truncationMode(.tail)
					.foregroundColor(.secondary)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer(minLength: 0)
					paymentAmount()
				}
			}
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(.leading, 12)
		}
	}
	
	@ViewBuilder
	func paymentImage() -> some View {
		
		Group {
			if let payment = fetched?.payment {
				switch payment.state() {
					case WalletPaymentState.successOnChain  : Image(systemName: "link.circle.fill")
					case WalletPaymentState.successOffChain : Image(systemName: "checkmark.circle.fill")
					case WalletPaymentState.pendingOnChain  : Image(systemName: "clock.fill")
					case WalletPaymentState.pendingOffChain : Image(systemName: "clock.fill")
					case WalletPaymentState.failure         : Image(systemName: "x.circle.fill")
				}
			} else {
				Image(systemName: "magnifyingglass.circle.fill")
			}
		}
		.font(.title3)
		.imageScale(.large)
		.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func paymentAmount() -> some View {
		
		let (amount, isFailure, isOutgoing) = paymentAmountInfo()
		if isLiquidityPaidInTheFuture() {
			
			Text(verbatim: "")
				.accessibilityHidden(true)
			
		} else if currencyPrefs.hideAmounts {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				// Do not display any indication as to whether payment in incoming or outgoing
				Text(verbatim: amount.digits)
					.foregroundColor(Color(UIColor.systemGray2))
					.accessibilityLabel("hidden amount")
			}
				
		} else {
		
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
				let color: Color = isFailure ? .secondary : (isOutgoing ? .appNegative : .appPositive)
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				
					Text(verbatim: isOutgoing ? "-" : "+")
						.foregroundColor(color)
						.padding(.trailing, 1)
					
					Text(verbatim: amount.digits)
						.foregroundColor(color)
				}
				.environment(\.layoutDirection, .leftToRight) // issue #237
				
				Text(verbatim: " ") // separate for RTL languages
					.font(.caption)
					.foregroundColor(.gray)
				Text_CurrencyName(currency: amount.currency, fontTextStyle: .caption)
					.foregroundColor(.gray)
			} // </HStack>
			.accessibilityElement()
			.accessibilityLabel("\(isOutgoing ? "-" : "+")\(amount.string)")
			
		} // </amount>
	}

	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func line1() -> String {

		if let fetched = fetched {
			return fetched.paymentDescription() ?? fetched.defaultPaymentDescription()
		} else {
			return ""
		}
	}
	
	func line2() -> String {

		guard let payment = fetched?.payment else {
			return ""
		}
		
		guard let completedAtDate = payment.completedAtDate else {
			if payment.isOnChain() {
				return String(
					localized: "waiting for confirmations",
					comment: "explanation for pending transaction"
				)
			} else {
				return String(
					localized: "pending",
					comment: "timestamp string for pending transaction"
				)
			}
		}
		
		let timestamp = stringForDate(completedAtDate)
		
		if let contact = fetched?.contact {
			if let payment = fetched?.payment, payment.isIncoming() {
				return String(localized: "\(timestamp) ∙ from \(contact.name)")
			} else {
				return String(localized: "\(timestamp) ∙ to \(contact.name)")
			}
			
		} else if
			let payment = fetched?.payment,
			let liquidity = payment as? Lightning_kmpInboundLiquidityOutgoingPayment
		{
			let amount = Utils.formatBitcoin(sat: liquidity.purchase.amount, bitcoinUnit: .sat)
			return "\(timestamp)  ∙  +\(amount.string)"
			
		} else {
			return timestamp
		}
	}
	
	func line2HasExtraInfo() -> Bool {
		
		if fetched?.contact != nil {
			// Also going to display contact name
			return true
		}
		
		if fetched?.payment is Lightning_kmpInboundLiquidityOutgoingPayment {
			// Also going to display liquidity amount
			return true
		}
		
		return false
	}
	
	func stringForDate(_ completedAtDate: Date) -> String {
		
		let calendar = Calendar.current
		let compsA = calendar.dateComponents([.year], from: completedAtDate)
		let compsB = calendar.dateComponents([.year], from: Date.now)
		
		let yearA = compsA.year ?? 0
		let yearB = compsB.year ?? 0
		
		let preferShortDate = (textScaling > 100) || line2HasExtraInfo()
		
		let formatter = DateFormatter()
		if yearA == yearB {

			if preferShortDate {
				formatter.setLocalizedDateFormatFromTemplate("MMMdjmma")  // ≈ dateStyle.medium - year
			} else {
				formatter.setLocalizedDateFormatFromTemplate("MMMMdjmma") // ≈ dateStyle.long - year
			}
		} else {
			
			if preferShortDate {
				formatter.dateStyle = .short
			} else {
				formatter.dateStyle = .long
			}
			formatter.timeStyle = .short
		}
		
		return formatter.string(from: completedAtDate)
	}
	
	func paymentAmountInfo() -> (FormattedAmount, Bool, Bool) {

		if let payment = fetched?.payment {

			let amount: FormattedAmount
			if currencyPrefs.hideAmounts {
				amount = Utils.hiddenAmount(currencyPrefs)
				
			} else if currencyPrefs.showOriginalFiatValue && currencyPrefs.currencyType == .fiat {
				
				if let originalExchangeRate = fetched?.metadata.originalFiat {
					amount = Utils.formatFiat(msat: payment.amount, exchangeRate: originalExchangeRate)
				} else {
					amount = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
				}
			} else {
				
				amount = Utils.format(currencyPrefs, msat: payment.amount)
			}

			let isFailure = payment.state() == WalletPaymentState.failure
			let isOutgoing = payment is Lightning_kmpOutgoingPayment

			return (amount, isFailure, isOutgoing)

		} else {
			
			let currency = currencyPrefs.currency
			let amount = FormattedAmount(amount: 0.0, currency: currency, digits: "", decimalSeparator: " ")

			let isFailure = false
			let isOutgoing = true

			return (amount, isFailure, isOutgoing)
		}
	}
	
	func isLiquidityPaidInTheFuture() -> Bool {
		
		if let payment = fetched?.payment,
		   let liquidity = payment as? Lightning_kmpInboundLiquidityOutgoingPayment
		{
			return liquidity.isPaidInTheFuture()
		} else {
			return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		
		if fetched == nil || fetchedIsStale {
			
			paymentsManager.fetcher.getPayment(
				row: row,
				options: PaymentCell.fetchOptions
			) { (result: WalletPaymentInfo?, _) in
				
				self.fetched = result
			}
		}
		
		if let didAppearCallback = didAppearCallback {
			didAppearCallback(row)
		}
	}
	
	func onDisappear() {
		
		if let didDisappearCallback = didDisappearCallback {
			didDisappearCallback(row)
		}
	}
}
