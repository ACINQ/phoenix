import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "RecoverySeedView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct RecoverySeedView : View {
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var mnemonics: [String] = []
	
	var body: some View {
		
		ScrollView(.vertical, showsIndicators: true) {
				
			VStack(alignment: .leading, spacing: 40) {
				Text(
					"""
					The backup phrase, known as a seed, is a list of 12 English words. \
					It allows you to recover full access to your funds if needed.
					"""
				)
				
				Text(
					"Only you alone possess this seed. Keep it private."
				)
				.fontWeight(.bold)
				
				Text(styled: NSLocalizedString(
					"""
					**Do not share this seed with anyone.** \
					Beware of phishing. The developers of Phoenix will never ask for your seed.
					""",
					comment: "RecoverySeedView"
				))
				
				Text(styled: NSLocalizedString(
					"""
					**Do not lose this seed.** \
					Save it somewhere safe (not on this phone). \
					If you lose your seed and your phone, you've lost your funds.
					""",
					comment: "RecoverySeedView"
				))
				
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
			
			RecoverySeedReveal(
				isShowing: $revealSeed,
				mnemonics: $mnemonics
			)
		}
		.navigationBarTitle(
			NSLocalizedString("Recovery Seed", comment: "Navigation bar title"),
			displayMode: .inline
		)
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
			AppSecurity.shared.tryUnlockWithKeychain { (mnemonics, _, _) in
				
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
	
	@Binding var isShowing: Bool
	@Binding var mnemonics: [String]
	
	func mnemonic(_ idx: Int) -> String {
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	var body: some View {
		
		ZStack {
			
			// close button
			// (required for landscapse mode, where swipe to dismiss isn't possible)
			VStack {
				HStack {
					Spacer()
					Button {
						close()
					} label: {
						Image("ic_cross")
							.resizable()
							.frame(width: 30, height: 30)
					}
				}
				Spacer()
			}
			.padding()
			
			main
		}
	}
	
	var main: some View {
		
		VStack {
			
			Spacer()
			
			Text("KEEP THIS SEED SAFE.")
				.font(.title2)
				.multilineTextAlignment(.center)
				.padding(.bottom, 2)
			Text("DO NOT SHARE.")
				.multilineTextAlignment(.center)
				.font(.title2)
			
			Spacer()
			
			HStack {
				Spacer()
				
				VStack {
					ForEach(0..<6, id: \.self) { idx in
						Text(verbatim: "#\(idx + 1) ")
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
						Text(verbatim: "#\(idx + 1) ")
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
			
			Text("BIP39 seed with standard BIP84 derivation path")
				.font(.footnote)
				.foregroundColor(.secondary)
			
		}
		.padding(.top, 20)
		.padding([.leading, .trailing], 30)
		.padding(.bottom, 20)
	}
	
	func close() {
		log.trace("[RecoverySeedReveal] close()")
		isShowing = false
	}
}

class RecoverySeedView_Previews: PreviewProvider {
	
	@State static var revealSeed = true
	
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
		
		RecoverySeedReveal(isShowing: $revealSeed, mnemonics: $testMnemonics)
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		RecoverySeedReveal(isShowing: $revealSeed, mnemonics: $testMnemonics)
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
	}
}
