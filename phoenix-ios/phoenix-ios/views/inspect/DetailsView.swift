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
	
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
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
			
			header()
				
			DetailsInfoGrid(
				paymentInfo: $paymentInfo,
				showOriginalFiatValue: $showOriginalFiatValue,
				showFiatValueExplanation: $showFiatValueExplanation
			)
		}
		.background(Color.primaryBackground)
	}
	
	@ViewBuilder
	func header() -> some View {
		
		if case .sheet(let closeAction) = type {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.backward")
						.imageScale(.medium)
						.font(.title3.weight(.semibold))
				}
				Spacer()
				Button {
					closeAction()
				} label: {
					Image(systemName: "xmark")
						.imageScale(.medium)
						.font(.title3)
				}
			} // </VStack>
			.padding()
			
		} else {
			Spacer().frame(height: 25)
		}
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

fileprivate struct DetailsInfoGrid: InfoGridView {
	
	@Binding var paymentInfo: WalletPaymentInfo
	@Binding var showOriginalFiatValue: Bool
	@Binding var showFiatValueExplanation: Bool
	
	@State var showBlockchainExplorerOptions = false
	
	@State var truncatedText: [String: Bool] = [:]
	
	// <InfoGridView Protocol>
	let minKeyColumnWidth: CGFloat = 50
	let maxKeyColumnWidth: CGFloat = 140
	
	@State var keyColumnSizes: [InfoGridRow_KeyColumn_Size] = []
	func setKeyColumnSizes(_ value: [InfoGridRow_KeyColumn_Size]) {
		keyColumnSizes = value
	}
	func getKeyColumnSizes() -> [InfoGridRow_KeyColumn_Size] {
		return keyColumnSizes
	}
	
	@State var rowSizes: [InfoGridRow_Size] = []
	func setRowSizes(_ sizes: [InfoGridRow_Size]) {
		rowSizes = sizes
	}
	func getRowSizes() -> [InfoGridRow_Size] {
		return rowSizes
	}
	// </InfoGridView Protocol>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var infoGridRows: some View {
		
		// Architecture:
		//
		// It would be nice to simply use a List here.
		// Except that a List is lazy-loaded,
		// which means we cannot properly calculate the KeyColumnWidth.
		// That is, the KeyColumnWidth changes as you scroll, which is not what we want.
		//
		// So instead we're forced to make a fake List using a VStack.
		
		ScrollView {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {

				if let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment {
					sections_incomingPayment(incomingPayment)

				} else if let outgoingPayment = paymentInfo.payment as? Lightning_kmpOutgoingPayment {
					sections_outgoingPayment(outgoingPayment)
				}
			}
		}
		.background(Color.primaryBackground)
	}
	
	@ViewBuilder
	func sections_incomingPayment(_ incomingPayment: Lightning_kmpIncomingPayment) -> some View {
		
		if let paymentRequest = incomingPayment.origin.asInvoice()?.paymentRequest {

			InlineSection {
				header("Payment Request")
			} content: {
				paymentRequest_invoiceCreated(paymentRequest)
				paymentRequest_invoiceDescription(paymentRequest)
				paymentRequest_amountRequested(paymentRequest)
				paymentRequest_paymentHash(paymentRequest)
				paymentRequest_preimage()
				paymentRequest_invoice(paymentRequest)
			}
		}

		if let received = incomingPayment.received {

			InlineSection {
				header("Payment Received")
			} content: {
				paymentReceived_receivedAt(received)
				amountReceived(msat: received.amount)
				payment_standardFees(incomingPayment)
				payment_minerFees(incomingPayment)
			}

			let receivedWithArray = received.receivedWith.sorted { $0.hash < $1.hash }
			ForEach(receivedWithArray.indices, id: \.self) { idx in

				let receivedWith = receivedWithArray[idx]

				InlineSection {
					header("Payment Part #\(idx+1)")
				} content: {
					paymentReceived_via(receivedWith)
					paymentReceived_channelId(receivedWith)
					paymentReceived_fundingTxId(receivedWith)
				}
			}
		}
	}
	
	@ViewBuilder
	func sections_outgoingPayment(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		
		if let lnurlPay = paymentInfo.metadata.lnurl?.pay {
			
			InlineSection {
				header("lnurl-pay")
			} content: {
				lnurl_service(lnurlPay)
				lnurl_range(lnurlPay)
			}
		}
		
		if let lightningPayment = outgoingPayment as? Lightning_kmpLightningOutgoingPayment {
			
			if let normal = lightningPayment.details.asNormal() {
				
				InlineSection {
					header("Payment Request")
				} content: {
					paymentRequest_invoiceCreated(normal.paymentRequest)
					paymentRequest_invoiceDescription(normal.paymentRequest)
					paymentRequest_amountRequested(normal.paymentRequest)
					paymentRequest_paymentHash(normal.paymentRequest)
					paymentRequest_preimage()
					paymentRequest_invoice(normal.paymentRequest)
				}
				
			} else if let swapOut = lightningPayment.details.asSwapOut() {
				
				InlineSection {
					header("Swap Out")
				} content: {
					swapOut_address(swapOut.address)
					paymentRequest_paymentHash(swapOut.paymentRequest)
				}
			}
			
			if let offChain = lightningPayment.status.asOffChain() {
				
				InlineSection {
					header("Payment Sent")
				} content: {
					offChain_completedAt(offChain)
					offChain_elapsed(outgoingPayment)
					outgoing_amountSent(outgoingPayment)
					offChain_fees(outgoingPayment)
					amountReceived(msat: lightningPayment.recipientAmount)
					offChain_recipientPubkey(lightningPayment)
				}
			
			} else if let failed = lightningPayment.status.asFailed() {
				
				InlineSection {
					header("Send Failed")
				} content: {
					failed_failedAt(failed)
					failed_reason(failed)
				}
			}
			
			if lightningPayment.parts.count > 0 {
				
				InlineSection {
					header("Payment Parts")
				} content: {
					ForEach(lightningPayment.parts.indices, id: \.self) { index in
						lightningPart_row(lightningPayment.parts[index])
					}
				}
			}
			
		} else if let spliceOut = outgoingPayment as? Lightning_kmpSpliceOutgoingPayment {
			
			InlineSection {
				header("Splice Out")
			} content: {
				onChain_broadcastAt(spliceOut)
				onChain_confirmedAt(spliceOut)
				outgoing_amountSent(outgoingPayment)
				onChain_minerFees(spliceOut)
				amountReceived(sat: spliceOut.recipientAmount)
				onChain_btcTxid(spliceOut)
			}
		
		} else if let channelClosing = outgoingPayment as? Lightning_kmpChannelCloseOutgoingPayment {
			
			InlineSection {
				header("Channel Closing")
			} content: {
				channelClosing_channelId(channelClosing)
				channelClosing_type(channelClosing)
				channelClosing_btcAddress(channelClosing)
				channelClosing_addrType(channelClosing)
			}
			
			InlineSection {
				header("Closing Status")
			} content: {
				onChain_broadcastAt(channelClosing)
				onChain_confirmedAt(channelClosing)
				outgoing_amountSent(outgoingPayment)
				onChain_minerFees(channelClosing)
				amountReceived(sat: channelClosing.recipientAmount)
				onChain_btcTxid(channelClosing)
			}
			
		} else if let spliceCpfp = outgoingPayment as? Lightning_kmpSpliceCpfpOutgoingPayment {
			
			InlineSection {
				header("Bump Fee (CPFP)")
			} content: {
				onChain_broadcastAt(spliceCpfp)
				onChain_confirmedAt(spliceCpfp)
				onChain_minerFees(spliceCpfp)
				onChain_btcTxid(spliceCpfp)
			}
		}
	}
	
	@ViewBuilder
	func header(_ title: LocalizedStringKey) -> some View {
		
		HStack {
			Spacer()
			Text(title)
				.textCase(.uppercase)
				.lineLimit(1)
				.minimumScaleFactor(0.5)
				.font(.headline)
				.foregroundColor(Color(UIColor.systemGray))
			Spacer()
		}
		.padding(.bottom, 12)
		.accessibilityAddTraits(.isHeader)
	}
	
	@ViewBuilder
	func keyColumn(_ title: LocalizedStringKey) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func keyColumn(verbatim title: String) -> some View {
		
		Text(title)
			.textCase(.lowercase)
			.font(.subheadline.weight(.thin))
			.multilineTextAlignment(.trailing)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func lnurl_service(_ lnurlPay: LnurlPay.Intent) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("service")
			
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
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("range")
				
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
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("invoice created")
			
		} valueColumn: {
			
			commonValue_date(date: paymentRequest.timestampDate)
		}
	}
	
	@ViewBuilder
	func paymentRequest_invoiceDescription(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("invoice description")
			
		} valueColumn: {
			
			let description = (paymentRequest.description_ ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
			if description.isEmpty {
				Text("empty").foregroundColor(.secondary)
			} else {
				Text(description)
			}
		}
	}
	
	@ViewBuilder
	func paymentRequest_amountRequested(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("amount requested")
			
		} valueColumn: {
			
			if let msat = paymentRequest.amount {
				commonValue_amounts(
					identifier: identifier,
					displayAmounts: displayAmounts(
						msat: msat,
						originalFiat: nil // we don't have this info (at time of invoice generation)
					)
				)
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
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("payment hash")
			
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
	func paymentRequest_preimage() -> some View {
		let identifier: String = #function
		
		if let preimage = paymentPreimage() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("payment preimage")
				
			} valueColumn: {
				
				Text(preimage)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = preimage
						}) {
							Text("Copy")
						}
					}
			} // </InfoGridRowWrapper>
		}
	}
	
	@ViewBuilder
	func paymentRequest_invoice(_ paymentRequest: Lightning_kmpPaymentRequest) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("invoice")
			
		} valueColumn: {
			
			let invoice = paymentRequest.write()
			Text(invoice)
				.lineLimit(5)
				.truncationMode(.tail)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = invoice
					}) {
						Text("Copy")
					}
				}
		} // </InfoGridRowWrapper>
	}
	
	@ViewBuilder
	func paymentReceived_receivedAt(
		_ received: Lightning_kmpIncomingPayment.Received
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("received at")
					
		} valueColumn: {
					
			commonValue_date(date: received.receivedAtDate)
		}
	}
	
	@ViewBuilder
	func swapOut_address(_ address: String) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("address")
			
		} valueColumn: {
			
			Text(verbatim: address)
		}
	}
	
	@ViewBuilder
	func paymentReceived_via(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("via")
		
		} valueColumn: {
		
			if let _ = receivedWith.asLightningPayment() {
				Text("Lightning network")
		
			} else if let _ = receivedWith.asNewChannel() {
				Text("New Channel (auto-created)")
				
			} else if let _ = receivedWith.asSpliceIn() {
				Text("Splice-In")
			
			} else {
				Text("")
			}
		}
	}
	
	@ViewBuilder
	func paymentReceived_channelId(_ receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> some View {
		let identifier: String = #function
		
		if let channelId = receivedWith.asNewChannel()?.channelId ?? receivedWith.asSpliceIn()?.channelId {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("channel id")
				
			} valueColumn: {
				
				let str = channelId.toHex()
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
		
		if let txId = self.txId(receivedWith: receivedWith) {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("funding txid")
				
			} valueColumn: {
				
				let txIdStr = txId.toHex()
				Text(txIdStr)
					.contextMenu {
						Button(action: {
							UIPasteboard.general.string = txIdStr
						}) {
							Text("Copy")
						}
						Button {
							showBlockchainExplorerOptions = true
						} label: {
							Text("Explore")
						}
					}
					.confirmationDialog("Blockchain Explorer",
						isPresented: $showBlockchainExplorerOptions,
						titleVisibility: .automatic
					) {
						Button {
							exploreTx(txIdStr, website: BlockchainExplorer.WebsiteMempoolSpace())
						} label: {
							Text(verbatim: "Mempool.space") // no localization needed
						}
						Button {
							exploreTx(txIdStr, website: BlockchainExplorer.WebsiteBlockstreamInfo())
						} label: {
							Text(verbatim: "Blockstream.info") // no localization needed
						}
					} // </confirmationDialog>
			}
		}
	}
	
	@ViewBuilder
	func payment_standardFees(
		_ payment: Lightning_kmpWalletPayment
	) -> some View {
		let identifier: String = #function
		
		if let standardFees = payment.standardFees(), standardFees.0 > 0 {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(verbatim: standardFees.1)
				
			} valueColumn: {
				
				commonValue_amounts(
					identifier: identifier,
					displayAmounts: displayAmounts(
						msat: Lightning_kmpMilliSatoshi(msat: standardFees.0),
						originalFiat: paymentInfo.metadata.originalFiat
					)
				)
			}
		}
	}
	
	@ViewBuilder
	func payment_minerFees(
		_ payment: Lightning_kmpWalletPayment
	) -> some View {
		let identifier: String = #function
		
		if let minerFees = payment.minerFees(), minerFees.0 > 0 {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				
				keyColumn(verbatim: minerFees.1)
				
			} valueColumn: {
				
				commonValue_amounts(
					identifier: identifier,
					displayAmounts: displayAmounts(
						msat: Lightning_kmpMilliSatoshi(msat: minerFees.0),
						originalFiat: paymentInfo.metadata.originalFiat
			 		)
				)
			}
		}
	}
	
	@ViewBuilder
	func channelClosing_channelId(
		_ channelClosing: Lightning_kmpChannelCloseOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("channel id")
			
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
	func channelClosing_type(
		_ channelClosing: Lightning_kmpChannelCloseOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("type")
			
		} valueColumn: {
			
			switch channelClosing.closingType {
				case Lightning_kmpChannelClosingType.local   : Text(verbatim: "Local")
				case Lightning_kmpChannelClosingType.mutual  : Text(verbatim: "Mutual")
				case Lightning_kmpChannelClosingType.remote  : Text(verbatim: "Remote")
				case Lightning_kmpChannelClosingType.revoked : Text(verbatim: "Revoked")
				case Lightning_kmpChannelClosingType.other   : Text(verbatim: "Other")
				default                                                                  : Text(verbatim: "?")
			}
		}
	}
	
	@ViewBuilder
	func channelClosing_btcAddress(
		_ channelClosing: Lightning_kmpChannelCloseOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("bitcoin address")
			
		} valueColumn: {
			
			let bitcoinAddr = channelClosing.address
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
	func channelClosing_addrType(
		_ channelClosing: Lightning_kmpChannelCloseOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("address type")
			
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
	func offChain_completedAt(
		_ offChain: Lightning_kmpLightningOutgoingPayment.StatusCompletedSucceededOffChain
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("sent at")
			
		} valueColumn: {
			
			commonValue_date(date: offChain.completedAtDate)
		}
	}
	
	@ViewBuilder
	func offChain_elapsed(
		_ outgoingPayment: Lightning_kmpOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		if let milliseconds = outgoingPayment.paymentTimeElapsed() {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("elapsed")
				
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
	func offChain_fees(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("fees paid")
			
		} valueColumn: {
			
			commonValue_amounts(
				identifier: identifier,
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
	func offChain_recipientPubkey(
		_ outgoingPayment: Lightning_kmpLightningOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("recipient pubkey")
			
		} valueColumn: {
			
			Text(outgoingPayment.recipient.value.toHex())
		}
	}
	
	@ViewBuilder
	func onChain_broadcastAt(
		_ onChain: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("broadcast at")
			
		} valueColumn: {
			
			commonValue_date(date: onChain.createdAtDate)
		}
	}
	
	@ViewBuilder
	func onChain_confirmedAt(
		_ onChain: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		if let confirmedAtDate = onChain.confirmedAtDate {
			
			InfoGridRowWrapper(
				identifier: identifier,
				keyColumnWidth: keyColumnWidth(identifier: identifier)
			) {
				keyColumn("confirmed at")
				
			} valueColumn: {
				
				commonValue_date(date: confirmedAtDate)
			}
		}
	}
	
	@ViewBuilder
	func onChain_minerFees(
		_ onChain: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("miner fees")
			
		} valueColumn: {
			
			commonValue_amounts(
				identifier: identifier,
				displayAmounts: displayAmounts(
					sat: onChain.miningFees,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func onChain_btcTxid(
		_ onChain: Lightning_kmpOnChainOutgoingPayment
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("bitcoin txids")
			
		} valueColumn: {
			
			let txid = onChain.txId.toHex()
			Text(txid)
				.contextMenu {
					Button(action: {
						UIPasteboard.general.string = txid
					}) {
						Text("Copy")
					}
				}
			
		} // </InfoGridRowWrapper>
	}
	
	@ViewBuilder
	func outgoing_amountSent(_ outgoingPayment: Lightning_kmpOutgoingPayment) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("amount sent")
			
		} valueColumn: {
			
			commonValue_amounts(
				identifier: identifier,
				displayAmounts: displayAmounts(
					msat: outgoingPayment.amount,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func amountReceived(
		msat: Lightning_kmpMilliSatoshi
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("amount received")
			
		} valueColumn: {
			
			commonValue_amounts(
				identifier: identifier,
				displayAmounts: displayAmounts(
					msat: msat,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func amountReceived(
		sat: Bitcoin_kmpSatoshi
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("amount received")
			
		} valueColumn: {
			
			commonValue_amounts(
				identifier: identifier,
				displayAmounts: displayAmounts(
					sat: sat,
					originalFiat: paymentInfo.metadata.originalFiat
				)
			)
		}
	}
	
	@ViewBuilder
	func failed_failedAt(
		_ failed: Lightning_kmpLightningOutgoingPayment.StatusCompletedFailed
	) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("timestamp")
			
		} valueColumn: {
			
			commonValue_date(date: failed.completedAtDate)
		}
	}
	
	@ViewBuilder
	func failed_reason(_ failed: Lightning_kmpLightningOutgoingPayment.StatusCompletedFailed) -> some View {
		let identifier: String = #function
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			keyColumn("reason")
			
		} valueColumn: {
			
			Text(failed.reason.description)
		}
	}
	
	@ViewBuilder
	func lightningPart_row(
		_ part: Lightning_kmpLightningOutgoingPayment.Part
	) -> some View {
		let identifier: String = #function
		let imgSize: CGFloat = 20
		
		InfoGridRowWrapper(
			identifier: identifier,
			keyColumnWidth: keyColumnWidth(identifier: identifier)
		) {
			
			if let part_succeeded = part.status as? Lightning_kmpLightningOutgoingPayment.PartStatusSucceeded {
				keyColumn(verbatim: shortDisplayTime(date: part_succeeded.completedAtDate))
				
			} else if let part_failed = part.status as? Lightning_kmpLightningOutgoingPayment.PartStatusFailed {
				keyColumn(verbatim: shortDisplayTime(date: part_failed.completedAtDate))
				
			} else {
				keyColumn(verbatim: shortDisplayTime(date: part.createdAtDate))
			}
			
		} valueColumn: {
		
			if let _ = part.status as? Lightning_kmpLightningOutgoingPayment.PartStatusSucceeded {
				
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
				
			} else if let part_failed = part.status as? Lightning_kmpLightningOutgoingPayment.PartStatusFailed {
				
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
		identifier: String,
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
			
			// If we know the original fiat exchange rate, then we can switch back and forth
			// between the original & current fiat value.
			//
			// However, if we do NOT know the original fiat value,
			// then we simply show the current fiat value (without the clock button)
			//
			let canShowOriginalFiatValue = displayAmounts.fiatOriginal != nil
			let showingOriginalFiatValue = showOriginalFiatValue && canShowOriginalFiatValue
			
			// The preferred layout is with a single row:
			// 1,234 USD (original) (clock)
			//
			// However, if the text gets truncated it looks really odd:
			// 1,23 USD (origi (clock)
			// 4        nal)
			//
			// In that case we switch to 2 rows.
			// Note that if we detect truncation for either (original) or (now),
			// then we keep the layout at 2 rows so it's not confusing when the user switches back and forth.
			
			let key = identifier
			let isTruncated = truncatedText[key] ?? false
			
			if isTruncated {
				
				// Two rows:
				// 1,234 USD
				//  (original) (clock)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					if showingOriginalFiatValue {
						
						let display_fiatOriginal = displayAmounts.fiatOriginal ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						Text(verbatim: "≈ \(display_fiatOriginal.digits) ")
						Text_CurrencyName(currency: display_fiatOriginal.currency, fontTextStyle: .callout)
						
					} else {
						
						let display_fiatCurrent = displayAmounts.fiatCurrent ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						Text(verbatim: "≈ \(display_fiatCurrent.digits) ")
						Text_CurrencyName(currency: display_fiatCurrent.currency, fontTextStyle: .callout)
					}
				} // </HStack>
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					if showingOriginalFiatValue {
						Text(" (original)").foregroundColor(.secondary)
					} else {
						Text(" (now)").foregroundColor(.secondary)
					}
					
					if canShowOriginalFiatValue {
						
						AnimatedClock(state: clockStateBinding(), size: 14, animationDuration: 0.0)
							.padding(.leading, 4)
							.offset(y: 1)
					}
				} // </HStack>
				
			} else {
				
				// Single row:
				// 1,234 USD (original) (clock)
				
				HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
					
					if showingOriginalFiatValue {
						
						let display_fiatOriginal = displayAmounts.fiatOriginal ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
						
						TruncatableView(fixedHorizontal: true, fixedVertical: true) {
							Text(verbatim: "≈ \(display_fiatOriginal.digits) ")
								.layoutPriority(0)
						} wasTruncated: {
							truncatedText[key] = true
						}
						Text_CurrencyName(currency: display_fiatOriginal.currency, fontTextStyle: .callout)
							.layoutPriority(1)
						Text(" (original)")
							.layoutPriority(1)
							.foregroundColor(.secondary)
						
					} else {
						
						let display_fiatCurrent = displayAmounts.fiatCurrent ??
							Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
					
						TruncatableView(fixedHorizontal: true, fixedVertical: true) {
							Text(verbatim: "≈ \(display_fiatCurrent.digits) ")
								.layoutPriority(0)
						} wasTruncated: {
							truncatedText[key] = true
						}
						Text_CurrencyName(currency: display_fiatCurrent.currency, fontTextStyle: .callout)
							.layoutPriority(1)
						Text(" (now)")
							.layoutPriority(1)
							.foregroundColor(.secondary)
					}
					
					if canShowOriginalFiatValue {
						
						AnimatedClock(state: clockStateBinding(), size: 14, animationDuration: 0.0)
							.padding(.leading, 4)
							.offset(y: 1)
					}
					
				} // </HStack>
			}
			
		} // </VStack>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
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
	
	func paymentPreimage() -> String? {
		
		if let incomingPayment = paymentInfo.payment as? Lightning_kmpIncomingPayment {
			return incomingPayment.preimage.toHex()
		}
		
		if let outgoingPayment = paymentInfo.payment as? Lightning_kmpOutgoingPayment,
		   let lightningPayment = outgoingPayment as? Lightning_kmpLightningOutgoingPayment,
		   let offChain = lightningPayment.status.asOffChain()
		{
			return offChain.preimage.toHex()
		}
		
		return nil
	}
	
	func txId(receivedWith: Lightning_kmpIncomingPayment.ReceivedWith) -> Bitcoin_kmpByteVector32? {
		
		if let newChannel = receivedWith.asNewChannel() {
			
			if let channel = Biz.business.peerManager.getChannelWithCommitments(channelId: newChannel.channelId),
				let active = channel.commitments.active.first
			{
				return active.fundingTxId
			}
			
		} else if let spliceIn = receivedWith.asSpliceIn() {
			
			return spliceIn.txId
		}
		
		return nil
	}
	
	func clockStateBinding() -> Binding<AnimatedClock.ClockState> {
		
		return Binding {
			showOriginalFiatValue ? .past : .present
		} set: { value in
			switch value {
				case .past    : showOriginalFiatValue = true
				case .present : showOriginalFiatValue = false
			}
			showFiatValueExplanation = true
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func exploreTx(_ txId: String, website: BlockchainExplorer.Website) {
		log.trace("exploreTX()")
		
		let txUrlStr = Biz.business.blockchainExplorer.txUrl(txId: txId, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

fileprivate struct DisplayAmounts {
	let bitcoin: FormattedAmount
	let fiatCurrent: FormattedAmount?
	let fiatOriginal: FormattedAmount?
}

fileprivate struct InlineSection<Header: View, Content: View>: View {
	
	let header: Header
	let content: Content
	
	init(
		@ViewBuilder header headerBuilder: () -> Header,
		@ViewBuilder content contentBuilder: () -> Content
	) {
		header = headerBuilder()
		content = contentBuilder()
	}
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				VStack(alignment: HorizontalAlignment.leading, spacing: 12) {
					content
				}
				Spacer(minLength: 0)
			}
			.padding(.vertical, 10)
			.padding(.horizontal, 16)
			.background {
				Color(UIColor.secondarySystemGroupedBackground).cornerRadius(10)
			}
			.padding(.horizontal, 16)
		}
		.padding(.vertical, 16)
	}
}

fileprivate struct InfoGridRowWrapper<KeyColumn: View, ValueColumn: View>: View {
	
	let identifier: String
	let keyColumnWidth: CGFloat
	let keyColumn: KeyColumn
	let valueColumn: ValueColumn
	
	init(
		identifier: String,
		keyColumnWidth: CGFloat,
		@ViewBuilder keyColumn keyColumnBuilder: () -> KeyColumn,
		@ViewBuilder valueColumn valueColumnBuilder: () -> ValueColumn
	) {
		self.identifier = identifier
		self.keyColumnWidth = keyColumnWidth
		self.keyColumn = keyColumnBuilder()
		self.valueColumn = valueColumnBuilder()
	}
	
	@ViewBuilder
	var body: some View {
		
		InfoGridRow(
			identifier: identifier,
			vAlignment: .firstTextBaseline,
			hSpacing: 8,
			keyColumnWidth: keyColumnWidth,
			keyColumnAlignment: .trailing
		) {
			keyColumn
		} valueColumn: {
			valueColumn.font(.callout)
		}
	}
}
