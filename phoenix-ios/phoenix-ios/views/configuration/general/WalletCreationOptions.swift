import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "WalletCreationOptions"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct WalletCreationOptions: View {
	
	@State var mnemonicLanguage = Biz.mnemonicLanguagePublisher.value
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}
			
			content
		}
		.navigationTitle("Wallet Creation")
		.navigationBarTitleDisplayMode(.inline)
		.onChange(of: mnemonicLanguage) { newValue in
			log.debug("mnemoicLanguagePublisher.send(\(newValue.code))")
			Biz.mnemonicLanguagePublisher.send(newValue)
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		List {
			Section(header: Text("BIP39 Mnemonic")) {
				ForEach(MnemonicLanguage.allCases, id: \.code) { lang in
					Toggle(isOn: mnemonicLanguageBinding(lang)) {
						HStack(alignment: VerticalAlignment.centerTopLine, spacing: 6) {
							Text(verbatim: "\(lang.flag) \(lang.displayName)")
							if lang == MnemonicLanguage.english {
								Text("(recommended)")
									.font(Font.subheadline)
									.foregroundColor(Color.secondary)
							}
						}
					}
					.toggleStyle(CheckboxToggleStyle(
						onImage: onImage(),
						offImage: offImage()
					))
					.padding(.vertical, 5)
					
				} // </ForEach>
			} // </Section>
			
			Section(header: Text("Notes")) {
				Text(
					"""
					Your recovery phrase is 12 words, generated using the BIP39 standard.
					
					English is recommended. If you prefer another language, we provide \
					recovery instructions on our website.
					
					Your selection here does **not** affect your ability to send or receive bitcoin \
					within Phoenix.
					"""
				)
				.font(.callout)
				.padding(.vertical, 5)
			} // </Section>
		} // </List>
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	func mnemonicLanguageBinding(_ lang: MnemonicLanguage) -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { mnemonicLanguage == lang },
			set: {
				if $0 {
					log.debug("Setting: mnemonicLanguage = \(lang.code)")
					mnemonicLanguage = lang
				}
			}
		)
	}
}
