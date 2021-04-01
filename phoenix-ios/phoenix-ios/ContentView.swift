import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && false
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ContentView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct GlobalEnvironment: ViewModifier {
	static var currencyPrefs = CurrencyPrefs()

	func body(content: Self.Content) -> some View {
		content
			.environmentObject(Self.currencyPrefs)
	}
}

struct ContentView: MVIView {

    static func UIKitAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().standardAppearance = appearance
    }

	@StateObject var mvi = MVIState({ $0.content() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State private var isUnlocked = false
	@State private var unlockedOnce = false
	@State private var enabledSecurity = EnabledSecurity()
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverItem: PopoverItem? = nil

	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	@ViewBuilder
	var view: some View {

		ZStack {
			
			if isUnlocked || !enabledSecurity.isEmpty {

				if isUnlocked || unlockedOnce {
					primaryView()
						.zIndex(0) // needed for proper animation
						.onAppear {
							unlockedOnce = true
						}

					if let popoverItem = popoverItem {
						PopoverWrapper(dismissable: popoverItem.dismissable) {
							popoverItem.view
						}
						.zIndex(1) // needed for proper animation
					}
				}

				if !isUnlocked {
					LockView(isUnlocked: $isUnlocked)
						.zIndex(2) // needed for proper animation
						.transition(.asymmetric(
							insertion : .identity,
							removal   : .move(edge: .bottom)
						))
				}
				
			} else { // App Launch

				NavigationView {
					loadingView().onAppear {
						onAppLaunch()
					}
				}
				.zIndex(3) // needed for proper animation
				.transition(.asymmetric(
					insertion : .identity,
					removal   : .opacity
				))
			}
			
		} // </ZStack>
		.modifier(GlobalEnvironment())
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			onDidEnterBackground()
		})
		.onReceive(popoverState.display) { (newPopoverItem: PopoverItem) in
			withAnimation {
				popoverItem = newPopoverItem
			}
		}
		.onReceive(popoverState.close) { _ in
			withAnimation {
				popoverItem = nil
			}
		}
	}

	@ViewBuilder
	func primaryView() -> some View {

		NavigationView {

			if mvi.model is Content.ModelIsInitialized {
				HomeView()
			} else if mvi.model is Content.ModelNeedInitialization {
				IntroContainer()
			} else {
				loadingView()
			}
		}
	}

	@ViewBuilder
	func loadingView() -> some View {

		VStack {
			Image(systemName: "arrow.triangle.2.circlepath")
				.imageScale(.large)
		}
		.edgesIgnoringSafeArea(.all)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
	}


	private func onAppLaunch() -> Void {
		log.trace("onAppLaunch()")
		
		AppSecurity.shared.tryUnlockWithKeychain {(mnemonics: [String]?, enabledSecurity: EnabledSecurity) in

			// There are multiple potential configurations:
			//
			// - no security       => mnemonics are available, enabledSecurity is empty
			// - standard security => mnemonics are available, enabledSecurity is non-empty
			// - advanced security => mnemonics are not available, enabledSecurity is non-empty
			//
			// Another way to think about it:
			// - standard security => touchID only protects the UI, wallet can immediately be loaded
			// - advanced security => touchID required to unlock both the UI and the seed

			if let mnemonics = mnemonics {
				// unlock & load wallet
				AppDelegate.get().loadWallet(mnemonics: mnemonics)
			}

			self.isUnlocked = enabledSecurity.isEmpty
			self.enabledSecurity = enabledSecurity
		}
	}

	private func onDidEnterBackground() -> Void {
		log.trace("onDidEnterBackground()")

		let currentSecurity = AppSecurity.shared.enabledSecurity.value
		enabledSecurity = currentSecurity
		//
		// Bug alert :
		//   Even though it *looks* like we just updated the `enabledSecurity` value,
		//   if we read it right now, we'll still see the old value !!!
		//
		// 	 For example, we might see something crazy like this:
		//
		//   print("currentSecurity: \(currentSecurity)") => 1
		//   print("enabledSecurity: \(enabledSecurity)") => 0
		//
		if !currentSecurity.isEmpty {
			self.isUnlocked = false
		}
	}
}

struct PardonOurMess: View {
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@Environment(\.openURL) var openURL
	
	var body: some View {
		
		// Pardon our mess
		// We refactored our database code to support versioning.
		// The bad news: your existing channels have been forced closed.
		// The good news: this shouldn't ever happen again.
		//
		// Help | OK
		
		VStack(alignment: HorizontalAlignment.leading) {
			
			HStack {
				Spacer()
				Image(systemName: "wrench.and.screwdriver.fill")
					.imageScale(.medium)
					.padding(.trailing, 4)
				Text("Pardon our mess")
					.font(.title2)
				Spacer()
			}
			.padding(.bottom, 10)
			
			Text("Your app has been updated and your channels have been removed. Thanks for testing.")
				.padding(.bottom, 20)
			
			HStack {
				Button {
					helpButtonTapped()
				} label: {
					Text("Help").font(.title3)
				}
				Spacer()
				Button {
					okButtonTapped()
				} label: {
					Text("OK").font(.title3)
				}
			}
		}
		.padding()
	}
	
	func helpButtonTapped() {
		log.trace("(PardonOurMess) helpButtonTapped()")
		
		let str = "https://phoenix.acinq.co/faq#my-channels-got-force-closed-how-do-i-recover-my-money"
		if let url = URL(string: str) {
			openURL(url)
		}
	}
	
	func okButtonTapped() {
		log.trace("(PardonOurMess) okButtonTapped()")
		popoverState.close.send()
	}
}


class ContentView_Previews: PreviewProvider {

	static var previews: some View {
		
		ContentView().mock(Content.ModelWaiting())
			.previewDevice("iPhone 11")
		
//		ContentView().mock(Content.ModelNeedInitialization())
//			.previewDevice("iPhone 11")
//		
//		ContentView().mock(Content.ModelIsInitialized())
//			.previewDevice("iPhone 11")
	}
}
