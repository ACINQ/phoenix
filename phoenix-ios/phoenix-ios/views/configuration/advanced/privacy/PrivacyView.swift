import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "PrivacyView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


fileprivate enum NavLinkTag: String {
	case ElectrumConfigurationView
	case TorView
	case PaymentsBackupView
}

struct PrivacyView: View {
	
	@State private var navLinkTag: NavLinkTag? = nil
	
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@State private var swiftUiBugWorkaround: NavLinkTag? = nil
	@State private var swiftUiBugWorkaroundIdx = 0
	
	@State var didAppear = false
	
	@ViewBuilder
	var body: some View {
		
		let hasWallet = hasWallet()
		
		List {
			NavigationLink(
				destination: ElectrumConfigurationView(),
				tag: NavLinkTag.ElectrumConfigurationView,
				selection: $navLinkTag
			) {
				Label { Text("Electrum server") } icon: {
					Image(systemName: "link")
				}
			}

			NavigationLink(
				destination: ComingSoonView(title: "Tor"),
				tag: NavLinkTag.TorView,
				selection: $navLinkTag
			) {
				Label { Text("Tor") } icon: {
					Image(systemName: "shield.lefthalf.fill")
				}
			}
			
			if hasWallet {
				NavigationLink(
					destination: PaymentsBackupView(),
					tag: NavLinkTag.PaymentsBackupView,
					selection: $navLinkTag
				) {
					Label { Text("Payments backup") } icon: {
						Image(systemName: "icloud.and.arrow.up")
					}
				}
			}
			
		} // </List>
		.listStyle(.insetGrouped)
		.navigationBarTitle(
			NSLocalizedString("Privacy", comment: "Navigation bar title"),
			displayMode: .inline
		)
		.onAppear() {
			onAppear()
		}
		.onChange(of: deepLinkManager.deepLink) {
			deepLinkChanged($0)
		}
		.onChange(of: navLinkTag) {
			navLinkTagChanged($0)
		}
	}
	
	func hasWallet() -> Bool {
		
		let walletManager = AppDelegate.get().business.walletManager
		let hasWalletFlow = SwiftStateFlow<NSNumber>(origin: walletManager.hasWallet)
		
		if let value = hasWalletFlow.value_ {
			return value.boolValue
		} else {
			return false
		}
	}
	
	func onAppear() {
		log.trace("onAppear()")
		
		if !didAppear {
			didAppear = true
			if let deepLink = deepLinkManager.deepLink {
				deepLinkChanged(deepLink)
			}
			
		}
	}
	
	func deepLinkChanged(_ value: DeepLink?) {
		log.trace("deepLinkChanged() => \(value?.rawValue ?? "nil")")
		
		// This is a hack, courtesy of bugs in Apple's NavigationLink:
		// https://developer.apple.com/forums/thread/677333
		//
		// Summary:
		// There's some quirky code in SwiftUI that is resetting our navLinkTag.
		// Several bizarre workarounds have been proposed.
		// I've tried every one of them, and none of them work (at least, without bad side-effects).
		//
		// The only clean solution I've found is to listen for SwiftUI's bad behaviour,
		// and forcibly undo it.
		
		if value == nil {
			// We reached the final destination of the deep link
			clearSwiftUiBugWorkaround(delay: 1.0)
		
		} else {
			
			// Navigate towards deep link (if needed)
			var newNavLinkTag: NavLinkTag? = nil
			switch value {
				case .electrum : newNavLinkTag = NavLinkTag.ElectrumConfigurationView
				default        : break
			}
			
			if let newNavLinkTag = newNavLinkTag {
				
				self.swiftUiBugWorkaround = newNavLinkTag
				self.swiftUiBugWorkaroundIdx += 1
				clearSwiftUiBugWorkaround(delay: 5.0)
				
				self.navLinkTag = newNavLinkTag // Trigger/push the view
			}
		}
	}
	
	fileprivate func navLinkTagChanged(_ tag: NavLinkTag?) {
		log.trace("navLinkTagChanged() => \(tag?.rawValue ?? "nil")")
		
		if tag == nil, let forcedNavLinkTag = swiftUiBugWorkaround {
				
			log.trace("Blocking SwiftUI's attempt to reset our navLinkTag")
			self.navLinkTag = forcedNavLinkTag
		}
	}
	
	func clearSwiftUiBugWorkaround(delay: TimeInterval) {
		
		let idx = self.swiftUiBugWorkaroundIdx
		
		DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
			
			if self.swiftUiBugWorkaroundIdx == idx {
				log.trace("swiftUiBugWorkaround = nil")
				self.swiftUiBugWorkaround = nil
			}
		}
	}
}
