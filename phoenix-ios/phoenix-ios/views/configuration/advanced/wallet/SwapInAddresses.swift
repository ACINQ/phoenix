import SwiftUI
import PhoenixShared

fileprivate let filename = "SwapInAddresses"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SwapInAddresses: View {
	
	let swapInAddressPublisher = Biz.business.peerManager.peerStatePublisher()
		.compactMap { $0.swapInWallet }
		.flatMap { $0.swapInAddressPublisher() }
	@State var swapInAddressInfo: Lightning_kmpSwapInWallet.SwapInAddressInfo? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			content()
			toast.view()
		}
		.navigationTitle("Swap-in addresses")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_taproot()
			section_legacy()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onReceive(swapInAddressPublisher) {
			swapInAddressChanged($0)
		}
	}
	
	@ViewBuilder
	func section_taproot() -> some View {
		
		Section {
			
			let count = taprootAddressCount()
			ForEach((0..<count).reversed(), id: \.self) { idx in
				
				let address = taprootAddress(idx)
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Text(verbatim: "#\(idx)")
						.lineLimit(1)
						.foregroundColor(.secondary)
						.padding(.trailing, 6)
					
					Text(address)
						.lineLimit(1)
						.truncationMode(.middle)
					
					Spacer(minLength: 6)
					
					Button {
						copyAddressToPasteboard(address)
					} label: {
						Image(systemName: "square.on.square")
					}
				}
			}
		} header: {
			Text("Taproot")
		}
	}
	
	@ViewBuilder
	func section_legacy() -> some View {
		
		Section {
			
			let address = legacyAddress()
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Text(address)
					.lineLimit(1)
					.truncationMode(.middle)
				
				Button {
					copyAddressToPasteboard(address)
				} label: {
					Image(systemName: "square.on.square")
				}
			}
			
		} header: {
			Text("Legacy")
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func taprootAddressCount() -> Int {
		
		let lastIndex = swapInAddressInfo?.index ?? Prefs.shared.swapInAddressIndex
		return lastIndex + 1
	}
	
	func taprootAddress(_ index: Int) -> String {
		
		guard let keyManager = Biz.business.walletManager.keyManagerValue() else {
			return "???"
		}
		
		return keyManager.swapInOnChainWallet
			.getSwapInProtocol(addressIndex: Int32(index))
			.address(chain: Biz.business.chain)
	}
	
	func legacyAddress() -> String {
		
		guard let keyManager = Biz.business.walletManager.keyManagerValue() else {
			return "???"
		}
		
		return keyManager.swapInOnChainWallet
			.legacySwapInProtocol
			.address(chain: Biz.business.chain)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func swapInAddressChanged(_ newInfo: Lightning_kmpSwapInWallet.SwapInAddressInfo?) {
		log.trace("swapInAddressChanged()")
		
		self.swapInAddressInfo = newInfo
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func copyAddressToPasteboard(_ address: String) -> Void {
		log.trace("copyAddressToPasteboard()")
		
		UIPasteboard.general.string = address
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			style: .chrome
		)
	}
}
