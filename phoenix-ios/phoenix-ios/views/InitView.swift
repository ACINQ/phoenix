import SwiftUI
import PhoenixShared

struct InitView : MVIView {
    typealias Model = Init.Model
    typealias Intent = Init.Intent

	var body: some View {
		mvi { model, intent in
			ZStack {
				
				// ZStack: layer 0 (background)
				// Position the settings icon in top-right corner.
				HStack{
					Spacer()
					VStack {
						NavigationLink(destination: ConfigurationView()) {
							Image(systemName: "gearshape")
							.imageScale(.large)
						}
						.buttonStyle(PlainButtonStyle())
						.padding([.top, .bottom], 8)
						.padding([.leading, .trailing], 8)
						.background(Color.white)
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
				
					Image("logo_flat")
					.resizable()
					.frame(width: 96, height: 96)
					.overlay(Circle().stroke(Color(UIColor.systemGray3), lineWidth: 1.5))
					.clipShape(Circle())
					.padding([.top, .bottom], 0)

					Text("Phoenix")
					.font(Font.title2)
					.padding(.top, 8)
					.padding(.bottom, 80)

					Button {
						intent(Init.IntentCreateWallet())
					} label: {
						HStack {
						//	Image("ic_fire")
						//	.resizable()
						//	.frame(width: 16, height: 16)
						//	.foregroundColor(.white)

							Image(systemName: "flame")
							.imageScale(.small)

							Text("Create new wallet")
						}
						.font(.title2)
						.foregroundColor(.appBackgroundLight)
					}
					.buttonStyle(PlainButtonStyle())
					.padding([.top, .bottom], 8)
					.padding([.leading, .trailing], 16)
					.background(Color.appHorizon)
					.cornerRadius(16)
					.padding(.bottom, 40)

					NavigationLink(destination: RestoreWalletView()) {
						HStack {
						//	Image("ic_restore")
						//	.resizable()
						//	.frame(width: 16, height: 16)

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
	}
}

class InitView_Previews : PreviewProvider {
    static let mockModel = Init.ModelInitialization()

    static var previews: some View {
        mockView(InitView()) { $0.initModel = mockModel }
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
