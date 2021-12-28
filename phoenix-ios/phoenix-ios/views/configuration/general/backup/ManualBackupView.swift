import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ManualBackupView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct ManualBackupView : View {
	
	@Binding var manualBackup_taskDone: Bool
	
	@State var isDecrypting = false
	@State var revealSeed = false
	@State var mnemonics: [String] = []
	
	let encryptedNodeId: String
	@State var legal_taskDone: Bool
	@State var legal_lossRisk: Bool
	
	@State var animatingLegalToggleColor = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	var canSave: Bool {
		if manualBackup_taskDone {
			// Currently enabled.
			// Saving to disable: user only needs to disable the taskDone toggle
			return !legal_taskDone
		} else {
			// Currently disabled.
			// To enable, user must enable both toggles
			return legal_taskDone && legal_lossRisk
		}
	}
	
	init(manualBackup_taskDone: Binding<Bool>) {
		self._manualBackup_taskDone = manualBackup_taskDone
		
		let encryptedNodeId = AppDelegate.get().encryptedNodeId!
		self.encryptedNodeId = encryptedNodeId
		
		self._legal_taskDone = State<Bool>(initialValue: manualBackup_taskDone.wrappedValue)
		self._legal_lossRisk = State<Bool>(initialValue: manualBackup_taskDone.wrappedValue)
	}
	
	var body: some View {
		
		List {
			section_info()
			section_button()
			section_legal()
		}
		.sheet(isPresented: $revealSeed) {
			
			RecoverySeedReveal(
				isShowing: $revealSeed,
				mnemonics: $mnemonics
			)
		}
		.navigationBarTitle(
			NSLocalizedString("Manual Backup", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.navigationBarBackButtonHidden(true)
		.navigationBarItems(leading: backButton())
		.onAppear {
			onAppear()
		}
	}
	
	@ViewBuilder
	func backButton() -> some View {
		
		Button {
			didTapBackButton()
		} label: {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Image(systemName: "chevron.left")
					 .font(.title2)
				if canSave {
					Text("Save")
				} else {
					Text("Cancel")
				}
			}
		}
	}
	
	@ViewBuilder
	func section_info() -> some View {
		
		Section {
			
			VStack(alignment: .leading, spacing: 35) {
				Text(
					"""
					The recovery phrase (sometimes called a seed), is a list of 12 english words. \
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
					comment: "ManualBackupView"
				))
				
				Text(styled: NSLocalizedString(
					"""
					**Do not lose this seed.** \
					Save it somewhere safe (not on this phone). \
					If you lose your seed and your phone, you've lost your funds.
					""",
					comment: "ManualBackupView"
				))
					
			} // </VStack>
			.padding(.vertical, 15)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_button() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				
				Button {
					decrypt()
				} label: {
					HStack {
						Image(systemName: "key")
							.imageScale(.medium)
						Text("Display seed")
							.font(.headline)
					}
				}
				.disabled(isDecrypting)
				.padding(.vertical, 5)
				
				let enabledSecurity = AppSecurity.shared.enabledSecurity.value
				if enabledSecurity != .none {
					Text("(requires authentication)")
						.font(.footnote)
						.foregroundColor(.secondary)
						.padding(.top, 5)
						.padding(.bottom, 10)
				}
			} // </VStack>
			.frame(maxWidth: .infinity)
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_legal() -> some View {
		
		Section {
			
			Toggle(isOn: $legal_taskDone) {
				Text(
					"""
					I have saved my recovery phrase somewhere safe.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			
			Toggle(isOn: $legal_lossRisk) {
				Text(
					"""
					I understand that if I lose my phone & my recovery phrase, \
					then I will lose the funds in my wallet.
					"""
				)
				.lineLimit(nil)
				.alignmentGuide(VerticalAlignment.center) { d in
					d[VerticalAlignment.firstTextBaseline]
				}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.vertical, 5)
			
		} header: {
			Text("Legal")
			
		} // </Section>
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.renderingMode(.template)
			.imageScale(.large)
			.foregroundColor(animatingLegalToggleColor ? Color.red : Color.primary)
	}
	
	func onAppear(){
		log.trace("onAppear()")
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
			withAnimation(Animation.linear(duration: 1.0).repeatForever(autoreverses: true)) {
				animatingLegalToggleColor = true
			}
		}
	}
	
	func decrypt() {
		log.trace("decrypt()")
		
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
	
	func didTapBackButton() {
		log.trace("didTapBackButton()")
		
		if canSave {
			let taskDone = legal_taskDone && legal_lossRisk
			
			manualBackup_taskDone = taskDone
			Prefs.shared.manualBackup_setTaskDone(taskDone, encryptedNodeId: encryptedNodeId)
		}
		presentationMode.wrappedValue.dismiss()
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
	
	@State static var manualBackup_taskDone: Bool = true
	@State static var revealSeed: Bool = true
	
	@State static var testMnemonics = [
		"witch", "collapse", "practice", "feed", "shame", "open",
		"despair", "creek", "road", "again", "ice", "least"
	]
	
	static var previews: some View {
		
		ManualBackupView(manualBackup_taskDone: $manualBackup_taskDone)
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
		
		ManualBackupView(manualBackup_taskDone: $manualBackup_taskDone)
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
