import SwiftUI
import PhoenixShared

fileprivate let filename = "RecoveryPhraseReveal"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct RecoveryPhraseReveal: View {
	
	@Binding var isShowing: Bool
	
	let recoveryPhrase: RecoveryPhrase
	let language: MnemonicLanguage
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	init(isShowing: Binding<Bool>, recoveryPhrase: RecoveryPhrase) {
		self._isShowing = isShowing
		self.recoveryPhrase = recoveryPhrase
		self.language = recoveryPhrase.language ?? MnemonicLanguage.english
	}
	
	func mnemonic(_ idx: Int) -> String {
		let mnemonics = recoveryPhrase.mnemonicsArray
		return (mnemonics.count > idx) ? mnemonics[idx] : " "
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				header()
				GeometryReader { geometry in
					ScrollView(.vertical) {
						content()
							.frame(width: geometry.size.width)
							.frame(minHeight: geometry.size.height)
					}
				}
			}
			toast.view()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text(verbatim: "\(language.flag) \(language.displayName)")
				.font(.callout)
				.foregroundColor(.secondary)
			Spacer()
			Button {
				close()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
		}
		.padding(.top)
		.padding(.horizontal)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center) {
			
			Spacer(minLength: 0)
			
			Text("KEEP THIS SEED SAFE.")
				.font(.title2)
				.lineLimit(nil)
				.multilineTextAlignment(.center)
				.fixedSize(horizontal: false, vertical: true)
				.padding(.bottom, 2)
			Text("DO NOT SHARE.")
				.font(.title2)
				.lineLimit(nil)
				.multilineTextAlignment(.center)
				.fixedSize(horizontal: false, vertical: true)
			
			Spacer(minLength: 0)
			
			ViewThatFits(in: .horizontal) {
				twoColumnLayout()
				singleColumnLayout()
			}
			.environment(\.layoutDirection, .leftToRight) // issue #237
			.padding(.top, 20)
			.padding(.bottom, 10)
			
			Spacer(minLength: 0)
			Spacer(minLength: 0)
			
			copyButton()
				.padding(.bottom, 6)

			Text("BIP39 seed with standard BIP84 derivation path")
				.font(.footnote)
				.foregroundColor(.secondary)
				.fixedSize(horizontal: false, vertical: true)
		}
		.padding(.all, 20)
	}
	
	@ViewBuilder
	func twoColumnLayout() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			let vSpacing: CGFloat = 4
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(0..<6, id: \.self) { idx in
					label_index(idx)
				}
			}
			.padding(.trailing, 2)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(0..<6, id: \.self) { idx in
					label_mnemonic(idx)
				}
			}
			.padding(.trailing, 40)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(6..<12, id: \.self) { idx in
					label_index(idx)
				}
			}
			.padding(.trailing, 2)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(6..<12, id: \.self) { idx in
					label_mnemonic(idx)
				}
			}
		} // </HStack>
	}
	
	@ViewBuilder
	func singleColumnLayout() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			let vSpacing: CGFloat = 4
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(0..<12, id: \.self) { idx in
					label_index(idx)
				}
			}
			.padding(.trailing, 2)
			
			VStack(alignment: HorizontalAlignment.leading, spacing: vSpacing) {
				ForEach(0..<12, id: \.self) { idx in
					label_mnemonic(idx)
				}
			}
			
		} // </HStack>
	}
	
	@ViewBuilder
	func label_index(_ idx: Int) -> some View {
		Text(verbatim: "#\(idx + 1) ")
			.font(.headline)
			.lineLimit(1)
			.foregroundColor(.secondary)
	}
	
	@ViewBuilder
	func label_mnemonic(_ idx: Int) -> some View {
		Text(mnemonic(idx))
			.font(.headline)
			.lineLimit(1)
			.fixedSize(horizontal: true, vertical: false)
	}
	
	@ViewBuilder
	func copyButton() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
				
			Button {
				copyRecoveryPhrase()
			} label: {
				Text("Copy").font(.title3)
			}
			
			Spacer()
		} // </HStack>
	}
	
	func copyRecoveryPhrase() {
		log.trace(#function)
		
		copy(recoveryPhrase.mnemonics)
	}
	
	private func copy(_ string: String) {
		log.trace(#function)
		
		UIPasteboard.general.string = string
		AppDelegate.get().clearPasteboardOnReturnToApp = true
		toast.pop(
			"Pasteboard will be cleared when you return to Phoenix.",
			colorScheme: colorScheme.opposite,
			duration: 4.0 // seconds
		)
	}
	
	func close() {
		log.trace(#function)
		isShowing = false
	}
}
