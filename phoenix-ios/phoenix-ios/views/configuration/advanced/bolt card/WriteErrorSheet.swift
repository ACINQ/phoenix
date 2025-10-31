import SwiftUI
import PhoenixShared

fileprivate let filename = "WriteErrorSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct WriteErrorSheet: View {
	
	enum Context {
		case whileWriting
		case whileResetting
	}
	
	let error: NfcWriter.WriteError
	let context: Context
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Write error")
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 16) {
			
			Text("An error occurred while attempting to write to the NFC tag.")
			
			switch error {
			case .readingNotAvailable:
				Text("NFC capabilities not available on this device.").bold()
				
			case .alreadyStarted:
				Text("An NFC session is already running.").bold()
				
			case .couldNotConnect:
				Text("Could not connect to the NFC tag.").bold()
				Text(
					"""
					Please try again. And be sure to hold the card close \
					to the phone until the writing process completes.
					"""
				)
				
			case .couldNotAuthenticate:
				Text("Could not authenticate with card.").bold()
				switch context {
				case .whileWriting:
					Text(
						"""
						This card is already linked to another wallet. \
						To re-use this card you must first unlink the card. \
						In Phoenix there is an option called "reset physical card" which will unlink it.
						"""
					)
				case .whileResetting:
					Text(
						"""
						This doesn't appear to be the linked card. \
						Perhaps this card is associated with a different wallet, \
						or a different card in this wallet.
						"""
					)
				}
				
				
			case .keySlotsUnavailable:
				Text("Key slots unavailable").bold()
				switch context {
				case .whileWriting:
					Text(
						"""
						This card has been improperly programmed or reset too many times, \
						and it's now impossible to use the card.
						"""
					)
				case .whileResetting:
					Text(
						"""
						An unknown error occurred while attempting to clear the keys from the card. \
						Please try resetting it again. If the problem persists, you may need to \
						destroy the card by cutting it up.
						"""
					)
				}
				
			case .protocolError(let writeStep, let error):
				Text("Protocol error: \(writeStepName(writeStep))").bold()
				Text("Details: \(error.localizedDescription)")
				switch context {
				case .whileWriting:
					Text(
						"""
						The card is **NOT** ready to be used. \
						Please try writing it again.
						"""
					)
				case .whileResetting:
					Text(
						"""
						An unexpected error occurred while attempting to reset the card. \
						Please try resetting it again. If the problem persists, you may need to \
						destroy the card by cutting it up.
						"""
					)
				}
				
			case .scanningTerminated(let nfcError):
				Text("NFC process terminated unexpectedly").bold()
				Text("NFC error: \(nfcError.localizedDescription)")
			}
			
		} // </VStack>
		.padding(.horizontal)
		.padding(.vertical, 16)
	}
	
	func writeStepName(_ writeStep: NfcWriter.WriteStep) -> String {
		switch writeStep {
			case .readChipUid        : return "Read Chip UID"
			case .writeFile2Settings : return "Write File(2) Settings"
			case .writeFile2Data     : return "Write File(2) Data"
			case .writeKey0          : return "Write Key(0)"
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}
