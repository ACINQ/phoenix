import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "DetailsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct DetailsView: View {
	
	@Binding var paymentInfo: WalletPaymentInfo
	let closeSheet: () -> Void
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.backward")
						.imageScale(.medium)
				}
				Spacer()
				Button {
					closeSheet()
				} label: {
					Image(systemName: "xmark") // must match size of chevron.backward above
						.imageScale(.medium)
				}
			}
			.font(.title2)
			.padding()
				
			ScrollView {
				DetailsInfoGrid(paymentInfo: $paymentInfo)
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
	
	@Binding var paymentInfo: WalletPaymentInfo
	
	// <InfoGridView Protocol>
	@State var keyColumnWidths: [InfoGridRow_KeyColumn_Width] = []
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 140
	
	func setKeyColumnWidths(_ value: [InfoGridRow_KeyColumn_Width]) {
		keyColumnWidths = value
	}
	func getKeyColumnWidths() -> [InfoGridRow_KeyColumn_Width] {
		return keyColumnWidths
	}
	// </InfoGridView Protocol>
	
	private let verticalSpacingBetweenRows: CGFloat = 12
	private let horizontalSpacingBetweenColumns: CGFloat = 8
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var infoGridRows: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: verticalSpacingBetweenRows) {
			
			if let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment {
				
				rows_incomingPayment(incomingPayment)
				
			} else if let outgoingPayment = paymentInfo.payment as? Lightning_kmpOutgoingPayment {
				
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

			// There's usually just one receivedWith instance.
			// But there could technically be multiple, so we'll show a section for each if that's the case.
			
			let receivedWithArray = received.receivedWith.sorted { $0.hash < $1.hash }
			ForEach(receivedWithArray.indices, id: \.self) { index in
				
				let receivedWith = receivedWithArray[index]
				header(NSLocalizedString("Payment Received", comment: "Title in DetailsView_IncomingPayment"))
				
				paymentReceived_receivedAt(received)
				paymentReceived_amountReceived(receivedWith)
				paymentReceived_via(receivedWith)
				paymentReceived_channelId(receivedWith)
			}
		}
	}
	
	@ViewBuilder
	func rows_outgoingPayment(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			header("lnurl-pay")
			
			lnurl_service(lnurlPay)
			lnurl_range(lnurlPay)
		}
		
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
			offChain_elapsed(outgoingPayment)
			offChain_amountSent(outgoingPayment)
			offChain_fees(outgoingPayment)
			offChain_amountReceived(outgoingPayment)
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
	func lnurl_service(_ lnurlPay: LNUrl.Pay) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("service", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(lnurlPay.lnurl.host)
		}
	}
	
	@ViewBuilder
	func lnurl_range(_ lnurlPay: LNUrl.Pay) -> some View {
		let identifier: String = #function
		
		if lnurlPay.maxSendable.msat > lnurlPay.minSendable.msat {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("range", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let minFormatted = Utils.formatBitcoin(msat: lnurlPay.minSendable, bitcoinUnit: .sat, policy: .showMsats)
				let maxFormatted = Utils.formatBitcoin(msat: lnurlPay.maxSendable, bitcoinUnit: .sat, policy: .showMsats)
				
				// is there a cleaner way to do this ???
				if minFormatted.hasFractionDigits {
				
					if maxFormatted.hasFractionDigits {
						
						Text(verbatim: "\(minFormatted.integerDigits)") +
						Text(verbatim: "\(minFormatted.decimalSeparator)\(minFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " – ") +
						Text(verbatim: "\(maxFormatted.integerDigits)") +
						Text(verbatim: "\(maxFormatted.decimalSeparator)\(maxFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: "\(minFormatted.integerDigits)") +
						Text(verbatim: "\(minFormatted.decimalSeparator)\(minFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " – ") +
						Text(verbatim: maxFormatted.digits) +
						Text(verbatim: " \(maxFormatted.type)")
					}
					
				} else {
					
					if maxFormatted.hasFractionDigits {
						
						Text(verbatim: minFormatted.digits) +
						Text(verbatim: " – ") +
						Text(verbatim: "\(maxFormatted.integerDigits)") +
						Text(verbatim: "\(maxFormatted.decimalSeparator)\(maxFormatted.fractionDigits)")
							.foregroundColor(.secondary) +
						Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: minFormatted.digits) +
						Text(verbatim: " – ") +
						Text(verbatim: maxFormatted.digits) +
						Text(verbatim: " \(maxFormatted.type)")
					}
				}
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_invoiceCreated(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("invoice created", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: paymentRequest.timestampDate)
		}
	}
	
	@ViewBuilder
	func paymentRequest_amountRequested(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount requested", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			if let msat = paymentRequest.amount {
				commonValue_amounts(displayAmounts: displayAmounts(
					msat: msat,
					originalFiat: nil // we don't have this info (at time of invoice generation)
				))
			} else {
				Text("Any amount")
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_paymentHash(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("payment hash", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let paymentHash = paymentInfo.payment.paymentHashString()
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
					
			keyColumn(NSLocalizedString("received at", comment: "Label in DetailsView_IncomingPayment"))
					
		} valueColumn: {
					
			commonValue_date(date: received.receivedAtDate)
		}
	}
	
	@ViewBuilder
	func paymentReceived_amountReceived(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount received", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amounts(displayAmounts: displayAmounts(
				msat: receivedWith.amount,
				originalFiat: paymentInfo.metadata.originalFiat
			))
		}
	}
	
	@ViewBuilder
	func paymentReceived_via(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
		
			keyColumn(NSLocalizedString("via", comment: "Label in DetailsView_IncomingPayment"))
		
		} valueColumn: {
		
			if let _ = receivedWith.asLightningPayment() {
				Text("Lightning network")
		
			} else if let _ = receivedWith.asNewChannel() {
				Text("New Channel (auto-created)")
		
			} else {
				Text("")
			}
		}
	}
	
	@ViewBuilder
	func paymentReceived_channelId(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		if let newChannel = receivedWith.asNewChannel() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
					
			keyColumn(NSLocalizedString("sent at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: offChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func offChain_elapsed(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		if let milliseconds = outgoingPayment.paymentTimeElapsed() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("elapsed", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				if milliseconds < 1_000 {
					Text("\(milliseconds) milliseconds")
					
				} else if milliseconds < (90 * 1_000) {
					Text("\(displayElapsedSeconds(milliseconds: milliseconds)) seconds")
					
				} else {
					let (minutes, seconds) = displayElapsedMinutes(milliseconds: milliseconds)
					
					Text("\(minutes):\(seconds) minutes")
				}
			}
		}
	}
	
	@ViewBuilder
	func offChain_amountReceived(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount received", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amounts(displayAmounts: displayAmounts(
				msat: outgoingPayment.recipientAmount,
				originalFiat: paymentInfo.metadata.originalFiat
			))
		}
	}
	
	@ViewBuilder
	func offChain_fees(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("fees paid", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let display_amounts = displayAmounts(
				msat: outgoingPayment.fees,
				originalFiat: paymentInfo.metadata.originalFiat
			)
			let display_percent = displayFeePercent(
				fees: outgoingPayment.fees,
				total: outgoingPayment.amount
			)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				
				let display_msat = display_amounts.bitcoin
				if display_msat.hasFractionDigits { // has visible millisatoshi's
					Text(verbatim: "\(display_msat.integerDigits)") +
					Text(verbatim: "\(display_msat.decimalSeparator)\(display_msat.fractionDigits)")
						.foregroundColor(.secondary) +
					Text(verbatim: " \(display_msat.type)")
				} else {
					Text(verbatim: display_msat.string)
				}
				
				Text(verbatim: display_percent)
				
				if let display_fiatCurrent = display_amounts.fiatCurrent {
					Text(verbatim: "≈ \(display_fiatCurrent.string)") +
					Text(" (now)")
						.foregroundColor(.secondary)
				}
				if let display_fiatOriginal = display_amounts.fiatOriginal {
					Text("≈ \(display_fiatOriginal.string)") +
					Text(" (original)")
						.foregroundColor(.secondary)
				}
			}
		}
	}
	
	@ViewBuilder
	func offChain_amountSent(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("amount sent", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amounts(displayAmounts: displayAmounts(
				msat: outgoingPayment.amount,
				originalFiat: paymentInfo.metadata.originalFiat
			))
		}
	}
	
	@ViewBuilder
	func offChain_recipientPubkey(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("recipient pubkey", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(outgoingPayment.recipient.value.toHex())
		}
	}
	
	@ViewBuilder
	func onChain_type(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("completed at", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: onChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func onChain_claimed(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("claimed amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amounts(displayAmounts: displayAmounts(
				sat: onChain.claimed,
				originalFiat: paymentInfo.metadata.originalFiat
			))
		}
	}
	
	@ViewBuilder
	func onChain_btcTxids(_ onChain: Lightning_kmpOutgoingPayment.StatusCompletedSucceededOnChain) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("timestamp", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_date(date: failed.completedAtDate)
		}
	}
	
	@ViewBuilder
	func failed_reason(_ failed: Lightning_kmpOutgoingPayment.StatusCompletedFailed) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("reason", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(failed.reason.description)
		}
	}
	
	@ViewBuilder
	func part_row(_ part: Lightning_kmpOutgoingPayment.Part) -> some View {
		let identifier: String = #function
		let imgSize: CGFloat = 20
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
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
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, policy: .showMsats)
						if formatted.hasFractionDigits { // has visible millisatoshi's
							Text(verbatim: "\(formatted.integerDigits)") +
							Text(verbatim: "\(formatted.decimalSeparator)\(formatted.fractionDigits)")
								.foregroundColor(.secondary) +
							Text(verbatim: " \(formatted.type)")
						} else {
							Text(verbatim: formatted.string)
						}
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
						
						let formatted = Utils.formatBitcoin(msat: part.amount, bitcoinUnit: .sat, policy: .showMsats)
						if formatted.hasFractionDigits { // has visible millisatoshi's
							Text(verbatim: "\(formatted.integerDigits)") +
							Text(verbatim: "\(formatted.decimalSeparator)\(formatted.fractionDigits)")
								.foregroundColor(.secondary) +
							Text(verbatim: " \(formatted.type)")
						} else {
							Text(verbatim: formatted.string)
						}
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
	func commonValue_amounts(displayAmounts: DisplayAmounts) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			let display_msat = displayAmounts.bitcoin
			if display_msat.hasFractionDigits { // has visible millisatoshi's
				Text(verbatim: "\(display_msat.integerDigits)") +
				Text(verbatim: "\(display_msat.decimalSeparator)\(display_msat.fractionDigits)")
					.foregroundColor(.secondary) +
				Text(verbatim: " \(display_msat.type)")
			} else {
				Text(verbatim: display_msat.string)
			}
			
			if let display_fiatCurrent = displayAmounts.fiatCurrent {
				Text(verbatim: "≈ \(display_fiatCurrent.string)") +
				Text(" (now)")
					.foregroundColor(.secondary)
			}
			if let display_fiatOriginal = displayAmounts.fiatOriginal {
				Text(verbatim: "≈ \(display_fiatOriginal.string)") +
				Text(" (original)")
					.foregroundColor(.secondary)
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
	
	func displayElapsedSeconds(milliseconds: Int64) -> String {
		
		let seconds = Double(milliseconds) / Double(1_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		formatter.usesGroupingSeparator = true
		formatter.minimumFractionDigits = 3
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: seconds))!
	}
	
	func displayElapsedMinutes(milliseconds: Int64) -> (String, String) {
		
		let minutes = milliseconds / (60 * 1_000)
		let seconds = milliseconds % (60 * 1_000) % 1_000
		
		let mFormatter = NumberFormatter()
		mFormatter.numberStyle = .decimal
		mFormatter.usesGroupingSeparator = true
		
		let minutesStr = mFormatter.string(from: NSNumber(value: minutes))!
		
		let sFormatter = NumberFormatter()
		sFormatter.minimumIntegerDigits = 2
		
		let secondsStr = sFormatter.string(from: NSNumber(value: seconds))!
		
		return (minutesStr, secondsStr)
	}
	
	func displayAmounts(
		msat: Lightning_kmpMilliSatoshi,
		originalFiat: ExchangeRate.BitcoinPriceRate?
	) -> DisplayAmounts {
		
		let bitcoin = Utils.formatBitcoin(msat: msat, bitcoinUnit: .sat, policy: .showMsats)
		var fiatCurrent: FormattedAmount? = nil
		var fiatOriginal: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			fiatCurrent = Utils.formatFiat(msat: msat, exchangeRate: fiatExchangeRate)
		}
		if let originalFiat = originalFiat {
			fiatOriginal = Utils.formatFiat(msat: msat, exchangeRate: originalFiat)
		}
		
		return DisplayAmounts(bitcoin: bitcoin, fiatCurrent: fiatCurrent, fiatOriginal: fiatOriginal)
	}
	
	func displayAmounts(
		sat: Bitcoin_kmpSatoshi,
		originalFiat: ExchangeRate.BitcoinPriceRate?
	) -> DisplayAmounts {
		
		let bitcoin = Utils.formatBitcoin(sat: sat, bitcoinUnit: .sat)
		var fiatCurrent: FormattedAmount? = nil
		var fiatOriginal: FormattedAmount? = nil

		if let fiatExchangeRate = currencyPrefs.fiatExchangeRate() {
			fiatCurrent = Utils.formatFiat(sat: sat, exchangeRate: fiatExchangeRate)
		}
		if let originalFiat = originalFiat {
			fiatOriginal = Utils.formatFiat(sat: sat, exchangeRate: originalFiat)
		}
		
		return DisplayAmounts(bitcoin: bitcoin, fiatCurrent: fiatCurrent, fiatOriginal: fiatOriginal)
	}
	
	func displayFeePercent(fees: Lightning_kmpMilliSatoshi, total: Lightning_kmpMilliSatoshi) -> String {
		
		let percent: Double
		if total.msat == 0 {
			percent = 0.0
		} else {
			percent = Double(fees.msat) / Double(total.msat)
		}
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	struct DisplayAmounts {
		let bitcoin: FormattedAmount
		let fiatCurrent: FormattedAmount?
		let fiatOriginal: FormattedAmount?
	}
	
	struct InfoGridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
		
		let identifier: String
		let hSpacing: CGFloat
		let keyColumnWidth: CGFloat
		let keyColumn: KeyColumn
		let valueColumn: ValueColumn
		
		init(
			identifier: String,
			hSpacing: CGFloat,
			keyColumnWidth: CGFloat,
			@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
			@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
		) {
			self.identifier = identifier
			self.hSpacing = hSpacing
			self.keyColumnWidth = keyColumnWidth
			self.keyColumn = keyColumnBuilder()
			self.valueColumn = valueColumnBuilder()
		}
		
		var body: some View {
			
			InfoGridRow(identifier: identifier, hSpacing: hSpacing, keyColumnWidth: keyColumnWidth) {
				
				keyColumn
				
			} valueColumn: {
				
				valueColumn.font(.callout)
			}
		}
	}
}
