import SwiftUI
import PhoenixShared

fileprivate let filename = "SwapInAddresses"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SwapInAddresses: View {
	
	struct TaprootAddress: Comparable {
		let address: String
		let index: Int32
		let state: Lightning_kmpWalletState.AddressState
		let isCurrent: Bool
		
		static func < (lhs: SwapInAddresses.TaprootAddress, rhs: SwapInAddresses.TaprootAddress) -> Bool {
			return lhs.index < rhs.index
		}
	}
	
	@State var taprootAddressList: [TaprootAddress] = []
	@State var legacyAddress: String? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("Swap-in addresses")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
			content()
			toast.view()
		}
		.task {
			await monitorSwapAddresses()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_taproot()
			
			if let addr = legacyAddress {
				section_legacy(addr)
			}
			
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_taproot() -> some View {
		
		Section {
			
			let count = taprootAddressList.count
			ForEach((0..<count).reversed(), id: \.self) { idx in
				
				let taprootAddr = taprootAddressList[idx]
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					
					let color = getVerticalDividerColor(taprootAddr)
					VerticalDivider(color: color, width: 3)
						.padding(.vertical, 2)
						.offset(x: -20)
					
					Text(verbatim: "#\(idx)")
						.lineLimit(1)
						.foregroundColor(.secondary)
						.padding(.trailing, 6)
					
					Text(taprootAddr.address)
						.lineLimit(1)
						.truncationMode(.middle)
					
					Spacer(minLength: 6)
					
					Button {
						copyAddressToPasteboard(taprootAddr.address)
					} label: {
						Image(systemName: "square.on.square")
					}
					
				} // </HStack>
				.listRowInsets(EdgeInsets(top: 10, leading: 20, bottom: 10, trailing: 20))
			}
		} header: {
			Text("Taproot")
		}
	}
	
	@ViewBuilder
	func section_legacy(_ address: String) -> some View {
		
		Section {
			
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
	
	func getVerticalDividerColor(_ taprootAddr: TaprootAddress) -> Color {
		
		if taprootAddr.state.alreadyUsed {
			return Color(UIColor.systemGray5)
		} else if taprootAddr.isCurrent {
			return Color.appAccent
		} else {
			return Color.clear
		}
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func swapInWalletChanged(_ wallet: Lightning_kmpWalletState) {
		log.trace(#function)
		
		let legacyAddr: String? = wallet.addresses.first {
			(address: String, state: Lightning_kmpWalletState.AddressState) in
			
			state.meta is Lightning_kmpWalletState.AddressMetaSingle
		}?.key
		
		let currentTaprootAddr: String? = wallet.firstUnusedDerivedAddress?.first as? String
		let taprootAddrList: [TaprootAddress] = wallet.addresses.compactMap {
			(address: String, state: Lightning_kmpWalletState.AddressState) in
			
			if let meta = state.meta as? Lightning_kmpWalletState.AddressMetaDerived {
				let isCurrent = if let currentTaprootAddr { address == currentTaprootAddr } else { false }
				return TaprootAddress(
					address   : address,
					index     : meta.index,
					state     : state,
					isCurrent : isCurrent
				)
			} else {
				return nil
			}
		}.sorted()
		
		self.legacyAddress = legacyAddr
		self.taprootAddressList = taprootAddrList
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------
	
	@MainActor
	func monitorSwapAddresses() async {
		log.trace(#function)
		
		do {
			let peer = try await Biz.business.peerManager.getPeer()
			for try await wallet in peer.phoenixSwapInWallet.wallet.walletStateSequence() {
				swapInWalletChanged(wallet)
			}
		} catch {
			log.error("monitorSwapAddresses(): \(error)")
		}
		
		log.debug("monitorSwapAddresses(): terminated")
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

struct VerticalDivider: View {
	 
	 let color: Color
	 let width: CGFloat
	 
	 init(color: Color, width: CGFloat = 0.5) {
		  self.color = color
		  self.width = width
	 }
	 
	 var body: some View {
		  color
				.frame(width: width)
	 }
}
