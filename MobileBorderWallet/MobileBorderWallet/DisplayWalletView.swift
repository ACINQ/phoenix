import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "DisplayWalletView"
)

enum DisplayWalletViewType {
	case beforeBackup
	case afterBackup
}

fileprivate enum NavLinkTag_DisplayWalletView: Equatable, Hashable {
	case backupWallet
}

struct DisplayWalletView: View {
	
	let wallet: WalletInfo
	let type: DisplayWalletViewType
	
	@EnvironmentObject var router: Router
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Your Wallet")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Grid(horizontalSpacing: 30, verticalSpacing: 15) {
				
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#1: \(wallet.seedPhraseWords[0])")
						.gridColumnAlignment(HorizontalAlignment.leading)
					Text("#7: \(wallet.seedPhraseWords[6])")
						.gridColumnAlignment(HorizontalAlignment.leading)
				}
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#2: \(wallet.seedPhraseWords[1])")
					Text("#8: \(wallet.seedPhraseWords[7])")
				}
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#3: \(wallet.seedPhraseWords[2])")
					Text("#9: \(wallet.seedPhraseWords[8])")
				}
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#4: \(wallet.seedPhraseWords[3])")
					Text("#10: \(wallet.seedPhraseWords[9])")
				}
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#5: \(wallet.seedPhraseWords[4])")
					Text("#11: \(wallet.seedPhraseWords[10])")
				}
				GridRow(alignment: VerticalAlignment.firstTextBaseline) {
					Text("#6: \(wallet.seedPhraseWords[5])")
					Text("#12: \(wallet.finalWordNumber)")
				}
			}
			.padding(.bottom, 40)
			
			switch type {
			case .beforeBackup:
				NavigationLink(value: NavLinkTag_DisplayWalletView.backupWallet) {
					Text("Backup wallet").font(.title2)
				}
				
			case .afterBackup:
				Button {
					router.popToRoot()
				} label: {
					Text("Done").font(.title2)
				}
			}
			
		} // </VStack>
		.navigationDestination(for: NavLinkTag_DisplayWalletView.self) { _ in
			
			PatternSketchView(type: .backup(wallet: wallet))
		}
	}
}
