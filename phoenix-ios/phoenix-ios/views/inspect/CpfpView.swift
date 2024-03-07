import SwiftUI
import PhoenixShared

fileprivate let filename = "CpfpSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct MinerFeeCPFP {
	
	/// The effective feerate of the bumped transaction(s)
	let effectiveFeerate: Lightning_kmpFeeratePerByte
	
	/// The feerate to use for the CPFP transaction itself.
	/// It should be significantly higher than the effectiveFeerate since
	/// it's only purpose is to increase the "effective" feerate of parent transaction(s).
	let cpfpTxFeerate: Lightning_kmpFeeratePerByte
	
	/// The miner fee that will be incurred to execute the CPFP transaction.
	let minerFee: Bitcoin_kmpSatoshi
}

enum CpfpError: Error {
	case feeBelowMinimum
	case feeNotIncreased
	case noChannels
	case errorThrown(message: String)
	case executeError(problem: SpliceOutProblem)
}


struct CpfpView: View {
	
	let location: PaymentView.Location
	let onChainPayment: Lightning_kmpOnChainOutgoingPayment
	
	@State var minerFeeInfo: MinerFeeCPFP?
	@State var satsPerByte: String = ""
	@State var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError> = .failure(.emptyInput)
	@State var mempoolRecommendedResponse: MempoolRecommendedResponse? = nil
	
	@State var cpfpError: CpfpError? = nil
	@State var txAlreadyMined: Bool = false
	@State var spliceInProgress: Bool = false
	
	@State var explicitlySelectedPriority: MinerFeePriority? = nil
	
	enum Field: Hashable {
		case satsPerByteTextField
	}
	@FocusState private var focusedField: Field?
	
	enum NavBarButtonWidth: Preference {}
	let navBarButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<NavBarButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var navBarButtonWidth: CGFloat? = nil
	
	enum PriorityBoxWidth: Preference {}
	let priorityBoxWidthReader = GeometryPreferenceReader(
		key: AppendValue<PriorityBoxWidth>.self,
		value: { [$0.size.width] }
	)
	@State var priorityBoxWidth: CGFloat? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		switch location {
		case .sheet:
			main()
				.navigationTitle(NSLocalizedString("Accelerate Transactions", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.navigationBarHidden(true)
			
		case .embedded:
			main()
				.navigationTitle(NSLocalizedString("Accelerate Transactions", comment: "Navigation bar title"))
				.navigationBarTitleDisplayMode(.inline)
				.background(
					Color.primaryBackground.ignoresSafeArea(.all, edges: .bottom)
				)
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			header()
			ScrollView {
				content()
			}
		}
		.onChange(of: satsPerByte) { _ in
			satsPerByteChanged()
		}
		.onChange(of: mempoolRecommendedResponse) { _ in
			mempoolRecommendedResponseChanged()
		}
		.task {
			await fetchMempoolRecommendedFees()
		}
		.task {
			await monitorBlockchain()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		if case .sheet(let closeAction) = location {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Button {
					presentationMode.wrappedValue.dismiss()
				} label: {
					Image(systemName: "chevron.backward")
						.imageScale(.medium)
						.font(.title3.weight(.semibold))
				}
				.read(navBarButtonWidthReader)
				.frame(width: navBarButtonWidth)
				
				Spacer(minLength: 0)
				Text("Accelerate transaction")
					.font(.headline)
					.fontWeight(.medium)
					.lineLimit(1)
				Spacer(minLength: 0)
				
				Button {
					closeAction()
				} label: {
					Image(systemName: "xmark") // must match size of chevron.backward above
						.imageScale(.medium)
						.font(.title3)
				}
				.read(navBarButtonWidthReader)
				.frame(width: navBarButtonWidth)
				
			} // </HStack>
			.padding()
			.assignMaxPreference(for: navBarButtonWidthReader.key, to: $navBarButtonWidth)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 20) {
			
			Spacer().frame(height: 25)
			
			Text(
				"""
				You can make all your unconfirmed transactions use a higher effective feerate \
				to encourage miners to favour your payments.
				"""
			)
			.font(.callout)
			
			priorityBoxes()
				.padding(.top)
				.padding(.bottom)
			
			minerFeeFormula()
				.padding(.bottom)
			
			footer()
		}
		.padding(.horizontal, 40)
	}
	
	@ViewBuilder
	func priorityBoxes() -> some View {
		
		if #available(iOS 16.0, *) {
			priorityBoxes_ios16()
		} else {
			priorityBoxes_ios15()
		}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func priorityBoxes_ios16() -> some View {
		
		ViewThatFits {
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					
				}
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBoxes_ios15() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_economy()
				priorityBox_low()
			}
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_medium()
				priorityBox_high()
			}
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBox_economy() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.none)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 1+ days")
			}
		} label: {
			Text("No Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.none),
			tapped: { priorityTapped(.none) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_low() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.low)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 1 hour")
			}
		} label: {
			Text("Low Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.low),
			tapped: { priorityTapped(.low) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_medium() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.medium)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 30 minutes")
			}
		} label: {
			Text("Medium Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.medium),
			tapped: { priorityTapped(.medium) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_high() -> some View {
		
		GroupBox {
			VStack(alignment: HorizontalAlignment.center, spacing: 4) {
				Text("\(satsPerByteString(.high)) sats/vByte")
					.font(.subheadline)
					.foregroundColor(.secondary)
				Text("≈ 10 minutes")
			}
		} label: {
			Text("High Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.high),
			tapped: { priorityTapped(.high) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func minerFeeFormula() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			satsPerByteTextField()
			minerFeeAmounts()
		}
	}
	
	@ViewBuilder
	func satsPerByteTextField() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("", text: satsPerByteStyler().amountProxy)
					.keyboardType(.numberPad)
					.focused($focusedField, equals: .satsPerByteTextField)
					.frame(maxWidth: 40)
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(isInvalidSatsPerByte ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
			)
			
			Text(verbatim: "sats/vByte")
				.font(.callout)
				.foregroundColor(.secondary)
				.padding(.leading, 4)
		}
	}
	
	@ViewBuilder
	func minerFeeAmounts() -> some View {
		
		Group {
			if case .failure = parsedSatsPerByte {
				
				Text("Select a fee to see the amount.")
					.foregroundColor(.secondary)
				
			} else if minerFeeInfo == nil {
				
				Text("Calculating amount…")
					.foregroundColor(cpfpError == nil ? .secondary : .clear)
				
			} else {
				
				let (btc, fiat) = minerFeeStrings()
				Text("You will pay \(btc.string) (≈ \(fiat.string)) to the Bitcoin miners.")
			}
		} // </Group>
		.font(.callout)
		.multilineTextAlignment(.center)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			
			Button {
				executePayment()
			} label: {
				Label("Pay", systemImage: "paperplane")
					.font(.title3)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.disabled(minerFeeInfo == nil || cpfpError != nil || txAlreadyMined || spliceInProgress)
			
			if txAlreadyMined {
				
				Text("Good news! Your transaction has been mined!")
					.foregroundColor(.appPositive)
					.font(.callout)
					.multilineTextAlignment(.center)
				
			} else if let cpfpError {
				
				Group {
					switch cpfpError {
					case .feeBelowMinimum:
						if let mrr = mempoolRecommendedResponse {
							Text("Feerate below minimum allowed by mempool: \(satsPerByteString(mrr.minimumFee))")
						} else {
							Text("Feerate below minimum allowed by mempool.")
						}
					case .feeNotIncreased:
						Text(
							"""
							This feerate is below what your transactions are already using. \
							It should be higher to have any effects.
							"""
						)
						
					case .noChannels:
						Text("No available channels. Please check your internet connection.")
						
					case .errorThrown(let message):
						Text("Unexpected error: \(message)")
						
					case .executeError(let problem):
						Text(problem.localizedDescription())
					}
				} // </Group>
				.font(.callout)
				.foregroundColor(.appNegative)
				.multilineTextAlignment(.center)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	func fetchMempoolRecommendedFees() async {
		
		for try await response in MempoolMonitor.shared.stream() {
			mempoolRecommendedResponse = response
			if Task.isCancelled {
				return
			}
		}
	}
	
	func checkConfirmations() async {
		log.trace("checkConfirmations()")
		
		do {
			let result = try await Biz.business.electrumClient.kotlin_getConfirmations(txid: onChainPayment.txId)

			let confirmations = result?.intValue ?? 0
			log.debug("checkConfirmations(): => \(confirmations)")

			if confirmations > 0 {
				self.txAlreadyMined = true
			}
		} catch {
			log.error("electrumClient.getConfirmations(): \(error)")
		}
	}
	
	func monitorBlockchain() async {
		log.trace("monitorBlockchain()")
		
		for await notification in Biz.business.electrumClient.notificationsSequence() {
			
			if notification is Lightning_kmpHeaderSubscriptionResponse {
				// A new block was mined !
				// Check to see if our pending transaction was included in the block.
				await checkConfirmations()
			} else {
				log.debug("monitorBlockchain(): notification =!= HeaderSubscriptionResponse")
			}
			
			if Task.isCancelled {
				log.debug("monitorBlockchain(): Task.isCancelled")
				break
			} else {
				log.debug("monitorBlockchain(): Waiting for next electrum notification...")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isInvalidSatsPerByte: Bool {
		switch parsedSatsPerByte {
		case .success(_):
			return false
			
		case .failure(let reason):
			switch reason {
				case .emptyInput   : return false
				case .invalidInput : return true
			}
		}
	}
	
	func satsPerByteStyler() -> TextFieldNumberStyler {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .decimal
		
		return TextFieldNumberStyler(
			formatter: formatter,
			amount: $satsPerByte,
			parsedAmount: $parsedSatsPerByte,
			userDidEdit: userDidEditSatsPerByteField
		)
	}
	
	func satsPerByte(_ priority: MinerFeePriority) -> (Double, String)? {
		
		guard let mempoolRecommendedResponse else {
			return nil
		}
		
		let doubleValue = mempoolRecommendedResponse.feeForPriority(priority)
		let stringValue = satsPerByteString(doubleValue)
		
		return (doubleValue, stringValue)
	}
	
	func satsPerByteString(_ value: Double) -> String {
		
		let nf = NumberFormatter()
		nf.numberStyle = .decimal
		nf.minimumFractionDigits = 0
		nf.maximumFractionDigits = 1
		
		return nf.string(from: NSNumber(value: value)) ?? "?"
	}
	
	func satsPerByteString(_ priority: MinerFeePriority) -> String {
		
		let tuple = satsPerByte(priority)
		return tuple?.1 ?? "?"
	}
	
	func isPriorityDisabled() -> Bool {
		
		return (mempoolRecommendedResponse == nil)
	}
	
	func isPrioritySelected(_ priority: MinerFeePriority) -> Bool {
		
		guard let mempoolRecommendedResponse else {
			return false
		}
		
		if let explicitlySelectedPriority {
			return explicitlySelectedPriority == priority
		}
			
		guard let amount = try? parsedSatsPerByte.get() else {
			return false
		}
		
		switch priority {
			case .none:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.none)
			
			case .low:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.low) &&
						 amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.none)
			
			case .medium:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.medium) &&
						 amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.high)
			
			case .high:
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(.high) &&
						 amount.doubleValue != mempoolRecommendedResponse.feeForPriority(.medium)
		}
	}
	
	func minerFeeStrings() -> (FormattedAmount, FormattedAmount) {
		
		guard let minerFeeInfo else {
			let btc = Utils.unknownBitcoinAmount(bitcoinUnit: currencyPrefs.bitcoinUnit)
			let fiat = Utils.unknownFiatAmount(fiatCurrency: currencyPrefs.fiatCurrency)
			return (btc, fiat)
		}
		
		let btc = Utils.formatBitcoin(currencyPrefs, sat: minerFeeInfo.minerFee)
		let fiat = Utils.formatFiat(currencyPrefs, sat: minerFeeInfo.minerFee)
		return (btc, fiat)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func priorityTapped(_ priority: MinerFeePriority) {
		log.trace("priorityTapped()")
		
		guard let tuple = satsPerByte(priority) else {
			return
		}
		
		explicitlySelectedPriority = priority
		parsedSatsPerByte = .success(NSNumber(value: tuple.0))
		satsPerByte = tuple.1
	}
	
	func userDidEditSatsPerByteField() {
		log.trace("userDidEditSatsPerByteField()")
		
		explicitlySelectedPriority = nil
	}
	
	func satsPerByteChanged() {
		log.trace("satsPerByteChanged(): \(satsPerByte)")
		
		minerFeeInfo = nil
		guard
			let satsPerByte_number = try? parsedSatsPerByte.get(),
			let peer = Biz.business.peerManager.peerStateValue()
		else {
			return
		}
		
		if let mrr = mempoolRecommendedResponse, mrr.minimumFee > satsPerByte_number.doubleValue {
			cpfpError = .feeBelowMinimum
			return
		}
		cpfpError = nil
		
		let originalSatsPerByte = satsPerByte
		
		let satsPerByte_satoshi = Bitcoin_kmpSatoshi(sat: satsPerByte_number.int64Value)
		let effectiveFeerate = Lightning_kmpFeeratePerByte(feerate: satsPerByte_satoshi)
		let effectiveFeeratePerKw = Lightning_kmpFeeratePerKw(feeratePerByte: effectiveFeerate)
		
		Task { @MainActor in
			
			var pair: KotlinPair<Lightning_kmpFeeratePerKw, Lightning_kmpChannelCommand.CommitmentSpliceFees>? = nil
			do {
				pair = try await peer.estimateFeeForSpliceCpfp(
					channelId: onChainPayment.channelId,
					targetFeerate: effectiveFeeratePerKw
				)
			} catch {
				log.error("Error: \(error)")
			}
			
			guard self.satsPerByte == originalSatsPerByte else {
				// Ignore: user has changed to a different rate
				return
			}
				
			if let pair {
				
				let cpfpFeeratePerKw: Lightning_kmpFeeratePerKw = pair.first!
				let cpfpFeerate = Lightning_kmpFeeratePerByte(feeratePerKw: cpfpFeeratePerKw)
				
				let spliceFees: Lightning_kmpChannelCommand.CommitmentSpliceFees = pair.second!
				let minerFee: Bitcoin_kmpSatoshi = spliceFees.miningFee
					
				// From the docs (in lightning-kmp):
				//
				// > if the output feerate is equal to the input feerate then the cpfp is useless
				// > and should not be attempted.
				//
				// So we check to ensure the output is larger than the input.
				
				let input: Int64 = effectiveFeerate.feerate.sat
				let output: Int64 = cpfpFeerate.feerate.sat
				
				log.debug("effectiveFeerate(\(input)) => cpfpFeerate(\(output))")
				
				if Double(output) > (Double(input) * 1.1) {
					self.minerFeeInfo = MinerFeeCPFP(
						effectiveFeerate: effectiveFeerate,
						cpfpTxFeerate: cpfpFeerate,
						minerFee: minerFee
					)
				} else {
					log.error("Error: peer.estimateFeeForSpliceCpfp() => fee not increased")
					self.cpfpError = .feeNotIncreased
				}
				
			} else {
				log.error("Error: peer.estimateFeeForSpliceCpfp() => nil")
				self.cpfpError = .noChannels
			}
			
		} // </Task>
	}
	
	func mempoolRecommendedResponseChanged() {
		log.trace("mempoolRecommendedResponseChanged()")
		
		// The UI will change, so we need to reset the geometry measurements
		priorityBoxWidth = nil
		
		// Might need to display an error (if minimumFee increased)
		satsPerByteChanged()
	}
	
	func executePayment() {
		log.trace("executePayment()")
		
		guard
			let minerFeeInfo = minerFeeInfo,
			let peer = Biz.business.peerManager.peerStateValue()
		else {
			return
		}
		
		spliceInProgress = true
		Task { @MainActor in
			
			do {
				let feeratePerByte = minerFeeInfo.cpfpTxFeerate
				let feeratePerKw = Lightning_kmpFeeratePerKw(feeratePerByte: feeratePerByte)
				
				let response = try await peer.spliceCpfp(
					channelId: onChainPayment.channelId,
					feerate: feeratePerKw
				)
				
				if let problem = SpliceOutProblem.fromResponse(response) {
					self.cpfpError = .executeError(problem: problem)
				} else {
					switch location {
					case .sheet(let closeAction):
						closeAction()
					case .embedded(let popTo):
						popTo(.TransactionsView)
						self.presentationMode.wrappedValue.dismiss()
					}
				}
				
			} catch {
				log.error("peer.spliceCpfp(): error: \(error)")
				self.spliceInProgress = false
			}
		} // </Task>
	}
}
