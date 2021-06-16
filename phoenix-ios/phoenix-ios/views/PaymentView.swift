import SwiftUI
import PhoenixShared
import DYPopoverView
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "TransactionView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

fileprivate let explainFeesButtonViewId = "explainFeesButtonViewId"


struct PaymentView : View {

	let payment: PhoenixShared.Lightning_kmpWalletPayment
	
	// We need an explicit close operation here because:
	// - we're going to use a NavigationView
	// - we need to programmatically close the sheet from any layer in the navigation stack
	// - the general API to pop a view from the nav stack is `presentationMode.wrappedValue.dismiss()`
	// - the general API to dismiss a sheet is `presentationMode.wrappedValue.dismiss()`
	// - thus we cannot use the general API
	//
	let closeSheet: () -> Void
	
	var body: some View {
		
		NavigationView {
			
			SummaryView(payment: payment, closeSheet: closeSheet)
				.navigationBarTitle("", displayMode: .inline)
				.navigationBarHidden(true)
		}
		.edgesIgnoringSafeArea(.all)
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

fileprivate struct SummaryView: View {
	
	@State var payment: PhoenixShared.Lightning_kmpWalletPayment
	let closeSheet: () -> Void
	
	@State var explainFeesText: String = ""
	@State var explainFeesPopoverVisible = false
	@State var explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: 200, height:500)
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	init(payment: PhoenixShared.Lightning_kmpWalletPayment, closeSheet: @escaping () -> Void) {
	//	self.payment = payment
		// ^^^ This compiles on Xcode 12.5, but crashes on the device.
		// To be more specific, it seems to crash on _some_ devices, and only when running in Release mode.
		self._payment = State<PhoenixShared.Lightning_kmpWalletPayment>(initialValue: payment)
		
		self.closeSheet = closeSheet
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {

			self.main

			// Close button in upper right-hand corner
			VStack {
				HStack {
					Spacer()
					Button {
						closeSheet()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
					.padding()
				}
				Spacer()
			}

			// An invisible layer used to detect taps, and dismiss the DYPopoverView.
			if explainFeesPopoverVisible {
				Color.clear
					.contentShape(Rectangle()) // required: https://stackoverflow.com/a/60151771/43522
					.onTapGesture {
						explainFeesPopoverVisible = false
					}
			}
		}
		.popoverView( // DYPopoverView
			content: { ExplainFeesPopover(explanationText: $explainFeesText) },
			background: { Color.mutedBackground },
			isPresented: $explainFeesPopoverVisible,
			frame: $explainFeesPopoverFrame,
			anchorFrame: nil,
			popoverType: .popover,
			position: .top,
			viewId: explainFeesButtonViewId
		)
		.onPreferenceChange(ExplainFeesPopoverHeight.self) {
			if let height = $0 {
				explainFeesPopoverFrame = CGRect(x: 0, y: 0, width: explainFeesPopoverFrame.width, height: height)
			}
		}
		.onAppear { onAppear() }
	}
	
	@ViewBuilder
	var main: some View {
		
		VStack {
			switch payment.state() {
			case .success:
				Image("ic_payment_sent")
					.renderingMode(.template)
					.resizable()
					.frame(width: 100, height: 100)
					.aspectRatio(contentMode: .fit)
					.foregroundColor(Color.appPositive)
					.padding(.bottom, 16)
				VStack {
					Group {
						if payment is Lightning_kmpOutgoingPayment {
							Text("SENT")
						} else {
							Text("RECEIVED")
						}
					}
					.font(Font.title2.bold())
					.padding(.bottom, 2)
					Text(payment.timestamp().formatDateMS())
						.font(.subheadline)
						.foregroundColor(.secondary)
				}
				.padding(.bottom, 30)
				
			case .pending:
				Image("ic_payment_sending")
					.renderingMode(.template)
					.resizable()
					.foregroundColor(Color.borderColor)
					.frame(width: 100, height: 100)
					.padding(.bottom, 16)
				Text("PENDING")
					.font(Font.title2.bold())
					.padding(.bottom, 30)
				
			case .failure:
				Image(systemName: "xmark.circle")
					.renderingMode(.template)
					.resizable()
					.frame(width: 100, height: 100)
					.foregroundColor(.appNegative)
					.padding(.bottom, 16)
				VStack {
					Text("FAILED")
						.font(Font.title2.bold())
						.padding(.bottom, 2)
					
					Text("NO FUNDS HAVE BEEN SENT")
						.font(Font.title2.uppercaseSmallCaps())
						.padding(.bottom, 6)
					
					Text(payment.timestamp().formatDateMS())
						.font(Font.subheadline)
						.foregroundColor(.secondary)
					
				}
				.padding(.bottom, 30)
				
			default:
				EmptyView()
			}

			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				let amount = Utils.format(currencyPrefs, msat: payment.amountMsat(), hideMsats: false)

				if currencyPrefs.currencyType == .bitcoin &&
				   currencyPrefs.bitcoinUnit == .sat &&
				   amount.hasFractionDigits
				{
					// We're showing the value in satoshis, but the value contains a fractional
					// component representing the millisatoshis.
					// This can be a little confusing for those new to Lightning.
					// So we're going to downplay the millisatoshis visually.
					
					Text(verbatim: "\(amount.integerDigits).")
						.font(.largeTitle)
						.onTapGesture { toggleCurrencyType() }
					Text(amount.fractionDigits)
						.lineLimit(1)            // SwiftUI bugs
						.minimumScaleFactor(0.5) // Truncating text
						.font(.title)
						.foregroundColor(Color.secondary)
						.onTapGesture { toggleCurrencyType() }
						.padding(.trailing, 6)
					Text(amount.type)
						.font(.title3)
						.foregroundColor(Color.appAccent)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
					
				} else {
					
					Text(amount.digits)
						.font(.largeTitle)
						.onTapGesture { toggleCurrencyType() }
						.padding(.trailing, 6)
					Text(amount.type)
						.font(.title3)
						.foregroundColor(Color.appAccent)
						.padding(.bottom, 4)
						.onTapGesture { toggleCurrencyType() }
				}
			}
			.padding([.top, .leading, .trailing], 8)
			.padding(.bottom, 33)
			.background(
				VStack {
					Spacer()
					RoundedRectangle(cornerRadius: 10)
						.frame(width: 70, height: 6, alignment: /*@START_MENU_TOKEN@*/.center/*@END_MENU_TOKEN@*/)
						.foregroundColor(Color.appAccent)
				}
			)
			.padding(.bottom, 24)
			
			SummaryInfoGrid(
				payment: $payment,
				explainFeesPopoverVisible: $explainFeesPopoverVisible
			)
			
			NavigationLink(destination: DetailsView(payment: $payment, closeSheet: closeSheet)) {
				Text("Details")
			}
			.padding([.top, .bottom])
		}
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
	
	func explainFeesPopoverText() -> String {
		
		let feesInfo = payment.paymentFees(currencyPrefs: currencyPrefs)
		return feesInfo?.1 ?? ""
	}
	
	func onAppear() -> Void {
		log.trace("onAppear()")
		
		// Update text in explainFeesPopover
		explainFeesText = payment.paymentFees(currencyPrefs: currencyPrefs)?.1 ?? ""
		
		if let outgoingPayment = payment as? PhoenixShared.Lightning_kmpOutgoingPayment {
			
			// If this is an outgoingPayment, then we don't have the proper parts list.
			// That is, the outgoingPayment was fetched via listPayments(),
			// which gives us a fake parts list.
			//
			// Let's fetch the full outgoingPayment (with parts list),
			// so we can improve our fees description.
			
			let paymentId = outgoingPayment.component1()
			AppDelegate.get().business.paymentsManager.getOutgoingPayment(id: paymentId) {
				(fullOutgoingPayment: Lightning_kmpOutgoingPayment?, error: Error?) in
				
				if let fullOutgoingPayment = fullOutgoingPayment {
					payment = fullOutgoingPayment
					
					let feesInfo = fullOutgoingPayment.paymentFees(currencyPrefs: currencyPrefs)
					explainFeesText = feesInfo?.1 ?? ""
				}
			}
		}
	}
}

// See InfoGridView for architecture discussion.
//
fileprivate struct SummaryInfoGrid: InfoGridView {
	
	@Binding var payment: PhoenixShared.Lightning_kmpWalletPayment
	
	@Binding var explainFeesPopoverVisible: Bool
	
	@State private var calculatedKeyColumnWidth: CGFloat? = nil
	
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 200
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@Environment(\.openURL) var openURL
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(
			alignment : HorizontalAlignment.leading,
			spacing   : verticalSpacingBetweenRows
		) {
			
			// Splitting this up into separate ViewBuilders,
			// because the compiler will sometimes choke while processing this method.
			
			paymentDescriptionRow
			paymentTypeRow
			channelClosingRow
			paymentFeesRow
			timeElapsedRow
			paymentErrorRow
		}
		.padding([.leading, .trailing])
	}
	
	// Needed for InfoGridView protocol
	func setCalculatedKeyColumnWidth(_ value: CGFloat?) -> Void {
		calculatedKeyColumnWidth = value
	}
	func getCalculatedKeyColumnWidth() -> CGFloat? {
		return calculatedKeyColumnWidth
	}
	
	@ViewBuilder
	func keyColumn(_ str: String) -> some View {
		
		Text(str).foregroundColor(.secondary)
	}
	
	@ViewBuilder
	var paymentDescriptionRow: some View {
		
		if let pDescription = payment.paymentDescription() {
			
			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Desc", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				Text(pDescription.count > 0 ? pDescription : "No description")
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = pDescription
						}) {
							Text("Copy")
						}
					}
			}
		}
	}
	
	@ViewBuilder
	var paymentTypeRow: some View {
		
		if let pType = payment.paymentType() {
			
			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Type", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					Text(pType.0)
					+ Text(verbatim: " (\(pType.1))")
						.font(.footnote)
						.foregroundColor(.secondary)
					
					if let link = payment.paymentLink() {
						Button {
							openURL(link)
						} label: {
							Text("blockchain tx")
						}
					}
				}
			}
		}
	}
	
	@ViewBuilder
	var channelClosingRow: some View {
		
		if let pClosingInfo = payment.channelClosing() {
			
			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Output", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
					
					// Bitcoin address (copyable)
					Text(pClosingInfo.closingAddress)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = pClosingInfo.closingAddress
							}) {
								Text("Copy")
							}
						}
					
					if pClosingInfo.isSentToDefaultAddress {
						Text("(This is your address - derived from your seed. You alone possess your seed.)")
							.font(.footnote)
							.foregroundColor(.secondary)
							.padding(.top, 4)
					}
				}
			}
		}
	}
	
	@ViewBuilder
	var paymentFeesRow: some View {
		
		if let pFees = payment.paymentFees(currencyPrefs: currencyPrefs) {

			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Fees", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					Text(pFees.0.string) // pFees.0 => FormattedAmount
						.onTapGesture { toggleCurrencyType() }
					
					if pFees.1.count > 0 {
						
						Button {
							explainFeesPopoverVisible = true
						} label: {
							Image(systemName: "questionmark.circle")
								.renderingMode(.template)
								.foregroundColor(.secondary)
								.font(.body)
						}
						.anchorView(viewId: explainFeesButtonViewId)
						.padding(.leading, 4)
					}
				}
			}
		}
	}
	
	@ViewBuilder
	var timeElapsedRow: some View {
		
		if let pElapsed = payment.paymentTimeElapsed() {
			
			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Elapsed", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				if pElapsed < 1_000 {
					Text("\(pElapsed) milliseconds")
				} else {
					let seconds = pElapsed / 1_000
					let millis = pElapsed % 1_000
					
					Text("\(seconds).\(millis) seconds")
				}
			}
		}
	}
	
	@ViewBuilder
	var paymentErrorRow: some View {
		
		if let pError = payment.paymentFinalError() {
			
			InfoGridRow(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: self.keyColumnWidth) {
				
				keyColumn(NSLocalizedString("Error", comment: "Label in SummaryInfoGrid"))
				
			} valueColumn: {
				
				Text(pError)
			}
		}
	}
	
	func toggleCurrencyType() -> Void {
		currencyPrefs.toggleCurrencyType()
	}
}

fileprivate struct ExplainFeesPopover: View {
	
	@Binding var explanationText: String
	
	var body: some View {
		
		VStack {
			Text(explanationText)
				.padding()
				.background(GeometryReader { proxy in
					Color.clear.preference(
						key: ExplainFeesPopoverHeight.self,
						value: proxy.size.height
					)
				})
		}.frame(minHeight: 500)
		// ^^^ Why? ^^^
		//
		// We're trying to accomplish 2 things:
		// - allow the explanationText to be dynamically changed
		// - calculate the appropriate height for the text
		//
		// But there's a problem, which occurs like so:
		// - explainFeesPopoverFrame starts with hard-coded frame of (150, 500)
		// - SwiftUI performs layout of our body with inherited frame of (150, 500)
		// - We calculate appropriate height (X) for our text,
		//   and it gets reported via ExplainFeesPopoverHeight
		// - explainFeesPopoverFrame is resized to (150, X)
		// - explanationText is changed, triggering a view update
		// - SwiftUI performs layout of our body with previous frame of (150, X)
		// - Text cannot report appropriate height, as it's restricted to X
		//
		// So we force the height of our VStack to 500,
		// giving the Text room to size itself appropriately.
	}
}

fileprivate struct ExplainFeesPopoverHeight: PreferenceKey {
	static let defaultValue: CGFloat? = nil
	
	static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
		value = value ?? nextValue()
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

fileprivate struct DetailsView: View {
	
	@Binding var payment: Lightning_kmpWalletPayment
	let closeSheet: () -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.left")
						.imageScale(.medium)
				}
				Spacer()
				Button {
					closeSheet()
				} label: {
					Image(systemName: "xmark") // must match size of chevron.left above
						.imageScale(.medium)
				}
			}
			.font(.title2)
			.padding()
				
			ScrollView {
				DetailsInfoGrid(payment: $payment)
					.padding([.leading, .trailing])
			}
		}
		.navigationBarTitle(
			NSLocalizedString("Details", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.navigationBarHidden(true)
	}
}

fileprivate struct DetailsInfoGrid: InfoGridView {
	
	@Binding var payment: Lightning_kmpWalletPayment
	
	@State var calculatedKeyColumnWidth: CGFloat? = nil
	
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 140
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// Needed for InfoGridView protocol
	func setCalculatedKeyColumnWidth(_ value: CGFloat?) -> Void {
		calculatedKeyColumnWidth = value
	}
	func getCalculatedKeyColumnWidth() -> CGFloat? {
		return calculatedKeyColumnWidth
	}
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: verticalSpacingBetweenRows) {
			
			if let incomingPayment = payment as? Lightning_kmpIncomingPayment {
				
				rows_incomingPayment(incomingPayment)
				
			} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
				
				rows_outgoingPayment(outgoingPayment)
			}
		}
		.padding(.bottom)
	}
	
	@ViewBuilder
	func rows_incomingPayment(_ incomingPayment: Lightning_kmpIncomingPayment) -> some View {
		
		if let paymentRequest = incomingPayment.origin.asInvoice()?.paymentRequest {
			
			header(NSLocalizedString("Payment Request", comment: "Title in DetailsView_IncomingPayment"))
		
			paymentRequest_invoiceCreated(paymentRequest)
			paymentRequest_amountRequested(paymentRequest)
			paymentRequest_paymentHash(paymentRequest)
		}
		
		if let received = incomingPayment.received {

			header(NSLocalizedString("Payment Received", comment: "Title in DetailsView_IncomingPayment"))
			
			paymentReceived_receivedAt(received)
			paymentReceived_amountReceived(received)
			paymentReceived_via(received)
			paymentReceived_channelId(received)
		}
	}
	
	@ViewBuilder
	func rows_outgoingPayment(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		if let paymentRequest = outgoingPayment.details.asNormal()?.paymentRequest {
		
			header(NSLocalizedString("Payment Request", comment: "Title in DetailsView_IncomingPayment"))
		
			paymentRequest_invoiceCreated(paymentRequest)
			paymentRequest_amountRequested(paymentRequest)
			paymentRequest_paymentHash(paymentRequest)
		
		} else if let channelClosing = outgoingPayment.details.asChannelClosing() {
			
			header(NSLocalizedString("Channel Closing", comment: "Title in DetailsView_IncomingPayment"))
			
			channelClosing_channelId(channelClosing)
			if let onChain = outgoingPayment.status.asOnChain() {
				onChain_type(onChain) // this makes more sense in this section
			}
			channelClosing_btcAddress(channelClosing)
			channelClosing_addrType(channelClosing)
		}
		
		if let offChain = outgoingPayment.status.asOffChain() {
			
			header(NSLocalizedString("Payment Sent", comment: "Title in DetailsView_IncomingPayment"))
			
			offChain_completedAt(offChain)
			offChain_totalAmount(outgoingPayment)
			offChain_recipientPubkey(outgoingPayment)
		
		} else if let onChain = outgoingPayment.status.asOnChain() {
			
			header(NSLocalizedString("Closing Status", comment: "Title in DetailsView_IncomingPayment"))
			
			onChain_completedAt(onChain)
			onChain_claimed(onChain)
			onChain_btcTxids(onChain)
			
		} else if let failed = outgoingPayment.status.asFailed() {
			
			header(NSLocalizedString("Send Failed", comment: "Title in DetailsView_IncomingPayment"))
			
			failed_failedAt(failed)
			failed_reason(failed)
		}
		
		if outgoingPayment.parts.count > 0 && outgoingPayment.details.asChannelClosing() == nil {
			
			header(NSLocalizedString("Payment Parts", comment: "Title in DetailsView_IncomingPayment"))
			
			ForEach(outgoingPayment.parts.indices, id: \.self) { index in
				part_row(outgoingPayment.parts[index])
			}
		}
	}
	
	@ViewBuilder
	func header(_ title: String) -> some View {
		
		HStack {
			Spacer()
			Text(title)
				.lineLimit(1)
				.minimumScaleFactor(0.5)
				.font(.title3)
			Spacer()
		}
		.padding(.horizontal)
		.padding(.bottom, 12)
		.background(
			VStack {
				Spacer()
				RoundedRectangle(cornerRadius: 10)
					.frame(height: 1, alignment: .center)
					.foregroundColor(Color.appAccent)
			}
		)
		.padding(.top, 24)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func keyColumn(_ str: String) -> some View {
		
		Text(str.lowercased())
			.font(.subheadline)
			.fontWeight(.thin)
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func paymentRequest_invoiceCreated(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("invoice created", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: paymentRequest.timestampDate)
		}
	}
	
	@ViewBuilder
	func paymentRequest_amountRequested(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("amount requested", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			if let msat = paymentRequest.amount {
				commonValue_amount(msat: msat)
			} else {
				Text("Any amount")
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_paymentHash(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("payment hash", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let paymentHash = payment.paymentHashString()
			Text(paymentHash)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = paymentHash
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func paymentReceived_receivedAt(_ received: Lightning_kmpIncomingPayment.Received) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
					
			keyColumn(NSLocalizedString("received at", comment: "Label in DetailsView_IncomingPayment"))
					
		} valueColumn: {
					
			commonValue_date(date: received.receivedAtDate)
		}
	}
	
	@ViewBuilder
	func paymentReceived_amountReceived(_ received: Lightning_kmpIncomingPayment.Received) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("amount received", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			if let msat = received.amount {
				commonValue_amount(msat: msat)
			} else {
				Text("Any amount")
			}
		}
	}
	
	@ViewBuilder
	func paymentReceived_via(_ received: Lightning_kmpIncomingPayment.Received) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("via", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			if let _ = received.receivedWith.asLightningPayment() {
				Text("Lightning network")
				
			} else if let _ = received.receivedWith.asNewChannel() {
				Text("New Channel (auto-created)")
				
			} else {
				Text("")
			}
		}
	}
	
	@ViewBuilder
	func paymentReceived_channelId(_ received: Lightning_kmpIncomingPayment.Received) -> some View {
		
		if let newChannel = received.receivedWith.asNewChannel() {
			
			InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
				
				keyColumn(NSLocalizedString("channel id", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let str = newChannel.channelId?.toHex() ?? "pending"
				Text(str)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = str
						}) {
							Text("Copy")
						}
					}
			}
		}
	}
	
	@ViewBuilder
	func channelClosing_channelId(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("channel id", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let str = channelClosing.channelId.toHex()
			Text(str)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = str
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func channelClosing_btcAddress(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("bitcoin address", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let bitcoinAddr = channelClosing.closingAddress
			Text(bitcoinAddr)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = bitcoinAddr
					}) {
						Text("Copy")
					}
				}
		}
	}
	
	@ViewBuilder
	func channelClosing_addrType(_ channelClosing: Lightning_kmpOutgoingPayment.DetailsChannelClosing) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("address type", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				if channelClosing.isSentToDefaultAddress {
					Text("Phoenix generated")
					Text("(derived from your seed)")
				} else {
					Text("External")
					Text("(you provided this address)")
				}
			}
		}
	}
	
	@ViewBuilder
	func offChain_completedAt(_ offChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOffChain) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
					
			keyColumn(NSLocalizedString("sent at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: offChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func offChain_totalAmount(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("total amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amount(msat: outgoingPayment.recipientAmount)
		}
	}
	
	@ViewBuilder
	func offChain_recipientPubkey(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("recipient pubkey", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(outgoingPayment.recipient.value.toHex())
		}
	}
	
	@ViewBuilder
	func onChain_type(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("type", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			switch onChain.closingType {
				case Lightning_kmpChannelClosingType.local   : Text(verbatim: "Local")
				case Lightning_kmpChannelClosingType.mutual  : Text(verbatim: "Mutual")
				case Lightning_kmpChannelClosingType.remote  : Text(verbatim: "Remote")
				case Lightning_kmpChannelClosingType.revoked : Text(verbatim: "Revoked")
				case Lightning_kmpChannelClosingType.other   : Text(verbatim: "Other")
				default                                      : Text(verbatim: "?")
			}
		}
	}
	
	@ViewBuilder
	func onChain_completedAt(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("completed at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: onChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func onChain_claimed(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("claimed amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let (display_sat, display_fiat) = displayAmounts(sat: onChain.claimed)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				Text(display_sat.string)
				if let display_fiat = display_fiat {
					Text(display_fiat.string)
				}
			}
		}
	}
	
	@ViewBuilder
	func onChain_btcTxids(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("bitcoin txids", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				ForEach(onChain.txids.indices, id: \.self) { index in
					
					let txid = onChain.txids[index].toHex()
					Text(txid)
						.contextMenu {
							Button(action: {
								UIPasteboard.general.string = txid
							}) {
								Text("Copy")
							}
						}
				}
			}
		}
	}
	
	@ViewBuilder
	func failed_failedAt(_ failed: Lightning_kmpOutgoingPayment.StatusCompletedFailed) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("timestamp", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: failed.completedAtDate)
		}
	}
	
	@ViewBuilder
	func failed_reason(_ failed: Lightning_kmpOutgoingPayment.StatusCompletedFailed) -> some View {
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			keyColumn(NSLocalizedString("reason", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(failed.reason.description)
		}
	}
	
	@ViewBuilder
	func part_row(_ part: Lightning_kmpOutgoingPayment.Part) -> some View {
		
		let imgSize: CGFloat = 20
		
		InfoGridRowWrapper(hSpacing: horizontalSpacingBetweenColumns, keyColumnWidth: keyColumnWidth) {
			
			if let part_succeeded = part.status as? Lightning_kmpOutgoingPayment.PartStatusSucceeded {
				keyColumn(shortDisplayTime(date: part_succeeded.completedAtDate))
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.PartStatusFailed {
				keyColumn(shortDisplayTime(date: part_failed.completedAtDate))
				
			} else {
				keyColumn(shortDisplayTime(date: part.createdAtDate))
			}
			
		} valueColumn: {
		
			if let _ = part.status as? Lightning_kmpOutgoingPayment.PartStatusSucceeded {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image("ic_payment_sent")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(Color.appPositive)
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, hideMsats: false)
						Text(formatted.string)
					}
				}
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.PartStatusFailed {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "xmark.circle")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(.appNegative)
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, hideMsats: false)
						Text(formatted.string)
					}
					
					let code = part_failed.remoteFailureCode?.description ?? "local"
					Text(verbatim: "\(code): \(part_failed.details)")
				}
				
			} else {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image("ic_payment_sending")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(Color.borderColor)
						
						Text("pending")
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func commonValue_date(date: Date) -> some View {
		
		let (dayMonthYear, timeOfDay) = displayTimes(date: date)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			Text(dayMonthYear)
			Text(timeOfDay)
		}
	}
	
	@ViewBuilder
	func commonValue_amount(msat: Lightning_kmpMilliSatoshi) -> some View {
		
		let (display_msat, display_fiat) = displayAmounts(msat: msat)
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			Text(display_msat.string)
			if let display_fiat = display_fiat {
				Text(verbatim: "≈ \(display_fiat.string)")
			}
		}
	}
	
	func displayTimes(date: Date) -> (String, String) {
		
		let df = DateFormatter()
		df.dateStyle = .long
		df.timeStyle = .none
		let dayMonthYear = df.string(from: date)
		
		let tf = DateFormatter()
		tf.dateStyle = .none
		tf.timeStyle = .long
		let timeOfDay = tf.string(from: date)
		
		return (dayMonthYear, timeOfDay)
	}
	
	func shortDisplayTime(date: Date) -> String {
		
		let formatter = DateFormatter()
		formatter.dateStyle = .none
		formatter.timeStyle = .medium
		
		return formatter.string(from: date)
	}
	
	func displayAmounts(msat: Lightning_kmpMilliSatoshi) -> (FormattedAmount, FormattedAmount?) {
		
		let display_msat = Utils.formatBitcoin(msat: msat, bitcoinUnit: .sat, hideMsats: false)
		var display_fiat: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			display_fiat = Utils.formatFiat(msat: msat, exchangeRate: fiatExchangeRate)
		}
		
		return (display_msat, display_fiat)
	}
	
	func displayAmounts(sat: Bitcoin_kmpSatoshi) -> (FormattedAmount, FormattedAmount?) {
		
		let display_sat = Utils.formatBitcoin(sat: sat, bitcoinUnit: .sat)
		var display_fiat: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			display_fiat = Utils.formatFiat(sat: sat, exchangeRate: fiatExchangeRate)
		}
		
		return (display_sat, display_fiat)
	}
	
	struct InfoGridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
		
		let hSpacing: CGFloat
		let keyColumnWidth: CGFloat
		let keyColumn: KeyColumn
		let valueColumn: ValueColumn
		
		init(
			hSpacing: CGFloat,
			keyColumnWidth: CGFloat,
			@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
			@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
		) {
			self.hSpacing = hSpacing
			self.keyColumnWidth = keyColumnWidth
			self.keyColumn = keyColumnBuilder()
			self.valueColumn = valueColumnBuilder()
		}
		
		var body: some View {
			
			InfoGridRow(hSpacing: hSpacing, keyColumnWidth: keyColumnWidth) {
				
				keyColumn
				
			} valueColumn: {
				
				valueColumn.font(.callout)
			}
		}
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

extension Lightning_kmpWalletPayment {
	
	fileprivate func paymentDescription() -> String? {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			if let invoice = incomingPayment.origin.asInvoice() {
				return invoice.paymentRequest.desc()
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let normal = outgoingPayment.details.asNormal() {
				return normal.paymentRequest.desc()
			}
		}
		
		return nil
	}
	
	fileprivate func paymentType() -> (String, String)? {
		
		// Will be displayed in the UI as:
		//
		// Type : value (explanation)
		//
		// where return value is: (value, explanation)
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asSwapIn() {
				let val = NSLocalizedString("Swap-In", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 1 -> 2", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = incomingPayment.origin.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.details.asSwapOut() {
				let val = NSLocalizedString("Swap-Out", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = outgoingPayment.details.asKeySend() {
				let val = NSLocalizedString("KeySend", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("non-invoice payment", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = outgoingPayment.details.asChannelClosing() {
				let val = NSLocalizedString("Channel Closing", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
		}
		
		return nil
	}
	
	fileprivate func paymentLink() -> URL? {
		
		var address: String? = nil
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
		
			if let swapIn = incomingPayment.origin.asSwapIn() {
				address = swapIn.address
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
		
			if let swapOut = outgoingPayment.details.asSwapOut() {
				address = swapOut.address
			}
		}
		
		if let address = address {
			let str: String
			if AppDelegate.get().business.chain.isTestnet() {
				str = "https://mempool.space/testnet/address/\(address)"
			} else {
				str = "https://mempool.space/address/\(address)"
			}
			return URL(string: str)
		}
		
		return nil
	}
	
	fileprivate func channelClosing() -> Lightning_kmpOutgoingPayment.DetailsChannelClosing? {
		
		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			if let result = outgoingPayment.details.asChannelClosing() {
				return result
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFees(currencyPrefs: CurrencyPrefs) -> (FormattedAmount, String)? {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
		
			// An incomingPayment may have fees if a new channel was automatically opened
			if let received = incomingPayment.received {
				
				let msat = received.receivedWith.fees.msat
				if msat > 0 {
					
					let formattedAmt = Utils.format(currencyPrefs, msat: msat, hideMsats: false)
					
					let exp = NSLocalizedString(
						"""
						In order to receive this payment, a new payment channel was opened. \
						This is not always required.
						""",
						comment: "Fees explanation"
					)
					
					return (formattedAmt, exp)
				}
				else {
					// I think it's nice to see "Fees: 0 sat" :)
					
					let formattedAmt = Utils.format(currencyPrefs, msat: 0, hideMsats: true)
					let exp = ""
					
					return (formattedAmt, exp)
				}
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment.status.asFailed() {
				
				// no fees for failed payments
				return nil
				
			} else if let onChain = outgoingPayment.status.asOnChain() {
				
				// for on-chain payments, the fees are extracted from the mined transaction(s)
				
				let channelDrain: Lightning_kmpMilliSatoshi = outgoingPayment.recipientAmount
				let claimed = Lightning_kmpMilliSatoshi(sat: onChain.claimed)
				let fees = channelDrain.minus(other: claimed)
				let formattedAmt = Utils.format(currencyPrefs, msat: fees, hideMsats: false)
				
				let txCount = onChain.component1().count
				let exp: String
				if txCount == 1 {
					exp = NSLocalizedString(
						"Bitcoin network fees paid for on-chain transaction. Payment required 1 transaction.",
						comment: "Fees explanation"
					)
				} else {
					exp = NSLocalizedString(
						"Bitcoin network fees paid for on-chain transactions. Payment required \(txCount) transactions.",
						comment: "Fees explanation"
					)
				}
				
				return (formattedAmt, exp)
				
			} else if let _ = outgoingPayment.status.asOffChain() {
				
				let msat = outgoingPayment.fees.msat
				let formattedAmt = Utils.format(currencyPrefs, msat: msat, hideMsats: false)
				
				var parts = 0
				var hops = 0
				for part in outgoingPayment.parts {
					
					parts += 1
					hops = part.route.count
				}
				
				let exp: String
				if parts == 1 {
					if hops == 1 {
						exp = NSLocalizedString(
							"Lightning fees for routing the payment. Payment required 1 hop.",
							comment: "Fees explanation"
						)
					} else {
						exp = String(format: NSLocalizedString(
							"Lightning fees for routing the payment. Payment required %d hops.",
							comment: "Fees explanation"),
							hops
						)
					}
					
				} else {
					exp = String(format: NSLocalizedString(
						"Lightning fees for routing the payment. Payment was divided into %d parts, using %d hops.",
						comment: "Fees explanation"),
						parts, hops
					)
				}
				
				return (formattedAmt, exp)
			}
		}
		
		return nil
	}
	
	/// If the OutgoingPayment succeeded or failed, reports the total elapsed time.
	/// The return value is in number of milliseconds.
	///
	fileprivate func paymentTimeElapsed() -> Int64? {

		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			let started = self.timestamp()
			var finished: Int64? = nil
			
			if let failed = outgoingPayment.status.asFailed() {
				finished = failed.completedAt
				
			} else if let succeeded = outgoingPayment.status.asSucceeded() {
				finished = succeeded.completedAt
			}
			
			if let finished = finished, finished > started {
				return finished - started
			}
		}
		
		return nil
	}
	
	fileprivate func paymentFinalError() -> String? {

		if let outgoingPayment = self as? Lightning_kmpOutgoingPayment {
			
			if let failed = outgoingPayment.status.asFailed() {
				
				return failed.reason.description
			}
		}
		
		return nil
	}
}

// --------------------------------------------------
// MARK:-
// --------------------------------------------------

class PaymentView_Previews : PreviewProvider {
	
	static var previews: some View {
		let mock = PhoenixShared.Mock()
		
		PaymentView(payment: mock.outgoingPending(), closeSheet: {})
			.preferredColorScheme(.light)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.outgoingSuccessful(), closeSheet: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.outgoingFailed(), closeSheet: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())

		PaymentView(payment: mock.incomingPaymentReceived(), closeSheet: {})
			.preferredColorScheme(.dark)
			.environmentObject(CurrencyPrefs.mockEUR())
	}
}
