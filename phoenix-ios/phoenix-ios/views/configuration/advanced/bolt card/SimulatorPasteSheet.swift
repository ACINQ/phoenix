import SwiftUI
import PhoenixShared

fileprivate let filename = "SimulatorPasteSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SimulatorPasteSheet: View {
	
	let input: BoltCardInput
	
	@State var jsonInput: String = ""
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
		.onChange(of: jsonInput) { _ in
			jsonInputChanged()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Simulator instructions")
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
			
			Text(
				"""
				The simulator doesn't support NFC. \
				But you can link a card to this wallet for testing.
				"""
			)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			
			content_instructions()
			content_copy()
			content_paste()
			
		} // </VStack>
		.frame(maxWidth: .infinity)
		.padding(.horizontal)
		.padding(.top, 16)
		.padding(.bottom, 32)
	}
	
	@ViewBuilder
	func content_instructions() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			Text("On a real device:")
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("Open Phoenix app (debug build)")
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("Go to: Configuration > Bolt cards")
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("Press and hold \"create new debit card\" button for 3 seconds")
			}
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				bullet()
				Text("A sheet will appear to guide you through the process")
			}
		}
	}
	
	@ViewBuilder
	func content_copy() -> some View {
		
		Grid(
			alignment: Alignment.trailing,
			horizontalSpacing: 8,
			verticalSpacing: 8
		) {
			GridRow {
				Text("Copy simulator's info:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				
				Button {
					copySimInfoToClipboard()
				} label: {
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Text(simulatorInfo())
							.lineLimit(1)
							.truncationMode(.tail)
						Image(systemName: "square.on.square")
					}
				}
				.gridCellAnchor(.leading)
			}
		}
	}
	
	@ViewBuilder
	func content_paste() -> some View {
		
		TextField("Paste JSON output from device here", text: $jsonInput, axis: .vertical)
			.lineLimit(3, reservesSpace: true)
			.padding(.all, 8)
			.background(
				RoundedRectangle(cornerRadius: 8)
					.fill(Color(UIColor.systemBackground))
			)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
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
	// MARK: View Helpers
	// --------------------------------------------------
	
	func simulatorInfo() -> String {
		
		do {
			let jsonData = try JSONEncoder().encode(input)
			return String(data: jsonData, encoding: .utf8) ?? ""
		} catch {
			return ""
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func jsonInputChanged() {
		log.trace("jsonInputChanged()")
		
		do {
			let data = jsonInput.data(using: .utf8)!
			let result = try JSONDecoder().decode(SimulatorBoltCardInput.self, from: data)
			
			importCard(result)
			
		} catch {
			log.debug("Invalid JSON")
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func copySimInfoToClipboard() {
		log.trace("copySimInfoToClipboard()")
		
		UIPasteboard.general.string = simulatorInfo()
	}
	
	func importCard(_ input: SimulatorBoltCardInput) {
		log.trace("importCard()")
		
		let key0_data = Data(fromHex: input.key0)!
		let key0_vector = Bitcoin_kmpByteVector(bytes: key0_data.toKotlinByteArray())
		
		let keySet = BoltCardKeySet(key0: key0_vector)
		
		let chipUid_data = Data(fromHex: input.chipUid)!
		let chipUid_vector = Bitcoin_kmpByteVector(bytes: chipUid_data.toKotlinByteArray())
		
		let cardInfo = BoltCardInfo(
			id: Lightning_kmpUUID.companion.randomUUID(),
			name: "",
			keys: keySet,
			uid: chipUid_vector,
			lastKnownCounter: 0,
			isFrozen: false,
			isArchived: false,
			isReset: false,
			isForeign: false,
			dailyLimit: nil,
			monthlyLimit: nil,
			createdAt: Date.now.toKotlinInstant()
		)
		
		Task { @MainActor in
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				try await cardsDb.saveCard(card: cardInfo)
				smartModalState.close()
				
			} catch {
				log.error("SqliteCardsDb.saveCard(): error: \(error)")
			}
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
