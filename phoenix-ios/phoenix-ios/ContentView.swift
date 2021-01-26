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


	@State private var unlockedOnce = false
	@State private var isUnlocked = false
	@State private var enabledSecurity = EnabledSecurity()
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverContent: AnyView? = nil

	var body: some View {

		ZStack {
			
			if isUnlocked || !enabledSecurity.isEmpty {

				if isUnlocked || unlockedOnce {
					primaryView()
						.zIndex(0) // needed for proper animation
						.onAppear {
							unlockedOnce = true
						}

					if let popoverContent = popoverContent {
						PopoverWrapper {
							popoverContent
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
		.environmentObject(CurrencyPrefs())
		.onReceive(didEnterBackgroundPublisher, perform: { _ in
			onDidEnterBackground()
		})
		.onReceive(popoverState.displayContent) {
			let newPopoverContent = $0
			withAnimation {
				popoverContent = newPopoverContent
			}
		}
		.onReceive(popoverState.close) { _ in
			withAnimation {
				popoverContent = nil
			}
		}
	}

	@ViewBuilder
	func primaryView() -> some View {

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

		performVersionUpgradeChecks()
		
		AppSecurity.shared.tryUnlockWithKeychain {(mnemonics: [String]?, enabledSecurity: EnabledSecurity) in

			if let mnemonics = mnemonics {
				// wallet is unlocked
				AppDelegate.get().loadWallet(mnemonics: mnemonics)
				self.isUnlocked = true

			} else if enabledSecurity.isEmpty {
				// wallet not yet configured
				self.isUnlocked = true

			} else {
				// wallet is locked
				self.enabledSecurity = enabledSecurity
			}
		}
	}
	
	private func performVersionUpgradeChecks() -> Void {
		
		// Upgrade check(s)
		
		let key = "lastVersionCheck"
		let previousBuild = UserDefaults.standard.string(forKey: key) ?? "3"
		
		// v0.7.3 (build 4)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		// - we decided to delete old channels database (due to low number of test users)
		//
		if previousBuild.isVersion(lessThan: "4") {
		
			migrateChannelsDbFiles()
		}
		
		// v0.7.3 (build 5)
		// - serialization change for Channels
		// - attempting to deserialize old version causes crash
		//
		if previousBuild.isVersion(lessThan: "5") {
			
			migrateChannelsDbFiles()
		}
		
		let currentBuild = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
		if previousBuild.isVersion(lessThan: currentBuild) {

			UserDefaults.standard.set(currentBuild, forKey: key)
		}
	}
	
	private func migrateChannelsDbFiles() -> Void {
		
		let fm = FileManager.default
		
		let appSupportDirs = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)
		guard let appSupportDir = appSupportDirs.first else {
			return
		}
		
		let databasesDir = appSupportDir.appendingPathComponent("databases", isDirectory: true)
		
		let db1 = databasesDir.appendingPathComponent("channels.sqlite", isDirectory: false)
		let db2 = databasesDir.appendingPathComponent("channels.sqlite-shm", isDirectory: false)
		let db3 = databasesDir.appendingPathComponent("channels.sqlite-wal", isDirectory: false)
		
		if !fm.fileExists(atPath: db1.path) &&
		   !fm.fileExists(atPath: db2.path) &&
		   !fm.fileExists(atPath: db3.path)
		{
			// Database files don't exist. So there's nothing to migrate.
			return
		}
		
		let placeholder = "{version}"
		
		let template1 = "channels.\(placeholder).sqlite"
		let template2 = "channels.\(placeholder).sqlite-shm"
		let template3 = "channels.\(placeholder).sqlite-wal"
		
		var done = false
		var version = 0
		
		while !done {
			
			let f1 = template1.replacingOccurrences(of: placeholder, with: String(version))
			let f2 = template2.replacingOccurrences(of: placeholder, with: String(version))
			let f3 = template3.replacingOccurrences(of: placeholder, with: String(version))
			
			let dst1 = databasesDir.appendingPathComponent(f1, isDirectory: false)
			let dst2 = databasesDir.appendingPathComponent(f2, isDirectory: false)
			let dst3 = databasesDir.appendingPathComponent(f3, isDirectory: false)
			
			if fm.fileExists(atPath: dst1.path) ||
			   fm.fileExists(atPath: dst2.path) ||
			   fm.fileExists(atPath: dst2.path)
			{
				version += 1
			} else {
				
				try? fm.moveItem(at: db1, to: dst1)
				try? fm.moveItem(at: db2, to: dst2)
				try? fm.moveItem(at: db3, to: dst3)
				
				done = true
			}
		}
		
		// As a safety precaution (to prevent a crash), always delete the original filePath.
		
		try? fm.removeItem(at: db1)
		try? fm.removeItem(at: db2)
		try? fm.removeItem(at: db3)
		
		// We just migrated the user's channels database.
		// Which means their existing channels are going to get force closed by the server.
		// So we need to inform the user about what just happened.
		
		popoverState.dismissable.send(false)
		popoverState.displayContent.send(
			PardonOurMess().anyView
		)
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
