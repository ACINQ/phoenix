import SwiftUI
import PhoenixShared

fileprivate let filename = "DeleteCardSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct DeleteCardSheet: View {
	
	let card: BoltCardInfo
	let didDelete: () -> Void
	
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
			Text("Delete card")
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
				Are you sure you want to delete this card?
				"""
			)
			
			Text(
				"""
				Any payments made with this card will remain in your transaction history, \
				but will no longer be linked with any card.
				"""
			)
			
			if !card.isReset {
				Text(
					"""
					If you still have access to the physical card, \
					it's recommended that you **reset** the card first. \
					This will allow it to be linked again with any wallet.
					"""
				)
			}
			
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
				.padding(.trailing, 24)
				
				Button {
					deleteButtonTapped()
				} label: {
					Text("Delete").font(.title3).foregroundStyle(Color.red)
				}
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
	
	func deleteButtonTapped() {
		log.trace(#function)
		
		isUpdatingCard = true
		Task { @MainActor in
			do {
				let cardsDb = try await Biz.business.databaseManager.cardsDb()
				try await cardsDb.deleteCard(cardId: card.id)
			} catch {
				log.error("SqliteCardsDb.updateCard(): error: \(error)")
			}
			
			self.isUpdatingCard = false
			self.smartModalState.close(animationCompletion: {
				self.didDelete()
			})
		}
	}
}

