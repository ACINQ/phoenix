import SwiftUI
import PhoenixShared

struct ContentView: View {

    static func UIKitAppearance() {
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().standardAppearance = appearance
    }
	
	let didEnterBackgroundPublisher = NotificationCenter.default.publisher(for:
		UIApplication.didEnterBackgroundNotification
	)
	
	@State var unlockedOnce = false
	@State var isUnlocked = false
	@State var enabledSecurity = EnabledSecurity()

	var body: some View {
	
		if isUnlocked || !enabledSecurity.isEmpty {
			
			ZStack {
				
				if isUnlocked || unlockedOnce {
					primaryView()
						.zIndex(0) // needed for proper animation
						.onAppear {
							unlockedOnce = true
						}
				}
				
				if !isUnlocked {
					LockView(isUnlocked: $isUnlocked)
						.zIndex(1) // needed for proper animation
						.transition(.asymmetric(
							insertion : .identity,
							removal   : .move(edge: .bottom)
						))
				}
			}
			.environmentObject(CurrencyPrefs())
			.onReceive(didEnterBackgroundPublisher, perform: { _ in
				onDidEnterBackground()
			})
			
		} else {
			
			loadingView().onAppear {
				onAppLaunch()
			}
		}
	}
	
	@ViewBuilder func primaryView() -> some View {
		
		appView(MVIView({ $0.content() }) { model, intent in
			
			NavigationView {
				
				if model is Content.ModelIsInitialized {
					HomeView()
				} else if model is Content.ModelNeedInitialization {
					InitializationView()
				} else {
					loadingView()
				}
				
			} // </NavigationView>
		})
    }
	
	@ViewBuilder func loadingView() -> some View {
		
		VStack {
			Image(systemName: "arrow.triangle.2.circlepath")
				.imageScale(.large)
				.rotationEffect(Angle(degrees: 360.0))
				.animation(.easeIn)
		}
		.edgesIgnoringSafeArea(.all)
		.navigationBarTitle("", displayMode: .inline)
		.navigationBarHidden(true)
	}
	
	private func handleFirstAppLaunch() -> Void {
		print("handleFirstAppLaunch()")
		
		// The very first time the app is launched we need to:
		// - randomly generate the databaseKey
		// - randomly generate the lockingKey
		// - wrap the databaseKey with the lockingKey
		// - store the lockingKey in the OS keychain
		// - store the wrapped databaseKey (ciphertext) in the security.json file
		
		let databaseKey = AppSecurity.shared.generateDatabaseKey()
		AppSecurity.shared.addKeychainEntry(databaseKey: databaseKey) {(error: Error?) in
			
			self.onAppLaunch()
		}
	}
	
	private func onAppLaunch() -> Void {
		print("onAppLaunch()")
		
		AppSecurity.shared.tryUnlockWithKeychain {(databaseKey: Data?, enabledSecurity: EnabledSecurity) in
			
			if databaseKey == nil && enabledSecurity.isEmpty {
				return handleFirstAppLaunch()
			}
			
			if databaseKey != nil {
				self.isUnlocked = true
			} else {
				self.enabledSecurity = enabledSecurity
			}
		}
	}
	
	private func onDidEnterBackground() -> Void {
		print("onDidEnterBackground()")
		
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


class ContentView_Previews: PreviewProvider {
    static let mockModel = Content.ModelNeedInitialization()

    static var previews: some View {
        mockView(ContentView())
                .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
