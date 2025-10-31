import SwiftUI

fileprivate let filename = "BoltCardsHelp"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct BoltCardsHelp: View {
	
	@Binding var isShowing: Bool
	
	@ViewBuilder
	var body: some View {
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
			Spacer()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		// close button
		// (required for landscapse mode, where swipe-to-dismiss isn't possible)
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				close()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
		} // </HStack>
		.padding()
	}
	
	@ViewBuilder
	func content() -> some View {
		
		ScrollView(.vertical) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 32) {
				content_intro()
				content_whereToBuy()
				content_howDoesItWork()
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: .center)
			.padding(.horizontal)
			.padding(.horizontal)
			.padding(.bottom)
		}
	}
	
	@ViewBuilder
	func content_intro() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 24) {
			
			Text("Bolt Card")
				.font(.title)
			
			Text("Bitcoin payments over the lightning network with a contactless payment card.")
			
			Text(
				"""
				You can link multiple debit cards to your wallet. \
				Set custom spending limits per card, and freeze a card at anytime.
				"""
			)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func content_whereToBuy() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			Text("Where can I buy bolt cards ?")
				.font(.headline)
			
			Text(
				"""
				What you need are blank NFC "NTAG 424 DNA" cards. \
				You can buy them from many different vendors.
				"""
			)
			.foregroundStyle(.secondary)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			.padding(.bottom, 8)
			
			Text(" • [CoinCorner.com](https://www.coincorner.com/BuyTheBoltCard)")
			Text(" • [Laser Eyes Cards](https://lasereyes.cards/)")
			Text(" • [PlebTag](https://plebtag.com/)")
			Text(" • [Yanabu Bolt Card - Korea](https://marpple.shop/kr/yanabu/products/13356281)")
			Text(" • [Bolt Ring](https://bitcoin-ring.com/)")
			Text(" • [NFC.cards](https://nfc.cards/en/white-cards/46-nfc-card-ntag424-dna.html)")
			Text(" • [ZipNFC.com](https://zipnfc.com/nfc-pvc-card-credit-card-size-ntag424-dna.html)")
			Text(" • [Hirsch](https://shop.hirschsecure.com/products/printed-nxp-ntag-424-dna-tag-5-pack)")
		}
	}
	
	@ViewBuilder
	func content_howDoesItWork() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 24) {
			
			Text("How does Bolt card work ?")
				.font(.headline)
			
			Text(
				"""
				The NFC card is programmed with a BLIP XX address and a set of secure keys. \
				The card then produces the address plus two unique hashes that change each \
				time the card is scanned.
				"""
			)
			Text(
				"""
				The merchant can use these values to make a one time request to your wallet. \
				After that, the card must be tapped again to get fresh values.
				"""
			)
			Text(
				"""
				Your wallet verifies the card is not frozen, and checks the payment amount \
				against any daily/monthly spending limits you may have configured.
				"""
			)
		} // </VStack>
	}
		
	func close() {
		log.trace(#function)
		
		isShowing = false
	}
}
