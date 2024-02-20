import Foundation
import PhoenixShared


extension WalletPaymentOrderRow {
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
	}
	
	var completedAtDate: Date? {
		if let completedAt = self.completedAt?.int64Value {
			return completedAt.toDate(from: .milliseconds)
		} else {
			return nil
		}
	}
	
	/// Models the sorting done in the raw Sqlite queries.
	/// I.e.: `ORDER BY COALESCE(completed_at, created_at)`
	///
	/// See: AggregatedQueries.sq
	///
	var sortDate: Date {
		return completedAtDate ?? createdAtDate
	}
}

extension WalletPaymentInfo {
	
	func paymentDescription(includingUserDescription: Bool = true) -> String? {
		
		let sanitize = { (input: String?) -> String? in
			
			if let trimmedInput = input?.trimmingCharacters(in: .whitespacesAndNewlines) {
				if !trimmedInput.isEmpty {
					return trimmedInput
				}
			}
			
			return nil
		}
		
		if includingUserDescription {
			if let description = sanitize(metadata.userDescription) {
				return description
			}
		}
		if let description = sanitize(metadata.lnurl?.description_) {
			return description
		}
		
		if let incomingPayment = payment as? Lightning_kmpIncomingPayment {
			
			if let invoice = incomingPayment.origin.asInvoice() {
				return sanitize(invoice.paymentRequest.description_)
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let lightningPayment = payment as? Lightning_kmpLightningOutgoingPayment {
			
				if let normal = lightningPayment.details.asNormal() {
					return sanitize(normal.paymentRequest.desc())
				} else if let swapOut = lightningPayment.details.asSwapOut() {
					return sanitize(swapOut.address)
				}
				
			} else if let spliceOut = outgoingPayment as? Lightning_kmpSpliceOutgoingPayment {
				return sanitize(spliceOut.address)
			}
		}
		
		return nil
	}
	
	func defaultPaymentDescription() -> String {
	
		if let incomingPayment = payment as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asKeySend() {
				return NSLocalizedString("Donation", comment: "Payment description for received KeySend")
			} else if let _ = incomingPayment.origin.asSwapIn() {
				return NSLocalizedString("On-chain deposit", comment: "Payment description for received deposit")
			} else if let _ = incomingPayment.origin.asOnChain() {
				return NSLocalizedString("On-chain deposit", comment: "Payment description for received deposit")
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let lightningPayment = outgoingPayment as? Lightning_kmpLightningOutgoingPayment {
				
				if let _ = lightningPayment.details.asKeySend() {
					return NSLocalizedString("Donation", comment: "Payment description for received KeySend")
				}
				
			} else if let _ = outgoingPayment as? Lightning_kmpChannelCloseOutgoingPayment {
				return NSLocalizedString("Channel closing", comment: "Payment description for channel closing")
				
			} else if let _ = outgoingPayment as? Lightning_kmpSpliceCpfpOutgoingPayment {
				return NSLocalizedString("Bump fees", comment: "Payment description for splice CPFP")
				
			} else if let il = outgoingPayment as? Lightning_kmpInboundLiquidityOutgoingPayment {
				let amount = Utils.formatBitcoin(sat: il._lease.amount, bitcoinUnit: .sat)
				return NSLocalizedString(
					"+\(amount.string) inbound liquidity",
					comment: "Payment description for inbound liquidity"
				)
			}
		}
	
		return NSLocalizedString("No description", comment: "placeholder text")
	}
}

extension WalletPaymentMetadata {
	
	static func empty() -> WalletPaymentMetadata {
		return WalletPaymentMetadata(
			lnurl: nil,
			originalFiat: nil,
			userDescription: nil,
			userNotes: nil,
			modifiedAt: nil
		)
	}
}

extension Lightning_kmpWalletPayment {

	func inIncoming() -> Bool {
		return self is Lightning_kmpIncomingPayment
	}

	func isOutgoing() -> Bool {
		return self is Lightning_kmpOutgoingPayment
	}
	
	func isOnChain() -> Bool {
		
		if let incomingPayment = self as? Lightning_kmpIncomingPayment {
			
			if let _ = incomingPayment.origin.asSwapIn() {
				return true
			} else if let _ = incomingPayment.origin.asOnChain() {
				return true
			}
			
		} else if let outgoingPayment = self as? Lightning_kmpLightningOutgoingPayment {
			
			if let _ = outgoingPayment.details.asSwapOut() {
				return true
			}
			
		} else if let _ = self as? Lightning_kmpChannelCloseOutgoingPayment {
			return true
			
		} else if let _ = self as? Lightning_kmpSpliceOutgoingPayment {
			return true
		}
		
		return false
	}
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
	}
	
	var completedAtDate: Date? {
		
		if let millis = completedAt?.int64Value {
			return millis.toDate(from: .milliseconds)
		} else {
			return nil
		}
	}
}

extension Lightning_kmpIncomingPayment {
	
	var isSpliceIn: Bool {
		
		guard
			let _ = self.origin.asOnChain(),
			let received = self.received
		else {
			return false
		}
		
		return received.receivedWith.contains {
			if let _ = $0.asSpliceIn() {
				return true
			} else {
				return false
			}
		}
	}
}

extension Lightning_kmpIncomingPayment.Received {
	
	var receivedAtDate: Date {
		return receivedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.StatusCompletedSucceededOffChain {
	
	var completedAtDate: Date {
		return completedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.StatusCompletedFailed {
	
	var completedAtDate: Date {
		return completedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.Part {
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.PartStatusSucceeded {
	
	var completedAtDate: Date {
		return completedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.PartStatusFailed {
	
	var completedAtDate: Date {
		return completedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpOnChainOutgoingPayment {
	
	var confirmedAtDate: Date? {
		if let millis = confirmedAt?.int64Value {
			return millis.toDate(from: .milliseconds)
		} else {
			return nil
		}
	}
}

extension Lightning_kmpBolt11Invoice {
	
	var timestampDate: Date {
		return timestampSeconds.toDate(from: .seconds)
	}
}
