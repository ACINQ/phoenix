import SwiftUI
import PhoenixShared

fileprivate let filename = "SimulatorWriteSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SimulatorWriteSheet: View {
	
	@State var hexAddrString: String = ""
	@State var jsonOutput: String = ""
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
		.onChange(of: hexAddrString) { _ in
			hexAddrStringChanged()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Simulator debugging")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
			Spacer()
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 32) {
			
			Text("Link a card to a simulator wallet for testing.")
				.fixedSize(horizontal: false, vertical: true)
			
			content_notes()
			content_address()
			if !jsonOutput.isEmpty {
				content_json()
			}
			
		} // </VStack>
		.frame(maxWidth: .infinity)
		.padding(.horizontal)
		.padding(.top, 16)
		.padding(.bottom, 32)
	}
	
	@ViewBuilder
	func content_notes() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("This will create a new card that is linked to a wallet running on a simulator")
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("Note that simulators do not support background execution")
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text(
					"""
					So to make a payment using the card, the simulator must be open, \
					with Phoenix running in the foreground
					"""
				)
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text(
					"""
					The simulator must be running on a Mac with either Apple Silicon or \
					the T2 security chip (to receive push notifications)
					"""
				)
			}
		}
	}
	
	@ViewBuilder
	func content_address() -> some View {
		
		Grid(
			alignment: Alignment.trailing,
			horizontalSpacing: 8,
			verticalSpacing: 8
		) {
			GridRow {
				Text("Simulator's HEX address:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				
				TextField("Paste here", text: $hexAddrString)
					.padding(.all, 8)
					.background(
						RoundedRectangle(cornerRadius: 8)
							.fill(Color(UIColor.systemBackground))
					)
					.overlay(
						RoundedRectangle(cornerRadius: 8)
							.stroke(Color.textFieldBorder, lineWidth: 1)
					)
					.gridCellAnchor(.leading)
			}
		}
	}
	
	@ViewBuilder
	func content_json() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			Text("Copy and paste into simulator:")
			Button {
				copyJsonToClipboard()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text(jsonOutput)
					Image(systemName: "square.on.square")
				}
			}
		}
	}
	
	@ViewBuilder
	func bullet() -> some View {
			
		Image(systemName: "circlebadge.fill")
			.imageScale(.small)
			.font(.system(size: 10))
			.foregroundStyle(.tertiary)
			.padding(.leading, 4)
			.padding(.trailing, 8)
			.offset(y: -3)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func hexAddrStringChanged() {
		log.trace("hexAddrStringChanged()")
		
		let trimmed = hexAddrString.trimmingCharacters(in: .whitespacesAndNewlines)
		if trimmed.count == 8, let hexAddrData = Data(fromHex: trimmed) {
			
			let sanitizedHexAddr = hexAddrData.toHex(options: .lowerCase)
			writeToNfcCard(sanitizedHexAddr)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func writeToNfcCard(_ hexAddr: String) {
		log.trace("writeToNfcCard()")
		
		let keys = BoltCardKeySet.companion.random()
		
		let baseUrl = URL(string: "https://phoenix.deusty.com/v1/pub/lnurlw/info?id=\(hexAddr)")!
		let template = Ndef.Template(baseUrl: baseUrl)!
		
		log.debug("template.url: \(template.urlString)")
		log.debug("template.piccDataOffset: \(template.piccDataOffset)")
		log.debug("template.cmacOffset: \(template.cmacOffset)")
		
		let input = NfcWriter.WriteInput(
			template    : template,
			key0        : keys.key0_bytes,
			piccDataKey : keys.piccDataKey_bytes,
			cmacKey     : keys.cmacKey_bytes
		)

		NfcWriter.shared.writeCard(input) { (result: Result<NfcWriter.WriteOutput, NfcWriter.WriteError>) in
			
			switch result {
			case .failure(let error):
				log.debug("error: \(error)")
				showWriteErrorSheet(error)
				
			case .success(let output):
				log.debug("output.chipUid: \(output.chipUid.toHex())")
				saveNewCard(keys, output)
			}
		}
	}
	
	func saveNewCard(
		_ keys: BoltCardKeySet,
		_ output: NfcWriter.WriteOutput
	) {
		
		// Conversion madness: [UInt8] -> Data -> ByteArray -> ByteVector
		let uidByteArray = Helper.dataFromBytes(bytes: output.chipUid).toKotlinByteArray()
		let uid = Bitcoin_kmpByteVector(bytes: uidByteArray)
		
		let cardInfo = BoltCardInfo(name: "", keys: keys, uid: uid, isForeign: true)
		
		Task { @MainActor in
			do {
				try await Biz.business.cardsManager.saveCard(card: cardInfo)
				
				let rawOutput = SimulatorBoltCardInput(
					key0: keys.key0_bytes.toHex(options: .lowerCase),
					chipUid: output.chipUid.toHex(options: .lowerCase)
				)
				let jsonData = try JSONEncoder().encode(rawOutput)
				jsonOutput = String(data: jsonData, encoding: .utf8) ?? ""
				
			} catch {
				log.error("CardsManager.saveCard(): error: \(error)")
			}
		}
	}
	
	func showWriteErrorSheet(_ error: NfcWriter.WriteError) {
		log.trace("showWriteErrorSheet()")
		
		var shouldIgnoreError = false
		if case .scanningTerminated(let nfcError) = error {
			shouldIgnoreError = nfcError.isIgnorable()
		}
		
		guard !shouldIgnoreError else {
			log.debug("showWriteErrorSheet(): ignoring standard user error")
			return
		}
		
		smartModalState.close(animationCompletion: {
			smartModalState.display(dismissable: true) {
				WriteErrorSheet(error: error, context: .whileWriting)
			}
		})
	}
	
	func copyJsonToClipboard() {
		log.trace("copyJsonToClipboard()")
		
		UIPasteboard.general.string = jsonOutput
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}