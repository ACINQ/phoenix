import SwiftUI
import PhoenixShared

fileprivate let filename = "ArchiveCardSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ArchiveCardSheet: View {
	
	let card: BoltCardInfo
	let didArchive: (BoltCardInfo) -> Void
	
	@State var isUpdatingCard: Bool = false
	
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
			Text("Archive card")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
			Spacer()
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
			
			Text(
				"""
				Once a card is archived it can never be activated again. \
				The card will remain in your list, but will be moved to the Archived section.
				"""
			)
			
			Text(
				"""
				Use this option if your card is permanantly lost or stolen.
				"""
			)
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				if isUpdatingCard {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				}
				Spacer()
				
				Button {
					cancelButtonTapped()
				} label: {
					Text("Cancel").font(.title3)
				}
				.disabled(isUpdatingCard)
				.padding(.trailing, 24)
				
				Button {
					archiveButtonTapped()
				} label: {
					Text("Archive").font(.title3).foregroundStyle(Color.red)
				}
				.disabled(isUpdatingCard)
			}
			.padding(.top, 16) // extra padding
		}
		.padding(.top, 16)
		.padding(.horizontal)
	}
	
	func cancelButtonTapped() {
		log.trace(#function)
		
		smartModalState.close()
	}
	
	func archiveButtonTapped() {
		log.trace(#function)
		
		isUpdatingCard = true
		Task { @MainActor in
			
			var result: BoltCardInfo? = nil
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				
				// Try to get the most recent version of the card,
				// just in-case any changes were made elsewhere in the system.
				//
				let currentCard = cardsDb.cardForId(cardId: card.id) ?? card
				let updatedCard = currentCard.archivedCopy()
				
				try await cardsDb.saveCard(card: updatedCard)
				result = updatedCard
				
			} catch {
				log.error("SqliteCardsDb.saveCard(): error: \(error)")
			}
			
			self.isUpdatingCard = false
			self.smartModalState.close(animationCompletion: {
				if let result {
					self.didArchive(result)
				}
			})
		}
	}
}
