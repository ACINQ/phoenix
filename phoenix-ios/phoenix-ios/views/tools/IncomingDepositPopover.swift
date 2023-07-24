import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MinimumDepositPopover"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct IncomingDepositPopover: View {
	
	let swapInWalletBalancePublisher = Biz.business.balanceManager.swapInWalletBalancePublisher()
	@State var swapInWalletBalance: WalletBalance = WalletBalance.companion.empty()
	
	let swapInRejectedPublisher = Biz.swapInRejectedPublisher
	@State var swapInRejected: Lightning_kmpLiquidityEventsRejected? = nil
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
		.onReceive(swapInWalletBalancePublisher) {
			swapInWalletBalanceChanged($0)
		}
		.onReceive(swapInRejectedPublisher) {
			swapInRejectedStateChanged($0)
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			if showNormal() {
				Text("Incoming payments")
					.font(.title3)
					.accessibilityAddTraits(.isHeader)
					.accessibilitySortPriority(100)
			} else {
				Text("On-chain pending funds")
					.font(.title3)
					.accessibilityAddTraits(.isHeader)
					.accessibilitySortPriority(100)
			}
			
			Spacer()
			
			Button {
				close()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(popoverState.currentItem?.dismissable ?? false)
		}
		.padding(.horizontal, 15)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		if showNormal() {
			content_normal()
		} else {
			content_pendingFunds()
		}
	}
	
	@ViewBuilder
	func content_normal() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text(
				"""
				These funds will appear on your balance once swapped to Lightning, according to your fee settings.
				"""
			)
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Button {
					navigateToLiquiditySettings()
				} label: {
					Text("Check fee settings")
				}
			}
			.padding(.top, 5)
		}
		.padding(.all, 15)
	}
	
	@ViewBuilder
	func content_pendingFunds() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text(
				"""
				You have funds that could not be swapped to Lightning.
				"""
			)
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			
			if let rejected = swapInRejected, let reason = rejected.reason.asOverAbsoluteFee() {
				
				let actualFee = Utils.formatBitcoin(msat: rejected.fee, bitcoinUnit: .sat)
				let maxFee = Utils.formatBitcoin(sat: reason.maxAbsoluteFee, bitcoinUnit: .sat)
				
				Text("The fee was \(actualFee.string), but your max fee was set to \(maxFee.string)")
					.multilineTextAlignment(.leading)
					.fixedSize(horizontal: false, vertical: true) // text truncation bugs
				
			} else if let rejected = swapInRejected, let reason = rejected.reason.asOverRelativeFee() {
				
				let actualFee = Utils.formatBitcoin(msat: rejected.fee, bitcoinUnit: .sat)
				let percent = basisPointsAsPercent(reason.maxRelativeFeeBasisPoints)
				
				Text("The fee was \(actualFee.string) which is more than \(percent) of the amount.")
					.multilineTextAlignment(.leading)
					.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Button {
					navigateToLiquiditySettings()
				} label: {
					Text("Check fee settings")
				}
			}
			.padding(.top, 5)
		}
		.padding(.all, 15)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func showNormal() -> Bool {
		
		return !showOnChainPendingFunds()
	}
	
	func showOnChainPendingFunds() -> Bool {
		
		return swapInWalletBalance.confirmed.sat > 0 &&
			swapInWalletBalance.unconfirmed.sat == 0 &&
			swapInWalletBalance.weaklyConfirmed.sat == 0 &&
			swapInRejected != nil
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func basisPointsAsPercent(_ basisPoints: Int32) -> String {
		
		// Example: 30% == 3,000 basis points
		// 
		// 3,000 / 100       => 30.0 => 3000%
		// 3,000 / 100 / 100 =>  0.3 => 30%
		
		let percent = Double(basisPoints) / Double(10_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 2
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func swapInWalletBalanceChanged(_ walletBalance: WalletBalance) {
		log.trace("swapInWalletBalanceChanged()")
		
		swapInWalletBalance = walletBalance
	}
	
	func swapInRejectedStateChanged(_ state: Lightning_kmpLiquidityEventsRejected?) {
		log.trace("swapInRejectedStateChanged()")
		
		swapInRejected = state
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		popoverState.close {
			self.deepLinkManager.broadcast(.liquiditySettings)
		}
	}
	
	func exploreIncomingDeposit(website: BlockchainExplorer.Website) {
		log.trace("exploreIncomingDeposit()")
		
		guard let peer = Biz.business.getPeer() else {
			return
		}
		let addr = peer.swapInAddress
		
		let txUrlStr = Biz.business.blockchainExplorer.addressUrl(addr: addr, website: website)
		if let txUrl = URL(string: txUrlStr) {
			UIApplication.shared.open(txUrl)
		}
	}
	
	func copySwapInAddress() {
		log.trace("copySwapInAddress()")
		
		guard let peer = Biz.business.getPeer() else {
			return
		}
		let addr = peer.swapInAddress
		
		UIPasteboard.general.string = addr
	}
	
	func close() {
		log.trace("close()")
		
		withAnimation {
			popoverState.close()
		}
	}
}
