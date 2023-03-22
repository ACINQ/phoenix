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
	
	let type: PaymentViewType
	@Binding var paymentInfo: WalletPaymentInfo
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@ViewBuilder
	var body: some View {
		
		switch type {
		case .sheet:
			content()
				.navigationTitle(NSLocalizedString("Details", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
			
		case .embedded:
			
			content()
				.navigationTitle(NSLocalizedString("Payment Details", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			if case .sheet(let closeAction) = type {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Button {
						presentationMode.wrappedValue.dismiss()
					} label: {
						Image(systemName: "chevron.backward")
							.imageScale(.medium)
					}
					Spacer()
					Button {
						closeAction()
					} label: {
						Image(systemName: "xmark") // must match size of chevron.backward above
							.imageScale(.medium)
					}
				} // </VStack>
				.font(.title2)
				.padding()
				
			} else {
				Spacer().frame(height: 25)
			}
				
			ScrollView {
				DetailsInfoGrid(paymentInfo: $paymentInfo)
					.padding([.leading, .trailing])
			}
		}
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
				paymentReceived_fundingTxId(receivedWith)
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
		
		} else if let swapOut = outgoingPayment.details.asSwapOut() {
			
			header(NSLocalizedString("Swap Out", comment: "Title in DetailsView_IncomingPayment"))
			
			swapOut_address(swapOut.address)
			paymentRequest_paymentHash(swapOut.paymentRequest)
			
		} else if let channelClosing = outgoingPayment.details.asChannelClosing() {
			
			header(NSLocalizedString("Channel Closing", comment: "Title in DetailsView_IncomingPayment"))
			
			channelClosing_channelId(channelClosing)
			if !outgoingPayment.closingTxParts().isEmpty { // otherwise type is unknown
				channelClosing_type(outgoingPayment)
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
			onChain_claimed(outgoingPayment)
			onChain_btcTxids(outgoingPayment)
			
		} else if let failed = outgoingPayment.status.asFailed() {
			
			header(NSLocalizedString("Send Failed", comment: "Title in DetailsView_IncomingPayment"))
			
			failed_failedAt(failed)
			failed_reason(failed)
		}
		
		if outgoingPayment.parts.count > 0 && outgoingPayment.details.asChannelClosing() == nil {
			
			header(NSLocalizedString("Payment Parts", comment: "Title in DetailsView_IncomingPayment"))
			
			ForEach(outgoingPayment.parts.indices, id: \.self) { index in
				let part = outgoingPayment.parts[index]
				if (part is Lightning_kmpOutgoingPayment.LightningPart) {
					lightningPart_row(part as! Lightning_kmpOutgoingPayment.LightningPart)
				}
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
		.accessibilityAddTraits(.isHeader)
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
	func lnurl_service(_ lnurlPay: LnurlPay.Intent) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("service", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(lnurlPay.initialUrl.host)
		}
	}
	
	@ViewBuilder
	func lnurl_range(_ lnurlPay: LnurlPay.Intent) -> some View {
		let identifier: String = #function
		
		if lnurlPay.maxSendable.msat > lnurlPay.minSendable.msat {
			
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("range", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let minFormatted = Utils.formatBitcoin(
					msat        : lnurlPay.minSendable,
					bitcoinUnit : currencyPrefs.bitcoinUnit,
					policy      : .showMsatsIfNonZero
				)
				let maxFormatted = Utils.formatBitcoin(
					msat        : lnurlPay.maxSendable,
					bitcoinUnit : currencyPrefs.bitcoinUnit,
					policy      : .showMsatsIfNonZero
				)
				
				// is there a cleaner way to do this ???
				if minFormatted.hasSubFractionDigits {
				
					if maxFormatted.hasSubFractionDigits {
						
						Text(verbatim: minFormatted.integerDigits)
						+	Text(verbatim: minFormatted.decimalSeparator)
								.foregroundColor(minFormatted.hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: minFormatted.stdFractionDigits)
						+	Text(verbatim: minFormatted.subFractionDigits)
								.foregroundColor(.secondary)
						+	Text(verbatim: " – ")
						+	Text(verbatim: maxFormatted.integerDigits)
						+	Text(verbatim: maxFormatted.decimalSeparator)
								.foregroundColor(maxFormatted.hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: maxFormatted.stdFractionDigits)
						+	Text(verbatim: maxFormatted.subFractionDigits)
								.foregroundColor(.secondary)
						+	Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: minFormatted.integerDigits)
						+	Text(verbatim: minFormatted.decimalSeparator)
								.foregroundColor(minFormatted.hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: minFormatted.stdFractionDigits)
						+	Text(verbatim: minFormatted.subFractionDigits)
								.foregroundColor(.secondary)
						+	Text(verbatim: " – ")
						+	Text(verbatim: maxFormatted.digits)
						+	Text(verbatim: " \(maxFormatted.type)")
					}
					
				} else {
					
					if maxFormatted.hasSubFractionDigits {
						
						Text(verbatim: minFormatted.digits)
						+	Text(verbatim: " – ")
						+	Text(verbatim: maxFormatted.integerDigits)
						+	Text(verbatim: maxFormatted.decimalSeparator)
								.foregroundColor(maxFormatted.hasStdFractionDigits ? .primary : .secondary)
						+	Text(verbatim: maxFormatted.stdFractionDigits)
						+	Text(verbatim: maxFormatted.subFractionDigits)
								.foregroundColor(.secondary)
						+	Text(verbatim: " \(maxFormatted.type)")
						
					} else {
						
						Text(verbatim: minFormatted.digits)
						+	Text(verbatim: " – ")
						+	Text(verbatim: maxFormatted.digits)
						+	Text(verbatim: " \(maxFormatted.type)")
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
	func swapOut_address(_ address: String) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("address", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			Text(verbatim: address)
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
	func paymentReceived_fundingTxId(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		if let newChannel = receivedWith.asNewChannel(),
			let channelId = newChannel.channelId,
			let channel = Biz.business.peerManager.getChannelWithCommitments(channelId: channelId)
		{
			InfoGridRowWrapper(
				identifier: identifier,
				hSpacing: horizontalSpacingBetweenColumns,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(NSLocalizedString("funding txid", comment: "Label in DetailsView_IncomingPayment"))
				
			} valueColumn: {
				
				let str = channel.commitments.fundingTxId.toHex()
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
	func channelClosing_type(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("type", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			let closingTxPart = outgoingPayment.closingTxParts().first
			switch closingTxPart?.closingType {
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
			
			commonValue_amounts(
				displayAmounts: displayAmounts(
					msat: outgoingPayment.fees,
					originalFiat: paymentInfo.metadata.originalFiat
			 	),
				displayFeePercent: displayFeePercent(
					fees: outgoingPayment.fees,
					total: outgoingPayment.amount
			 	)
			)
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
	func onChain_claimed(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("claimed amount", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			commonValue_amounts(displayAmounts: displayAmounts(
				sat: outgoingPayment.claimedOnChain(),
				originalFiat: paymentInfo.metadata.originalFiat
			))
		}
	}
	
	@ViewBuilder
	func onChain_btcTxids(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		let closingTxParts = outgoingPayment.closingTxParts()
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			keyColumn(NSLocalizedString("bitcoin txids", comment: "Label in DetailsView_IncomingPayment"))
			
		} valueColumn: {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
				ForEach(closingTxParts.indices, id: \.self) { index in

					let txid = closingTxParts[index].txId.toHex()
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
	func lightningPart_row(_ part: Lightning_kmpOutgoingPayment.LightningPart) -> some View {
		let identifier: String = #function
		let imgSize: CGFloat = 20
		
		InfoGridRowWrapper(
			identifier: identifier,
			hSpacing: horizontalSpacingBetweenColumns,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			if let part_succeeded = part.status as? Lightning_kmpOutgoingPayment.LightningPartStatusSucceeded {
				keyColumn(shortDisplayTime(date: part_succeeded.completedAtDate))
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.LightningPartStatusFailed {
				keyColumn(shortDisplayTime(date: part_failed.completedAtDate))
				
			} else {
				keyColumn(shortDisplayTime(date: part.createdAtDate))
			}
			
		} valueColumn: {
		
			if let _ = part.status as? Lightning_kmpOutgoingPayment.LightningPartStatusSucceeded {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image("ic_payment_sent")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(Color.appPositive)
						
						let formatted = Utils.formatBitcoin(
							msat        : part.amount,
							bitcoinUnit : currencyPrefs.bitcoinUnit,
							policy      : .showMsatsIfNonZero
						)
						if formatted.hasSubFractionDigits { // e.g.: has visible millisatoshi's
							Text(verbatim: formatted.integerDigits)
							+	Text(verbatim: formatted.decimalSeparator)
									.foregroundColor(formatted.hasStdFractionDigits ? .primary : .secondary)
							+	Text(verbatim: formatted.stdFractionDigits)
							+	Text(verbatim: formatted.subFractionDigits)
									.foregroundColor(.secondary)
							+	Text(verbatim: " \(formatted.type)")
						} else {
							Text(verbatim: formatted.string)
						}
					}
				}
				
			} else if let part_failed = part.status as? Lightning_kmpOutgoingPayment.LightningPartStatusFailed {
				
				VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "xmark.circle")
							.renderingMode(.template)
							.resizable()
							.aspectRatio(contentMode: .fit)
							.frame(width: imgSize, height: imgSize)
							.foregroundColor(.appNegative)
						
						let formatted = Utils.formatBitcoin(
							msat        : part.amount,
							bitcoinUnit : currencyPrefs.bitcoinUnit,
							policy      : .showMsatsIfNonZero
						)
						if formatted.hasSubFractionDigits { // e.g.: has visible millisatoshi's
							Text(verbatim: formatted.integerDigits)
							+	Text(verbatim: formatted.decimalSeparator)
									.foregroundColor(formatted.hasStdFractionDigits ? .primary : .secondary)
							+	Text(verbatim: formatted.stdFractionDigits)
							+	Text(verbatim: formatted.subFractionDigits)
									.foregroundColor(.secondary)
							+	Text(verbatim: " \(formatted.type)")
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
	func commonValue_amounts(
		displayAmounts: DisplayAmounts,
		displayFeePercent: String? = nil
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			let display_bitcoin = displayAmounts.bitcoin
			if display_bitcoin.hasSubFractionDigits {
				
				// We're showing sub-fractional values.
				// For example, we're showing millisatoshis.
				//
				// It's helpful to downplay the sub-fractional part visually.
				
				let hasStdFractionDigits = display_bitcoin.hasStdFractionDigits
				
				Text(verbatim: display_bitcoin.integerDigits)
				+	Text(verbatim: display_bitcoin.decimalSeparator)
						.foregroundColor(hasStdFractionDigits ? .primary : .secondary)
				+	Text(verbatim: display_bitcoin.stdFractionDigits)
				+	Text(verbatim: display_bitcoin.subFractionDigits)
						.foregroundColor(.secondary)
				+	Text(verbatim: " \(display_bitcoin.type)")
				
			} else {
				Text(verbatim: display_bitcoin.string)
			}
			
			if let display_feePercent = displayFeePercent {
				Text(verbatim: display_feePercent)
			}
			
			if let display_fiatCurrent = displayAmounts.fiatCurrent {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Text(verbatim: "≈ \(display_fiatCurrent.digits) ")
					Text_CurrencyName(currency: display_fiatCurrent.currency, fontTextStyle: .callout)
					Text(" (now)")
						.foregroundColor(.secondary)
				}
			}
			if let display_fiatOriginal = displayAmounts.fiatOriginal {
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					Text(verbatim: "≈ \(display_fiatOriginal.digits) ")
					Text_CurrencyName(currency: display_fiatOriginal.currency, fontTextStyle: .callout)
					Text(" (original)")
						.foregroundColor(.secondary)
				}
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
		
		let bitcoin = Utils.formatBitcoin(
			msat        : msat,
			bitcoinUnit : currencyPrefs.bitcoinUnit,
			policy      : .showMsatsIfNonZero
		)
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
		
		let bitcoin = Utils.formatBitcoin(sat: sat, bitcoinUnit: currencyPrefs.bitcoinUnit)
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
