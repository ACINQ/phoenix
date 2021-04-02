import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "InitializationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct InitializationView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.initialization() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			
			// ZStack: layer 0 (background)
			// Position the settings icon in top-right corner.
			HStack{
				Spacer()
				VStack {
					NavigationLink(destination: ConfigurationView()) {
						Image(systemName: "gearshape")
							.renderingMode(.template)
							.imageScale(.large)
					}
					.buttonStyle(PlainButtonStyle())
					.padding(.all, 8)
					.background(Color.buttonFill)
					.cornerRadius(16)
					.overlay(
						RoundedRectangle(cornerRadius: 16)
						.stroke(Color.appHorizon, lineWidth: 2)
					)
					Spacer()
				}
				.padding(.all, 16)
			}
			
			// ZStack: layer 1 (foreground)
			VStack {
			
				Image(logoImageName)
					.resizable()
					.frame(width: 96, height: 96)
				//	.overlay(Circle().stroke(Color.secondary, lineWidth: 1.5))
				//	.clipShape(Circle())
					.padding([.top, .bottom], 0)

				Text("Phoenix")
					.font(Font.title2)
					.padding(.top, -10)
					.padding(.bottom, 80)

				Button {
					createMnemonics()
				} label: {
					HStack {
						Image(systemName: "flame")
							.imageScale(.small)

						Text("Create new wallet")
					}
					.font(.title2)
					.foregroundColor(Color(red: 0.99, green: 0.99, blue: 1.0))
				}
				.buttonStyle(PlainButtonStyle())
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(Color.appHorizon)
				.cornerRadius(16)
				.padding(.bottom, 40)

				NavigationLink(destination: RestoreWalletView()) {
					HStack {
						Image(systemName: "arrow.down.circle")
							.imageScale(.small)

						Text("Restore my wallet")
					}
					.font(.title2)
				}
				.buttonStyle(PlainButtonStyle())
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
				.background(Color(UIColor.systemFill))
				.cornerRadius(16)
				.overlay(
					RoundedRectangle(cornerRadius: 16)
					.stroke(Color.appHorizon, lineWidth: 2)
				)
				.padding([.top, .bottom], 0)

			} // </VStack>
			.padding(.top, keyWindow?.safeAreaInsets.top)
			.padding(.bottom, keyWindow?.safeAreaInsets.bottom)
			.frame(maxWidth: .infinity, maxHeight: .infinity)
			.offset(x: 0, y: -40) // move center upwards; focus is buttons, not logo
			.edgesIgnoringSafeArea(.all)
				
		} // </ZStack>
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
		.onChange(of: mvi.model, perform: { model in
			onModelChange(model: model)
		})
	}
	
	var logoImageName: String {
		if AppDelegate.get().business.chain.isTestnet() {
			return "logo_blue"
		} else {
			return "logo_green"
		}
	}
	
	func createMnemonics() -> Void {
		log.trace("createMnemonics()")
		
		let swiftEntropy = AppSecurity.shared.generateEntropy()
		let kotlinEntropy = KotlinByteArray.fromSwiftData(swiftEntropy)
		
		let intent = Initialization.IntentGenerateWallet(entropy: kotlinEntropy)
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
		
		AppSecurity.shared.addKeychainEntry(mnemonics: model.mnemonics) { (error: Error?) in
			if error == nil {
				AppDelegate.get().loadWallet(seed: model.seed)
			}
		}
	}
}

class InitView_Previews : PreviewProvider {

	static var previews: some View {
		InitializationView()
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
			
		InitializationView()
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
			
		InitializationView()
			.preferredColorScheme(.light)
			.previewDevice("iPhone 11")
    }
}
