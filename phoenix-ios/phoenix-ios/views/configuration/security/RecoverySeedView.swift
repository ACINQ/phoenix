import SwiftUI
import PhoenixShared

struct RecoverySeedView : View {
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var mnemonics: [String] = []
	
	var body: some View {
		
		MVIView({ $0.recoveryPhraseConfiguration() }, onModel: { change in
			isDecrypting = change.newModel is RecoveryPhraseConfiguration.ModelDecrypting
			
			if let decrypted = change.newModel as? RecoveryPhraseConfiguration.ModelDecrypted {
				revealSeed = true
				mnemonics = decrypted.mnemonics
			} else {
				revealSeed = false
				mnemonics = []
			}
			
		}) { (model, postIntent) in
			
			ScrollView(.vertical, showsIndicators: true) {
				
				VStack(alignment: .leading, spacing: 40) {
					Text(
						"The backup phrase, known as a seed, is a list of 12 english words. " +
						"It allows you to recover full access to your funds if needed."
					)
					
					Text(
						"Only you alone possess this seed. Keep it private."
					)
					.fontWeight(.bold)
					
					Group {
						Text(
							"Do not share this seed with anyone. "
						)
						.fontWeight(.bold)
						+
						Text(
							"Beware of phishing. The developers of Phoenix will never ask for your seed."
						)
					}
					
					Group {
						Text(
							"Do not lose this seed. "
						)
						.fontWeight(.bold)
						+
						Text(
							"Save it somewhere safe (not on this phone). " +
							"If you lose your seed and your phone, you've lost your funds."
						)
					}
					
					Button {
						print("Button click")
						postIntent(RecoveryPhraseConfiguration.IntentDecrypt())
					//	isDecrypting = true
					//	revealSeed = true
						
						
					} label: {
						HStack {
							Image(systemName: "key")
							.imageScale(.small)

							Text("Display seed")
						}
					}.disabled(isDecrypting)
					
				} // </VStack>
				.padding(.top, 40)
				.padding([.leading, .trailing], 30)
				
			} // </ScrollView>
			.sheet(isPresented: $revealSeed) {
				RecoverySeedReveal(mnemonics: $mnemonics)
			}
			.navigationBarTitle("Recovery Seed", displayMode: .inline)
		}
	}
}

struct RecoverySeedReveal: View {
	
	@Binding var mnemonics: [String]
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	var body: some View {
		
		VStack {
			
			Spacer()
			
			Text("KEEP THIS SEED SAFE.").font(.title2).padding(.bottom, 2)
			Text("DO NOT SHARE.").font(.title2)
			
			Spacer()
			
			HStack {
				Spacer()
				
				VStack {
					ForEach(0..<6, id: \.self) { idx in
						Text("#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(0..<6, id: \.self) { idx in
						Text(mnemonic(idx))
							.font(.headline)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 4) // boost spacing a wee bit
				
				Spacer()
				
				VStack {
					ForEach(6..<12, id: \.self) { idx in
						Text("#\(idx + 1) ")
							.font(.headline)
							.foregroundColor(.secondary)
							.padding(.bottom, 2)
					}
				}
				.padding(.trailing, 2)
				
				VStack(alignment: .leading) {
					ForEach(6..<12, id: \.self) { idx in
						Text(mnemonic(idx))
							.font(.headline)
							.padding(.bottom, 2)
					}
				}
				
				Spacer()
			}
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			Spacer()
			Spacer()
			
			Text("BIP39 seed with standard BIP85 derivation path")
				.font(.footnote)
				.foregroundColor(.secondary)
			
		}
		.padding(.top, 20)
		.padding([.leading, .trailing], 30)
		.padding(.bottom, 20)
	}
}

class RecoverySeedView_Previews: PreviewProvider {
	static let mockModel = RecoveryPhraseConfiguration.ModelAwaiting()

	static let fakeMnemonics = [
		"witch", "collapse", "practice", "feed", "shame", "open",
		"despair", "creek", "road", "again", "ice", "least"
	]
	
	static var previews: some View {
		mockView(RecoverySeedView()).previewDevice("iPhone 11")
	}

	#if DEBUG
	@objc class func injected() {
		UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
	}
	#endif
}
