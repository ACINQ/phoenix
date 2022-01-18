import SwiftUI
import PhoenixShared


fileprivate enum NavLinkTag: String {
	case ElectrumConfigurationView
	case TorView
	case PaymentsBackupView
}

struct PrivacyView: View {
	
	@State private var navLinkTag: NavLinkTag? = nil
	
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
}
