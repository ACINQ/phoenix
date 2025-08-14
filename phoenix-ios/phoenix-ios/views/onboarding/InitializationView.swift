import SwiftUI
import PhoenixShared
import CryptoKit

fileprivate let filename = "InitializationView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct InitializationView: MVIView {
	
	enum NavLinkTag: String, Codable {
		case Configuration
		case RestoreView
	}
	
	let isCancellable: Bool
	
	@StateObject var mvi = MVIState({ Biz.business.controllers.initialization() })

	@State var mnemonicLanguage = Biz.mnemonicLanguagePublisher.value
	
	enum ButtonWidth: Preference {}
	let buttonWidthReader = GeometryPreferenceReader(
		key: AppendValue<ButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var buttonWidth: CGFloat? = nil
	
	// <iOS_16_workarounds>
	@State var navLinkTag: NavLinkTag? = nil
	// </iOS_16_workarounds>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	init(isCancellable: Bool) {
		self.isCancellable = isCancellable
	}
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		layers()
			.navigationTitle("")
			.navigationBarTitleDisplayMode(.inline)
			.navigationBarHidden(true)
			.navigationStackDestination(isPresented: navLinkTagBinding()) { // iOS 16
				navLinkView()
			}
			.navigationStackDestination(for: NavLinkTag.self) { tag in // iOS 17+
				navLinkView(tag)
			}
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if Biz.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
					.accessibilityHidden(true)
			}
			
			header()
			content()
			
		} // </ZStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { model in
			onModelChange(model)
		}
		.onReceive(Biz.mnemonicLanguagePublisher) {
			log.debug("mnemonicLanguage = \($0.code)")
			mnemonicLanguage = $0
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.top, spacing: 0) {
				Spacer()
				
				navLink(.Configuration) {
					Image(systemName: "gearshape")
						.renderingMode(.template)
						.imageScale(.large)
				}
				.accessibilityLabel("Settings")
				.accessibilitySortPriority(-1)
			}
			
			Spacer()
		}
		.padding(.all, 20)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			Spacer()
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Image(Biz.isTestnet ? "logo_blue" : "logo_green")
					.resizable()
					.frame(width: 96, height: 96)

				Text("Phoenix")
					.font(Font.title2)
					.padding(.top, -10)
			}
			.padding(.bottom, 80)
			.accessibilityHidden(true)
			
			createNewWalletButton()
				.padding(.bottom, 40)

			restoreWalletButton()
			
			Spacer() // 2 spacers at bottom
			Spacer() // move center upwards; focus is buttons, not logo
			
			if isCancellable {
				cancelButton()
					.padding(.bottom, 10)
			}

		} // </VStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
	}
	
	@ViewBuilder
	func createNewWalletButton() -> some View {
		
		Button {
			createNewWallet()
		} label: {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "flame")
					.imageScale(.small)
				Text("Create new wallet")
			}
			.foregroundColor(Color.white)
			.font(.title3)
			.read(buttonWidthReader)
			.frame(width: buttonWidth)
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(
			ScaleButtonStyle(
				cornerRadius: 100,
				backgroundFill: Color.appAccent
			)
		)
	}
	
	@ViewBuilder
	func restoreWalletButton() -> some View {
		
		navLink(.RestoreView) {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "arrow.down.circle")
					.imageScale(.small)
				Text("Restore my wallet")
			}
			.foregroundColor(Color.primary)
			.font(.title3)
			.read(buttonWidthReader)
			.frame(width: buttonWidth)
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(
			ScaleButtonStyle(
				cornerRadius: 100,
				backgroundFill: Color.primaryBackground,
				borderStroke: Color.appAccent
			)
		)
	}
	
	@ViewBuilder
	func cancelButton() -> some View {
		
		Button {
			cancelNewWallet()
		} label: {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "x.circle")
					.imageScale(.small)
				Text("Cancel")
			}
			.foregroundColor(Color.primary)
			.font(.title3)
			.read(buttonWidthReader)
			.frame(width: buttonWidth)
			.padding([.top, .bottom], 8)
			.padding([.leading, .trailing], 16)
			.contentShape(Rectangle()) // make Spacer area tappable
		}
		.buttonStyle(
			ScaleButtonStyle(
				cornerRadius: 100,
				borderStroke: Color.appNegative
			)
		)
	}
	
	@ViewBuilder
	private func navLink<Content>(
		_ tag: NavLinkTag,
		label: @escaping () -> Content
	) -> some View where Content: View {
		
		if #available(iOS 17, *) {
			NavigationLink(value: tag, label: label)
		} else {
			NavigationLink_16(
				destination: navLinkView(tag),
				tag: tag,
				selection: $navLinkTag,
				label: label
			)
		}
	}
	
	@ViewBuilder
	func navLinkView() -> some View {
		
		if let tag = self.navLinkTag {
			navLinkView(tag)
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func navLinkView(_ tag: NavLinkTag) -> some View {
		
		switch tag {
		case .Configuration:
			ConfigurationView()
			//	.environment(\.mnemonicLanguageBinding, $mnemonicLanguage)
			//
			// ^ This doesn't work. Apparently you can't pass an environment variable thru a NavigationLink:
			// https://stackoverflow.com/questions/59812640/environmentobject-doesnt-work-well-through-navigationlink
			
		case .RestoreView:
			RestoreView()
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func onModelChange(_ model: Initialization.Model) -> Void {
		log.trace(#function)
	
		if let model = model as? Initialization.ModelGeneratedWallet {
			didGenerateWallet(model: model)
		}
	}
	
	func didGenerateWallet(model: Initialization.ModelGeneratedWallet) -> Void {
		log.trace(#function)
		
		let recoveryPhrase = RecoveryPhrase(
			mnemonics : model.mnemonics,
			language  : model.language
		)

		let chain = Biz.business.chain
		AppSecurity.shared.addWallet(chain: chain, recoveryPhrase: recoveryPhrase, seed: model.seed) { result in
			switch result {
			case .failure(let reason):
				log.error("Error adding wallet: \(reason)")
				
			case .success():
				Biz.loadWallet(
					trigger        : .newWallet,
					recoveryPhrase : recoveryPhrase,
					seed           : model.seed
				)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func createNewWallet() {
		log.trace(#function)
		
		let randomKey = SymmetricKey(size: .bits128)
		let swiftEntropy: Data = randomKey.withUnsafeBytes {(bytes: UnsafeRawBufferPointer) -> Data in
			return Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
		assert(swiftEntropy.count == (128 / 8))
		
		let kotlinEntropy = swiftEntropy.toKotlinByteArray()
		
		let intent = Initialization.IntentGenerateWallet(
			entropy: kotlinEntropy,
			language: mnemonicLanguage
		)
		mvi.intent(intent)
	}
	
	func cancelNewWallet() {
		log.trace(#function)
		
		SceneDelegate.get().cancelIntroWindow()
	}
}
