import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ContentView"
)

fileprivate enum NavLinkTag_ContentView: Equatable, Hashable {
	case createNewWallet(wallet: WalletInfo)
	case restoreWallet
	case bigPattern
}

struct ContentView: View {
	
	@StateObject private var router = Router()
	
	@ViewBuilder
	var body: some View {
		
		NavigationStack(path: $router.navPath) {
			content()
		}
		.navigationTitle("")
		.navigationBarTitleDisplayMode(.inline)
		.navigationBarHidden(true)
		.environmentObject(router)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 60) {
			
			Button {
				createNewWallet()
			} label: {
				Text("Create new wallet")
					.font(.title2)
			}
			
			NavigationLink(value: NavLinkTag_ContentView.restoreWallet) {
				Text("Restore wallet")
					.font(.title2)
			}
			
		//	NavigationLink(value: NavLinkTag_ContentView.bigPattern) {
		//		Text("Big pattern")
		//			.font(.title2)
		//	}
			
		} // </VStack>
		.navigationDestination(for: NavLinkTag_ContentView.self) { tag in
			
			switch tag {
			case .createNewWallet(let wallet):
				DisplayWalletView(wallet: wallet, type: .beforeBackup)
				
			case .restoreWallet:
				RestoreWalletView()
				
			case .bigPattern:
				BigPatternSketchView()
			}
		}
	}
	
	func createNewWallet() {
		log.trace("createNewWallet()")
		
		let newWallet = Bip39.generateWallet()
		router.navPath.append(NavLinkTag_ContentView.createNewWallet(wallet: newWallet))
	}
}
