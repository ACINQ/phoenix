import Foundation
import PhoenixShared

fileprivate let filename = "WalletPaymentExtensions"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

extension Lightning_kmpWalletPayment {
	
	func paymentType() -> (String, String)? {
		
		// Will be displayed in the UI as:
		//
		// Type : value (explanation)
		//
		// where return value is: (value, explanation)
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment as? Lightning_kmpLegacySwapInIncomingPayment {
				let val = NSLocalizedString("Swap-In", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 1 -> 2", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			if let _ = incomingPayment as? Lightning_kmpSpliceInIncomingPayment {
				let val = NSLocalizedString("Splice-In", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("adding to existing channel", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
			
			if let _ = outgoingPayment.details.asSwapOut() {
				let val = NSLocalizedString("Swap-Out", comment: "Transaction Info: Value")
				let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
				return (val, exp.lowercased())
			}
			
		} else if let _ = self as? Lightning_kmpChannelCloseOutgoingPayment {
			
			let val = NSLocalizedString("Channel Closing", comment: "Transaction Info: Value")
			let exp = NSLocalizedString("layer 2 -> 1", comment: "Transaction Info: Explanation")
			return (val, exp.lowercased())
		}
		
		return nil
	}
	
	func paymentLink() -> URL? {
		
		var address: String? = nil
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
		
			if let swapIn = incomingPayment as? Lightning_kmpLegacySwapInIncomingPayment {
				address = swapIn.address
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
		
			if let swapOut = outgoingPayment.details.asSwapOut() {
				address = swapOut.address
			}
		}
		
		if let address = address {
			let str: String
			if BusinessManager.isTestnet {
				str = "https://mempool.space/testnet/address/\(address)"
			} else {
				str = "https://mempool.space/address/\(address)"
			}
			return URL(string: str)
		}
		
		return nil
	}
	
	func channelClosing() -> Lightning_kmpChannelCloseOutgoingPayment? {
		
		return self as? Lightning_kmpChannelCloseOutgoingPayment
	}
	
	func standardFees() -> (Int64, String, String)? {
		
		if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
		
			let msat = outgoingPayment.routingFee.msat // excludes swapOutFee
			if msat == 0 {
				return nil
			}
			
			var parts = 0
			var hops = 0
			for part in outgoingPayment.parts {
				parts += 1
				hops += part.route.count
			}
			
			let title = String(localized: "Lightning Fees", comment: "Label in SummaryInfoGrid")
			let exp: String
			if parts == 1 {
				if hops == 1 {
					exp = String(
						localized: "Lightning fees for routing the payment. Payment required 1 hop.",
						comment: "Fees explanation"
					)
				} else {
					exp = String(
						localized: "Lightning fees for routing the payment. Payment required \(hops) hops.",
						comment: "Fees explanation"
					)
				}
				
			} else {
				exp = String(localized:
					"""
					Lightning fees for routing the payment. \
					Payment was divided into \(parts) parts, using \(hops) hops.
					""",
					comment: "Fees explanation"
				)
			}
			
			return (msat, title, exp)
		}
		
		return nil
	}
	
	func minerFees() -> (Int64, String, String)? {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			var sat: Int64 = 0
			if let newChannel = incomingPayment as? Lightning_kmpNewChannelIncomingPayment {
				
				sat = newChannel.miningFee.sat
				
			} else if let spliceIn = incomingPayment as? Lightning_kmpSpliceInIncomingPayment {
				
				sat = spliceIn.miningFee.sat
				
			} else if let lightningIncoming = incomingPayment as? Lightning_kmpLightningIncomingPayment {

				if let purchaseDetails = lightningIncoming.liquidityPurchaseDetails {

					sat = purchaseDetails.miningFee.sat

				}
			}
			
			if sat > 0 {
				
				let msat = Utils.toMsat(sat: sat)
				let title = String(localized: "Miner Fees", comment: "Label in SummaryInfoGrid")
				let exp = String(
					localized: "Bitcoin network fees paid for on-chain transaction.",
					comment: "Fees explanation"
				)
				
				return (msat, title, exp)
			}
			
		} else if let channelCloseOutgoingPayment = self as? Lightning_kmpChannelCloseOutgoingPayment {

			let msat = channelCloseOutgoingPayment.fees.msat

			let title = String(localized: "Miner Fees", comment: "Label in SummaryInfoGrid")
			let exp = String(
				localized: "Bitcoin network fees paid for on-chain transaction.",
				comment: "Fees explanation"
			)

			return (msat, title, exp)
			
		} else if let onChainOutgoingPayment = self as? Lightning_kmpOnChainOutgoingPayment {
			
			let sat = onChainOutgoingPayment.miningFee.sat
			let msat = Utils.toMsat(sat: sat)
			
			let title = String(localized: "Miner Fees", comment: "Label in SummaryInfoGrid")
			let exp = String(
				localized: "Bitcoin network fees paid for on-chain transaction.",
				comment: "Fees explanation"
			)
			
			return (msat, title, exp)
		}
		
		return nil
	}
	
	func serviceFees() -> (Int64, String, String)? {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {

			if let lightningIncoming = incomingPayment as? Lightning_kmpLightningIncomingPayment {
				if let purchaseDetails = lightningIncoming.liquidityPurchaseDetails {

					let sat = purchaseDetails.purchase.fees.serviceFee
					let msat = Utils.toMsat(sat: sat)

					let title = String(localized: "Service Fees", comment: "Label in SummaryInfoGrid")
					let exp = String(
						localized: "Fees paid for the liquidity service.",
						comment: "Fees explanation"
					)

					return (msat, title, exp)
				}
			}

		} else if let lp = self as? Lightning_kmpAutomaticLiquidityPurchasePayment {
			
			let sat = lp.liquidityPurchase.fees.serviceFee
			let msat = Utils.toMsat(sat: sat)
			
			let title = String(localized: "Service Fees", comment: "Label in SummaryInfoGrid")
			let exp = String(
				localized: "Fees paid for the liquidity service.",
				comment: "Fees explanation"
			)
			
			return (msat, title, exp)
			
		} else if let lp = self as? Lightning_kmpManualLiquidityPurchasePayment {
			
			let sat = lp.liquidityPurchase.fees.serviceFee
			let msat = Utils.toMsat(sat: sat)
			
			let title = String(localized: "Service Fees", comment: "Label in SummaryInfoGrid")
			let exp = String(
				localized: "Fees paid for the liquidity service.",
				comment: "Fees explanation"
			)
			
			return (msat, title, exp)
			
		} else if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment,
		          let _ = outgoingPayment.details.asSwapOut()
		{
			let msat = outgoingPayment.fees.msat - outgoingPayment.routingFee.msat
			
			let title = String(localized: "Swap Fees", comment: "Label in SummaryInfoGrid")
			let exp = String(
				localized: "Includes Bitcoin network miner fees, and the fee for the Swap-Out service.",
				comment: "Fees explanation"
			)
			
			return (msat, title, exp)
		}
		
		return nil
	}
	
	/// If the OutgoingPayment succeeded or failed, reports the total elapsed time.
	/// The return value is in number of milliseconds.
	///
	func paymentTimeElapsed() -> Int64? {

		if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
			
			let started = outgoingPayment.createdAt
			var finished: Int64? = nil
			
			if let failed = outgoingPayment.status.asFailed() {
				finished = failed.completedAt
				
			} else if let succeeded = outgoingPayment.status.asSucceeded() {
				finished = succeeded.completedAt
			}
			
			if let finished = finished, finished > started {
				return finished - started
			}
		}
		
		return nil
	}
	
	func paymentFinalError() -> String? {

		if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
			
			if let failed = outgoingPayment.status.asFailed() {
				
				return failed.reason.description
			}
		}
		
		return nil
	}
}
