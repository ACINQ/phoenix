import SwiftUI
import PhoenixShared

fileprivate let filename = "IncomingBalancePopover"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct IncomingBalancePopover: View {
	
	let showSwapInWallet: () -> Void
	let showFinalWallet: () -> Void
	
	@State var swapInWallet = Biz.business.balanceManager.swapInWalletValue()
	let swapInWalletPublisher = Biz.business.balanceManager.swapInWalletPublisher()
	
	@State var finalWallet = Biz.business.peerManager.finalWalletValue()
	let finalWalletPublisher = Biz.business.peerManager.finalWalletPublisher()
	
	@State var pendingChannelsBalance = Biz.business.balanceManager.pendingChannelsBalanceValue()
	let pendingChannelsBalancePublisher = Biz.business.balanceManager.pendingChannelsBalancePublisher()
	
	@State var liquidityPolicy: LiquidityPolicy = GroupPrefs.shared.liquidityPolicy
	let liquidityPolicyPublisher = GroupPrefs.shared.liquidityPolicyPublisher
	
	@EnvironmentObject var popoverState: PopoverState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 10) {
			group_fundsBeingConfirmed()
			group_fundsConfirmedNotLocked()
			group_fundsConfirmedExpired()
			group_finalWalletBalance()
		}
		.padding(.vertical, 10)
		.onReceive(swapInWalletPublisher) {
			swapInWalletChanged($0)
		}
		.onReceive(finalWalletPublisher) {
			finalWalletChanged($0)
		}
		.onReceive(pendingChannelsBalancePublisher) {
			pendingChannelsBalanceChanged($0)
		}
		.onReceive(liquidityPolicyPublisher) {
			liquidityPolicyChanged($0)
		}
	}
	
	@ViewBuilder
	func group_fundsBeingConfirmed() -> some View {
		
	#if DEBUG
		let fundsBeingConfirmed: Int64 =
			swapInWallet.unconfirmedBalance.toMsat() +
			swapInWallet.weaklyConfirmedBalance.toMsat() +
			pendingChannelsBalance.msat // + 1_000_000
	#else
		let fundsBeingConfirmed: Int64 =
			swapInWallet.unconfirmedBalance.toMsat() +
			swapInWallet.weaklyConfirmedBalance.toMsat() +
			pendingChannelsBalance.msat
	#endif
		
		if fundsBeingConfirmed > 0 {
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				// Title line
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "clock")
					
					let formatted = Utils.format(currencyPrefs, msat: fundsBeingConfirmed)
					if formatted.currency.type == .bitcoin {
						Text("Confirming: ") + Text(verbatim: "+\(formatted.string)")
					} else {
						Text("Confirming: ") + Text(verbatim: "+≈\(formatted.string)")
					}
				} // </HStack>
				.font(.headline)
				
				// Explanation line
				Text("Waiting for confirmation first before they can be swapped to Lightning.")
					.font(.callout)
					.foregroundStyle(.secondary)
				
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(10)
			.background(Color(.secondarySystemBackground))
			.cornerRadius(16)
			.onTapGesture {
				didTapSection_swapInWallet()
			}
			.padding(.horizontal, 10)
		}
	}
	
	@ViewBuilder
	func group_fundsConfirmedNotLocked() -> some View {
		
	#if DEBUG
		let fundsConfirmedNotLocked: Int64 = swapInWallet.deeplyConfirmedBalance.sat // + 1_000_000
	#else
		let fundsConfirmedNotLocked: Int64 = swapInWallet.deeplyConfirmedBalance.sat
	#endif
		
		if fundsConfirmedNotLocked > 0 {
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				let expiringSoon = (swapInWallet.expirationWarningInDays() ?? Int.max) <= 7
				
				// Title line
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					
					Group {
						if expiringSoon {
							Image(systemName: "exclamationmark.triangle").foregroundColor(.appNegative)
						} else {
							Image(systemName: "zzz").foregroundColor(.appWarn)
						}
					}
					
					let formatted = Utils.format(currencyPrefs, sat: fundsConfirmedNotLocked)
					if formatted.currency.type == .bitcoin {
						Text("Waiting for swap: ") + Text(verbatim: "+\(formatted.string)")
					} else {
						Text("Waiting for swap: ") + Text(verbatim: "+≈\(formatted.string)")
					}
				} // </HStack>
				.font(.headline)
				
				// Explanation line
				Group {
					if !liquidityPolicy.enabled {
						
						Text("Will remain on-chain because automated channels management is disabled.")
						
					} else {
						
						let (maxFee, isPercentBased) = maxSwapInFeeDetails()
						if isPercentBased {
							
							let percent = basisPointsAsPercent(liquidityPolicy.effectiveMaxFeeBasisPoints)
							Text(
								"""
								Will automatically be swapped to Lightning if the \
								fee is **less than \(percent)** (\(maxFee.string)) of the amount.
								"""
							)
							
						} else {
							
							Text(
								"""
								Will automatically be swapped to Lightning if the \
								fee is **less than \(maxFee.string)**.
								"""
							)
						}
					}
				}
				.font(.callout)
				.foregroundStyle(.secondary)
				
				// Expiration line
				if expiringSoon {
					Text("Attention! Some funds will expire soon and won't be eligible for a swap anymore.")
						.font(.callout)
						.foregroundColor(.appNegative.opacity(0.8))
				}
				
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(10)
			.background(Color(.secondarySystemBackground))
			.cornerRadius(16)
			.onTapGesture {
				didTapSection_swapInWallet()
			}
			.padding(.horizontal, 10)
		}
	}
	
	@ViewBuilder
	func group_fundsConfirmedExpired() -> some View {
		
	#if DEBUG
		let fundsConfirmedExpired =
			swapInWallet.lockedUntilRefundBalance.sat +
			swapInWallet.readyForRefundBalance.sat // + 1_000_000
	#else
		let fundsConfirmedExpired =
			swapInWallet.lockedUntilRefundBalance.sat +
			swapInWallet.readyForRefundBalance.sat
	#endif
		
		if fundsConfirmedExpired > 0 {
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				// Title line
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					
					Image(systemName: "x.circle")
					
					let formatted = Utils.format(currencyPrefs, sat: fundsConfirmedExpired)
					if formatted.currency.type == .bitcoin {
						Text("Expired: ") + Text(verbatim: "+\(formatted.string)")
					} else {
						Text("Expired: ") + Text(verbatim: "+≈\(formatted.string)")
					}
					
				} // </HStack>
				.font(.headline)
				
				// Explanation line
				Text("Cannot be swapped anymore, after 4 months waiting. These funds must be spent manually.")
					.font(.callout)
					.foregroundStyle(.secondary)
				
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(10)
			.background(Color(.secondarySystemBackground))
			.cornerRadius(16)
			.onTapGesture {
				didTapSection_swapInWallet()
			}
			.padding(.horizontal, 10)
		}
	}
	
	@ViewBuilder
	func group_finalWalletBalance() -> some View {
		
	#if DEBUG
		let finalWalletBalance = finalWallet.totalBalance.sat // + 1_000_000
	#else
		let finalWalletBalance = finalWallet.totalBalance.sat
	#endif
		
		if finalWalletBalance > 0 {
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				
				// Title line
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					
					Image(systemName: "link")
					
					let formatted = Utils.format(currencyPrefs, sat: finalWalletBalance)
					if formatted.currency.type == .bitcoin {
						Text("Final wallet: ") + Text(verbatim: "+\(formatted.string)")
					} else {
						Text("Final wallet: ") + Text(verbatim: "+≈\(formatted.string)")
					}
					
				} // </HStack>
				.font(.headline)
				
				// Explanation line
				Text("These funds come from closed Lightning channels. They must be spent manually.")
					.font(.callout)
					.foregroundStyle(.secondary)
				
			} // </VStack>
			.frame(maxWidth: .infinity, alignment: .leading)
			.padding(10)
			.background(Color(.secondarySystemBackground))
			.cornerRadius(16)
			.onTapGesture {
				didTapSection_finalWallet()
			}
			.padding(.horizontal, 10)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func maxSwapInFeeDetails() -> (FormattedAmount, Bool) {
		
		let absoluteMax: Int64 = liquidityPolicy.effectiveMaxFeeSats
		
		let readyForSwapBalance: Int64 = swapInWallet.deeplyConfirmedBalance.sat
		if readyForSwapBalance > 0 {
			
			let maxPercent: Double = Double(liquidityPolicy.effectiveMaxFeeBasisPoints) / Double(10_000)
			let percentMax: Int64 = Int64(Double(readyForSwapBalance) * maxPercent)
			
			if percentMax < absoluteMax {
				
				let formatted = Utils.formatBitcoin(currencyPrefs, sat: percentMax)
				return (formatted, true)
			}
		}
		
		let formatted = Utils.formatBitcoin(currencyPrefs, sat: absoluteMax)
		return (formatted, false)
	}
	
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
	
	func swapInWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("swapInWalletChanged()")
		
		swapInWallet = newValue
	}
	
	func finalWalletChanged(_ newValue: Lightning_kmpWalletState.WalletWithConfirmations) {
		log.trace("finalWalletChanged()")
		
		finalWallet = newValue
	}
	
	func pendingChannelsBalanceChanged(_ newValue: Lightning_kmpMilliSatoshi) {
		log.trace("pendingChannelsBalanceChanged()")
		
		pendingChannelsBalance = newValue
	}
	
	func liquidityPolicyChanged(_ newValue: LiquidityPolicy) {
		log.trace("liquidityPolicyChanged()")
		
		liquidityPolicy = newValue
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapSection_swapInWallet() {
		log.trace("didTapSection_swapInWallet()")
		
		popoverState.close()
		showSwapInWallet()
	}
	
	func didTapSection_finalWallet() {
		log.trace("didTapSection_finalWallet()")
		
		popoverState.close()
		showFinalWallet()
	}
}
