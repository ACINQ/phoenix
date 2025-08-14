import SwiftUI

fileprivate let filename = "WalletSelector"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

fileprivate let GRID_HSPACING: CGFloat = 10
fileprivate let GRID_VSPACING: CGFloat = 20
fileprivate let IMG_SIZE: CGFloat = 64

struct WalletSelector: View {
	
	@Binding var visibleWallets: [WalletMetadata]
	@Binding var hiddenWallets: [WalletMetadata]
	
	let didSelectWallet: (_ wallet: WalletMetadata) -> Void
	let hiddenWallet: () -> Void
	
	@EnvironmentObject var deviceInfo: DeviceInfo
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			walletGrid()
			footer()
		}
	}
	
	@ViewBuilder
	func walletGrid() -> some View {
		
		ScrollView(.vertical) {
			Grid(
				alignment: Alignment.top,
				horizontalSpacing: GRID_HSPACING,
				verticalSpacing: GRID_VSPACING
			) {
				ForEach(0 ..< sortedWallets.count, id: \.self) { idx in
					if idx % 2 == 0 {
						walletGridRow(idx)
					} else {
						EmptyView()
					}
				}
			} // </Grid>
		} // </ScrollView>
		.frame(maxWidth: scrollViewMaxWidth, maxHeight: scrollViewMaxHeight)
	}
	
	@ViewBuilder
	func walletGridRow(_ idx: Int) -> some View {
		
		GridRow(alignment: VerticalAlignment.top) {
			if idx+1 < sortedWallets.count {
				walletGridItem(idx)
				walletGridItem(idx+1)
			} else {
				walletGridItem(idx).gridCellColumns(2)
			}
		}
	}
	
	@ViewBuilder
	func walletGridItem(_ idx: Int) -> some View {
		
		Button {
			didSelectWallet(sortedWallets[idx])
		} label: {
			VStack(alignment: HorizontalAlignment.center, spacing: 2) {
				
				let wallet = sortedWallets[idx]
				WalletImage(filename: wallet.photo, size: IMG_SIZE)
				Text(wallet.name)
					.font(.title3)
					.foregroundColor(.primary)
					.lineLimit(2)
					.multilineTextAlignment(.center)
			}
		}
		.frame(maxWidth: gridItemMaxWidth)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		Group {
			if isShowingHiddenWallets {
				footer_showVisibleWallets()
			} else {
				footer_accessHiddenWallet()
			}
		}
		.padding(.top, 20)
		.padding(.bottom, deviceInfo.isFaceID ? 10 : 20)
		.background(
			Color.primaryBackground
				.cornerRadius(15, corners: [.topLeft, .topRight])
				.edgesIgnoringSafeArea([.horizontal, .bottom])
		)
	}
	
	@ViewBuilder
	func footer_accessHiddenWallet() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			
			Button {
				hiddenWallet()
			} label: {
				Label {
					Text("Hidden wallet")
						.lineLimit(1)
						.foregroundColor(.primary)
				} icon: {
					Image(systemName: "circle.grid.3x3")
						.foregroundColor(.appAccent)
				}
			}
			.accessibilityLabel("Access hidden wallet")
			
			Spacer(minLength: 0)
		} // </HStack>
		.transition(.move(edge: .trailing))
	}
	
	@ViewBuilder
	func footer_showVisibleWallets() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			
			Button {
				showVisibleWallets()
			} label: {
				Label {
					Text("Visible wallets")
						.lineLimit(1)
						.foregroundColor(.primary)
				} icon: {
					Image(systemName: "chevron.backward")
						.foregroundColor(.appAccent)
				}
			}
			.accessibilityLabel("Go back to visible wallets list")
			
			Spacer(minLength: 0)
		} // </HStack>
		.transition(.move(edge: .leading))
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isShowingHiddenWallets: Bool {
		
		return !hiddenWallets.isEmpty
	}
	
	var sortedWallets: [WalletMetadata] {
		
		return isShowingHiddenWallets ? hiddenWallets : visibleWallets
	}
	
	var scrollViewMaxWidth: CGFloat {
		
		return deviceInfo.textColumnMaxWidth
	}
	
	var scrollViewMaxHeight: CGFloat {
		
		if deviceInfo.isShortHeight {
			return CGFloat.infinity
		} else {
			return deviceInfo.windowSize.height * 0.5
		}
	}
	
	var gridItemMaxWidth: CGFloat {
		
		let scrollViewWidth = min(scrollViewMaxWidth, deviceInfo.windowSize.width)
		
		return (scrollViewWidth / 2.0) - GRID_HSPACING
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func showVisibleWallets() {
		log.trace(#function)
		
		withAnimation {
			hiddenWallets = []
		}
	}
}
