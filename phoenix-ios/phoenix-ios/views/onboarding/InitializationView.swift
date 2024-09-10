import SwiftUI
import PhoenixShared

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
	
	@StateObject var mvi = MVIState({ $0.initialization() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

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
			
			if BusinessManager.showTestnetBackground {
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
			onModelChange(model: model)
		}
		.onReceive(Biz.mnemonicLanguagePublisher) {
			log.debug("mnemonicLanguage = \($0.code)")
			mnemonicLanguage = $0
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		// Position the settings icon in top-right corner.
		HStack{
			Spacer()
			VStack {
				navLink(.Configuration) {
					Image(systemName: "gearshape")
						.renderingMode(.template)
						.imageScale(.large)
				}
				.accessibilityLabel("Settings")
				.accessibilitySortPriority(-1)
				Spacer()
			}
			.padding(.all, 20)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
		
			Spacer()
			
			VStack(alignment: HorizontalAlignment.center, spacing: 0) {
				Image(logoImageName)
					.resizable()
					.frame(width: 96, height: 96)

				Text("Phoenix")
					.font(Font.title2)
					.padding(.top, -10)
					.padding(.bottom, 80)
			}
			.accessibilityHidden(true)
			
			Button {
				createMnemonics()
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
			}
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 100,
					backgroundFill: Color.appAccent
				)
			)
			.padding(.bottom, 40)

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
			}
			.buttonStyle(
				ScaleButtonStyle(
					cornerRadius: 100,
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent
				)
			)
			.padding([.top, .bottom], 0)
			
			Spacer() // 2 spacers at bottom
			Spacer() // move center upwards; focus is buttons, not logo

		} // </VStack>
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.assignMaxPreference(for: buttonWidthReader.key, to: $buttonWidth)
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
	
	var logoImageName: String {
		if BusinessManager.isTestnet {
			return "logo_blue"
		} else {
			return "logo_green"
		}
	}
	
	func navLinkTagBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { navLinkTag != nil },
			set: { if !$0 { navLinkTag = nil }}
		)
	}
	
	func createMnemonics() -> Void {
		log.trace("createMnemonics()")
		
		let swiftEntropy = AppSecurity.shared.generateEntropy()
		let kotlinEntropy = swiftEntropy.toKotlinByteArray()
		
		let intent = Initialization.IntentGenerateWallet(
			entropy: kotlinEntropy,
			language: mnemonicLanguage
		)
		mvi.intent(intent)
	}
	
	func onModelChange(model: Initialization.Model) -> Void {
		log.trace("onModelChange()")
	
		if let model = model as? Initialization.ModelGeneratedWallet {
			createWallet(model: model)
		}
	}
	
	func createWallet(model: Initialization.ModelGeneratedWallet) -> Void {
		log.trace("createWallet()")
		
		let recoveryPhrase = RecoveryPhrase(
			mnemonics : model.mnemonics,
			language  : model.language
		)

		AppSecurity.shared.addKeychainEntry(recoveryPhrase: recoveryPhrase) { (error: Error?) in
			if error == nil {
				Biz.loadWallet(recoveryPhrase: recoveryPhrase, seed: model.seed)
			}
		}
	}
}

/*
struct MnemonicLanguageBindingKey: EnvironmentKey {
	static var defaultValue: Binding<MnemonicLanguage> = .constant(MnemonicLanguage.french)
}

extension EnvironmentValues {
	var mnemonicLanguageBinding: Binding<MnemonicLanguage> {
		get { self[MnemonicLanguageBindingKey.self] }
		set { self[MnemonicLanguageBindingKey.self] = newValue }
	}
}
*/
