import SwiftUI
import PhoenixShared

struct RecoverySeedView : View {
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var mnemonics: [String] = []
	
	var body: some View {
		
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
					decrypt()
				} label: {
					HStack {
						Image(systemName: "key").imageScale(.small)
						Text("Display seed")
					}
				}
				.disabled(isDecrypting)
					
			} // </VStack>
			.padding(.top, 20)
			.padding(.bottom, 20)
			.padding([.leading, .trailing], 30)
				
		} // </ScrollView>
		.sheet(isPresented: $revealSeed) {
			RecoverySeedReveal(mnemonics: $mnemonics)
		}
		.navigationBarTitle("Recovery Seed", displayMode: .inline)
	}
	
	func decrypt() -> Void {
		
		isDecrypting = true
		
		let Succeed = {(result: [String]) in
			mnemonics = result
			revealSeed = true
			isDecrypting = false
		}
		
		let Fail = {
			isDecrypting = false
		}
		
		let enabledSecurity = AppSecurity.shared.enabledSecurity.value
		if enabledSecurity == .none {
			AppSecurity.shared.tryUnlockWithKeychain { mnemonics, _ in
				
				if let mnemonics = mnemonics {
					Succeed(mnemonics)
				} else {
					Fail()
				}
			}
		} else {
			let prompt = NSLocalizedString("Unlock your seed.", comment: "Biometrics prompt")
			
			AppSecurity.shared.tryUnlockWithBiometrics(prompt: prompt) { result in
				if case .success(let mnemonics) = result {
					Succeed(mnemonics)
				} else {
					Fail()
				}
			}
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
	
	@State static var testMnemonics = [
		"witch", "collapse", "practice", "feed", "shame", "open",
		"despair", "creek", "road", "again", "ice", "least"
	]
	
	static var previews: some View {
		
		RecoverySeedView()
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		RecoverySeedView()
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
		
		RecoverySeedReveal(mnemonics: $testMnemonics)
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		RecoverySeedReveal(mnemonics: $testMnemonics)
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
	}
}
