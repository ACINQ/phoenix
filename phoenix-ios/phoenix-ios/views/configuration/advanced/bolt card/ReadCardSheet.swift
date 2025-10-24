import SwiftUI
import PhoenixShared
import CoreNFC
import DnaCommunicator

fileprivate let filename = "ReadCardSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ReadCardSheet: View {
	
	let result: Result<NFCNDEFMessage, NfcReader.ReadError>
	
	@State var scannedUri: URL? = nil
	@State var scannedText: String? = nil
	@State var scannedUnknown: Bool = false
	
	@State var errorMessage: String? = nil
	
	@State var isBoltCard: Bool = false
	@State var matchingCard: BoltCardInfo? = nil
	@State var piccDataInfo: Ntag424.PiccDataInfo? = nil
	
	@State var didAppear: Bool = false
	
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
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Read card")
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
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			if let link = scannedUri?.absoluteString {
				
				let sanitizedLink = preventAutoHyphenation(link)
				Button {
					openScannedUri()
				} label: {
					Text(sanitizedLink)
						.multilineTextAlignment(.leading)
				}
					
			} else if let scannedText {
				
				let sanitizedText = preventAutoHyphenation(scannedText)
				Text(sanitizedText)
					.contextMenu {
						Button {
							copyScannedText()
						} label: {
							Text("Copy")
						}
					} // </contextMenu>
				
			} else if scannedUnknown {
				Text("Scanned NDEF tag with unknown type")
				
			} else if let errorMessage {
				Text(errorMessage)
					.multilineTextAlignment(.leading)
					.foregroundStyle(Color.red)
			}
			
			if isBoltCard {
				boltCardDetails()
					.padding(.top, 16)
			}
		}
		.frame(maxWidth: .infinity)
		.multilineTextAlignment(.center)
		.padding(.horizontal)
		.padding(.top, 16)
		.padding(.bottom, 32)
	}
	
	@ViewBuilder
	func boltCardDetails() -> some View {
		
		if let matchingCard, let piccDataInfo {
			boltCardDetails(matchingCard, piccDataInfo)
		} else {
			Text("Card not associated with this wallet.")
		}
	}
	
	@ViewBuilder
	func boltCardDetails(_ matchingCard: BoltCardInfo, _ piccDataInfo: Ntag424.PiccDataInfo) -> some View {
		
		Grid(
			alignment: Alignment.trailing,
			horizontalSpacing: 8,
			verticalSpacing: 8
		) {
			GridRow {
				HStack(spacing: 0) {
					Text("Bolt Card:").bold()
					Spacer()
				}
				.gridCellColumns(2)
				.gridCellAnchor(.leading)
			}
			GridRow {
				Text(" - Name:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text(matchingCard.sanitizedName)
					.gridCellAnchor(.leading)
			}
			GridRow {
				Text(" - Status:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text(cardStatus(matchingCard))
					.gridCellAnchor(.leading)
			}
		#if DEBUG && false
			// For testing: make sure `func updateLastKnownCounter` is working properly.
			GridRow {
				Text(" - LastKnownCounter:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text(matchingCard.lastKnownCounter.description)
					.gridCellAnchor(.leading)
			}
		#endif
			GridRow {
				HStack(spacing: 0) {
					Text("Picc Data:").bold()
					Spacer()
				}
				.gridCellColumns(2)
				.gridCellAnchor(.leading)
			}
			GridRow {
				Text(" - UID:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text(piccDataInfo.uid.toHex(.upperCase))
					.gridCellAnchor(.leading)
			}
			GridRow {
				Text(" - Counter:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text(piccDataInfo.counter.description)
					.gridCellAnchor(.leading)
			}
			GridRow {
				HStack(spacing: 0) {
					Text("Message Authentication Code:").bold()
					Spacer()
				}
				.gridCellColumns(2)
				.gridCellAnchor(.leading)
			}
			GridRow {
				Text(" - Verified:")
					.foregroundStyle(.secondary)
					.gridCellAnchor(.trailing)
				Text("True")
					.gridCellAnchor(.leading)
			}
		}
		.padding()
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func preventAutoHyphenation(_ text: String) -> String {
		
		// The URL is long because of the query parameters.
		// When SwiftUI displays long text, it automatically adds
		// hyphen characters at the end of some lines.
		//
		// E.g.
		// id=3fabbe50&picc_data=FB9B4202A7-  <- added hyphen
		// C37842120BE2D...
		//
		// I don't like this. And there's a simple way to prevent it.
		// You just add zero-width characters in-between every character
		// in the string.
		//
		// https://stackoverflow.com/q/78208090
		//
		
		return text.map({ String($0) }).joined(separator: "\u{200B}")
	}
	
	func cardStatus(_ card: BoltCardInfo) -> String {
		
		if card.isArchived {
			return String(localized: "Frozen (archived)")
		} else if card.isFrozen {
			return String(localized: "Frozen")
		} else {
			return String(localized: "Active")
		}
	}

	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onAppear() {
		log.trace(#function)
		
		guard !didAppear else {
			log.debug("onAppear(): ignoring: didAppear is true")
			return
		}
		didAppear = true
		
		switch result {
		case .failure(let failure):
			switch failure {
			case .readingNotAvailable:
				errorMessage = "NFC cababilities not available on this device."
			case .alreadyStarted:
				errorMessage = "An NFC session is already running."
			case .errorReadingTag:
				errorMessage = "Error reading NDEF tag."
			case .scanningTerminated(let nfcError):
				errorMessage = "NFC reader error: \(nfcError.localizedDescription)"
			}
		case .success(let result):
			log.debug("NFCNDEFMessage: \(result)")
			
			var detectedUri: URL? = nil
			var detectedText: String? = nil
			var detectedUnknown: Bool = false
			
			result.records.forEach { payload in
				if let uri = payload.wellKnownTypeURIPayload() {
					log.debug("found uri = \(uri)")
					
					if detectedUri == nil {
						detectedUri = uri
					}
					
				} else if let text = payload.wellKnownTypeTextPayload().0 {
					log.debug("found text = \(text)")
					
					if detectedText == nil {
						detectedText = text
					}
					
				} else {
					log.debug("found tag with unknown type")
					detectedUnknown = true
					
				}
			}
			
			if let detectedUri {
				scannedUri = detectedUri
				let result = Ntag424.extractQueryItems(url: detectedUri)
				if case .success(let queryItems) = result {
					isBoltCard = true
					tryMatchCard(queryItems)
				}
				
			} else if let detectedText {
				scannedText = detectedText
				let result = Ntag424.extractQueryItems(text: detectedText)
				if case .success(let queryItems) = result {
					isBoltCard = true
					tryMatchCard(queryItems)
				}
				
			} else if detectedUnknown {
				scannedUnknown = true
				
			} else {
				errorMessage = "No URI detected in NFC tag"
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func tryMatchCard(_ queryItems: Ntag424.QueryItems) {
		log.trace(#function)
		
		Task {
			let cardsDb = try await Biz.business.databaseManager.cardsDb()
			let cards: [BoltCardInfo] = cardsDb.cardsListValue
			
			var matchingCard: BoltCardInfo? = nil
			var piccDataInfo: Ntag424.PiccDataInfo? = nil
			
			for card in cards {
				
				let keySet = Ntag424.KeySet(
					piccDataKey : card.keys.piccDataKey_data,
					cmacKey     : card.keys.cmacKey_data
				)
				let result = Ntag424.extractPiccDataInfo(
					piccData : queryItems.piccData,
					cmac     : queryItems.cmac,
					keySet   : keySet
				)
				
				switch result {
				case .failure(let err):
					log.debug("card[\(card.id)]: err: \(err)")
					
				case .success(let result):
					log.debug("card[\(card.id)]: success")
					
					matchingCard = card
					piccDataInfo = result
					break
				}
			}
			
			guard let matchingCard, let piccDataInfo else {
				return
			}
			
			DispatchQueue.main.async { [matchingCard, piccDataInfo] in
				self.matchingCard = matchingCard
				self.piccDataInfo = piccDataInfo
			}
			
			updateLastKnownCounter(matchingCard, piccDataInfo.counter)
		}
	}
	
	/// While we're here, we might as well take advantage, and update the card's `lastKnownCounter`.
	/// Technically this could be considered a safety mechanism too.
	/// For example, if you worry somebody may have scanned your card without your permission.
	///
	func updateLastKnownCounter(_ matchingCard: BoltCardInfo, _ lastKnownCounter: UInt32) {
		log.trace("updateLastKnownCounter(\(lastKnownCounter))")
		
		Task { @MainActor in
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				
				let currentCard = cardsDb.cardForId(cardId: matchingCard.id) ?? matchingCard
				let updatedCounter = max(currentCard.lastKnownCounter, lastKnownCounter)
				let updatedCard = currentCard.withUpdatedLastKnownCounter(updatedCounter)
				
				try await cardsDb.saveCard(card: updatedCard)
			} catch {
				log.debug("SqliteCardsDb.saveCard(): error: \(error)")
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func openScannedUri() {
		log.trace(#function)
		
		guard let uri = scannedUri else {
			return
		}
		
		if UIApplication.shared.canOpenURL(uri) {
			UIApplication.shared.open(uri)
		}
	}
	
	func copyScannedText() {
		log.trace(#function)
		
		if let scannedText {
			UIPasteboard.general.string = scannedText
		}
	}
	
	func closeButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
}
