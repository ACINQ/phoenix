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
	
	@State var swapInWalletBalance: WalletBalance = WalletBalance.companion.empty()
	
	@State var swapIn_minFundingSat: Int64 = 0
	@State var swapIn_minFeeSat: Int64 = 0
	@State var swapIn_feePercent: Double = 0.0
	
	// Toggles confirmation dialog (used to select preferred explorer)
	@State var showBlockchainExplorerOptions = false
	
	let swapInWalletBalancePublisher = Biz.business.balanceManager.swapInWalletBalancePublisher()
	let chainContextPublisher = Biz.business.appConfigurationManager.chainContextPublisher()
	
	@Environment(\.popoverState) var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			content()
			footer()
		}
		.onReceive(swapInWalletBalancePublisher) {
			swapInWalletBalanceChanged($0)
		}
		.onReceive(chainContextPublisher) {
			chainContextChanged($0)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		let incomingSat = swapInWalletBalance.total.sat
		if incomingSat >= swapIn_minFundingSat {
			content_sufficient()
		} else {
			content_insufficient()
		}
	}
	
	@ViewBuilder
	func content_sufficient() -> some View {
		
		let minFee = Utils.formatBitcoin(sat: swapIn_minFeeSat, bitcoinUnit: .sat)
		let feePercent = formatFeePercent()
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text(
				"""
				Once the incoming payment is confirmed, Phoenix will use the funds to open a payment channel.
				"""
			)
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			
			Text(styled: String(format: NSLocalizedString(
				"""
				The fee is **%@%%** (%@ minimum).
				""",
				comment:	"Minimum amount description."),
				feePercent, minFee.string
			))
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
		}
		.padding([.top, .leading, .trailing], 20)
		.padding(.bottom, 30)
	}
	
	@ViewBuilder
	func content_insufficient() -> some View {
		
		let minFunding = Utils.formatBitcoin(sat: swapIn_minFundingSat, bitcoinUnit: .sat)
		
		let minFee = Utils.formatBitcoin(sat: swapIn_minFeeSat, bitcoinUnit: .sat)
		let feePercent = formatFeePercent()
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text(styled: String(format: NSLocalizedString(
				"""
				Total deposits must be at least **%@**. Please make additional deposits to use the funds within Phoenix.
				""",
				comment:	"Minimum amount description."),
				minFunding.string
			))
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
			
			Text(styled: String(format: NSLocalizedString(
				"""
				Once the minimum amount is reached, Phoenix will use the funds to open a payment channel. \
				The fee is **%@%%** (%@ minimum).
				""",
				comment:	"Minimum amount description."),
				feePercent, minFee.string
			))
			.multilineTextAlignment(.leading)
			.fixedSize(horizontal: false, vertical: true) // text truncation bugs
		}
		.padding([.top, .leading, .trailing], 20)
		.padding(.bottom, 30)
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		HStack {
			if #available(iOS 15.0, *) {
				Button {
					showBlockchainExplorerOptions = true
				} label: {
					Text("Explore").font(.title2)
				}
				.confirmationDialog("Blockchain Explorer",
					isPresented: $showBlockchainExplorerOptions,
					titleVisibility: .automatic
				) {
					Button {
						exploreIncomingDeposit(website: BlockchainExplorer.WebsiteMempoolSpace())
					} label: {
						Text(verbatim: "Mempool.space") // no localization needed
					}
					Button {
						exploreIncomingDeposit(website: BlockchainExplorer.WebsiteBlockstreamInfo())
					} label: {
						Text(verbatim: "Blockstream.info") // no localization needed
					}
					Button("Copy bitcoin address") {
						copySwapInAddress()
					}
				} // </confirmationDialog>
			} // </if #available(iOS 15.0, *)>
			
			Spacer()
			
			Button {
				close()
			} label: {
				Text("Close").font(.title2)
			}
			.accessibilityHidden(popoverState.publisher.value?.dismissable ?? false)
		} // </HStack>
		.padding(.vertical, 10)
		.padding(.horizontal, 20)
		.background(
			Color(UIColor.secondarySystemBackground)
		)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func formatFeePercent() -> String {
		
		let formatter = NumberFormatter()
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 3
		
		return formatter.string(from: NSNumber(value: swapIn_feePercent))!
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func swapInWalletBalanceChanged(_ walletBalance: WalletBalance) {
		log.trace("swapInWalletBalanceChanged()")
		
		swapInWalletBalance = walletBalance
	}
	
	func chainContextChanged(_ context: WalletContext.V0ChainContext) -> Void {
		log.trace("chainContextChanged()")
		
		swapIn_minFundingSat = context.payToOpen.v1.minFundingSat // not yet segregated for swapIn - future work
		swapIn_minFeeSat = context.payToOpen.v1.minFeeSat         // not yet segregated for swapIn - future work
		swapIn_feePercent = context.swapIn.v1.feePercent * 100    // 0.01 => 1%
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
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
