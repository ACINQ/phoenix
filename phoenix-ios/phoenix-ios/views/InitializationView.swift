import SwiftUI
import PhoenixShared

struct InitializationView: View {
	
	var body: some View {
		MVIView({ $0.initialization() }, onModel: { change in
			
			if let model = change.newModel as? Initialization.ModelGeneratedWallet {
				createWallet(model: model)
			}
			
		}) { model, postIntent in
			
			main(model, postIntent)
		}
	}
	
	@ViewBuilder func main(
		_ model: Initialization.Model,
		_ postIntent: @escaping (Initialization.Intent) -> Void
	) -> some View {
		
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
			
				Image("logo")
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
					createMnemonics(postIntent)
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
			.navigationBarTitle("", displayMode: .inline)
			.navigationBarHidden(true)
				
		} // </ZStack>
	}
	
	func createMnemonics(
		_ postIntent: @escaping (Initialization.Intent) -> Void
	) -> Void {
		
		let swiftEntropy = AppSecurity.shared.generateEntropy()
		let kotlinEntropy = KotlinByteArray.fromSwiftData(swiftEntropy)
		
		let intent = Initialization.IntentGenerateWallet(entropy: kotlinEntropy)
		postIntent(intent)
	}
	
	func createWallet(model: Initialization.ModelGeneratedWallet) -> Void {
		
		AppSecurity.shared.addKeychainEntry(mnemonics: model.mnemonics) { (error: Error?) in
			if error == nil {
				AppDelegate.get().loadWallet(seed: model.seed)
			}
		}
	}
}

class InitView_Previews : PreviewProvider {
	static let mockModel = Initialization.ModelReady()

	static var previews: some View {
		mockView(InitializationView())
			.preferredColorScheme(.light)
			.previewDevice("iPhone 8")
			
		mockView(InitializationView())
			.preferredColorScheme(.dark)
			.previewDevice("iPhone 8")
			
		mockView(InitializationView())
			.preferredColorScheme(.light)
			.previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
