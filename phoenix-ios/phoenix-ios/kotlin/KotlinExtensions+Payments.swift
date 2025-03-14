import Foundation
import PhoenixShared

extension WalletPaymentInfo {
	
	struct PaymentDescriptionOptions: OptionSet, CustomStringConvertible {
		
		let rawValue: Int

		static let userDescription       = PaymentDescriptionOptions(rawValue: 1 << 0)
		static let incomingBolt12Message = PaymentDescriptionOptions(rawValue: 1 << 1)
		static let knownContact          = PaymentDescriptionOptions(rawValue: 1 << 2)

		static let all: PaymentDescriptionOptions = [.userDescription, .incomingBolt12Message, .knownContact]
		static let none: PaymentDescriptionOptions = []
		
		var description: String {
			var items = [String]()
			items.reserveCapacity(3)
			if contains(.userDescription) {
				items.append("userDescription")
			}
			if contains(.incomingBolt12Message) {
				items.append("incomingBolt12Message")
			}
			if contains(.knownContact) {
				items.append("knownContact")
			}
			return "[\(items.joined(separator: ","))]"
		}
	}
	
	func paymentDescription(options: PaymentDescriptionOptions = .all) -> String? {
		
		let sanitize = { (input: String?) -> String? in
			
			if let trimmedInput = input?.trimmingCharacters(in: .whitespacesAndNewlines) {
				if !trimmedInput.isEmpty {
					return trimmedInput
				}
			}
			
			return nil
		}
		
		if options.contains(.userDescription) {
			if let result = sanitize(metadata.userDescription) {
				return result
			}
		}
		if let contact {
			if options.contains(.incomingBolt12Message) {
				if payment.isIncoming(), let msg = attachedMessage() {
					return msg // only incoming messages from **known contacts**
				}
			}
			if options.contains(.knownContact) {
				if payment.isIncoming() {
					return String(localized: "Payment from \(contact.name)")
				} else {
					return String(localized: "Payment to \(contact.name)")
				}
			}
		}
		if let result = sanitize(metadata.lnurl?.description_) {
			return result
		}
		
		if let incomingPayment = payment as? Lightning_kmpIncomingPayment {
			
			if let bolt11 = incomingPayment as? Lightning_kmpBolt11IncomingPayment {
				return sanitize(bolt11.paymentRequest.description_)
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let lightningPayment = outgoingPayment as? Lightning_kmpLightningOutgoingPayment {
			
				if let normal = lightningPayment.details.asNormal() {
					return sanitize(normal.paymentRequest.desc)
					
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
			
			if let _ = incomingPayment as? Lightning_kmpOnChainIncomingPayment {
				return String(localized: "On-chain deposit", comment: "Payment description for received deposit")
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment as? Lightning_kmpChannelCloseOutgoingPayment {
				return String(localized: "Channel closing", comment: "Payment description for channel closing")
				
			} else if let _ = outgoingPayment as? Lightning_kmpSpliceCpfpOutgoingPayment {
				return String(localized: "Bump fees", comment: "Payment description for splice CPFP")
				
			} else if let _ = outgoingPayment as? Lightning_kmpManualLiquidityPurchasePayment {
				return String(localized: "Manual liquidity", comment: "Payment description for inbound liquidity")
				
			} else if let _ = outgoingPayment as? Lightning_kmpAutomaticLiquidityPurchasePayment {
				return String(localized: "Channel management", comment: "Payment description for inbound liquidity")
			}
		}
	
		return String(localized: "No description", comment: "placeholder text")
	}
	
	func hasAttachedMessage() -> Bool {
		return attachedMessage() != nil
	}
	
	func attachedMessage() -> String? {
		
		var msg: String? = nil
		
		if let incomingOfferMetadata = payment.incomingOfferMetadata() {
			msg = incomingOfferMetadata.payerNote
			
		} else if let outgoingInvoiceRequest = payment.outgoingInvoiceRequest() {
			msg = outgoingInvoiceRequest.payerNote
		}
		
		if var msg {
			msg = msg.trimmingCharacters(in: .whitespacesAndNewlines)
			return msg.isEmpty ? nil : msg
		} else {
			return nil
		}
	}
	
	func canAddToContacts() -> Bool {
		return addToContactsInfo() != nil
	}
	
	func addToContactsInfo() -> AddToContactsInfo? {
	
		if payment is Lightning_kmpOutgoingPayment {
			
			// First check for a lightning address.
			// Remember that an outgoing payment might have both an address & offer (i.e. BIP-353).
			// But from the user's perspective, they sent a payment to the address.
			// The fact that it used an offer under-the-hood is just a technicality.
			// What they expect to save is the lightning address.
			//
			// Note: in the future we may support something like "offer pinning" for an LN address.
			// But that's a different feature. The user's perspective remains the same.
			//
			if let address = self.metadata.lightningAddress {
				return AddToContactsInfo(offer: nil, address: address)
			}
			
			let invoiceRequest = payment.outgoingInvoiceRequest()
			if let offer = invoiceRequest?.offer {
				return AddToContactsInfo(offer: offer, address: nil)
			}
		}

		return nil
	}
}

extension WalletPaymentMetadata {
	
	static func empty() -> WalletPaymentMetadata {
		return WalletPaymentMetadata(
			lnurl: nil,
			originalFiat: nil,
			userDescription: nil,
			userNotes: nil,
			lightningAddress: nil,
			modifiedAt: nil
		)
	}
}

extension Lightning_kmpWalletPayment {

	func isIncoming() -> Bool {
		return self is Lightning_kmpIncomingPayment
	}

	func isOutgoing() -> Bool {
		return self is Lightning_kmpOutgoingPayment
	}
	
	func isOnChain() -> Bool {
		
		if self is Lightning_kmpOnChainIncomingPayment {
			return true
		}
		if self is Lightning_kmpOnChainOutgoingPayment {
			return true
		}
		
		return false
	}
	
	var createdAtDate: Date {
		return createdAt.toDate(from: .milliseconds)
	}
	
	var completedAtDate: Date? {
		return completedAt?.int64Value.toDate(from: .milliseconds)
	}
	
	var sortDate: Date {
		return completedAtDate ?? createdAtDate
	}
}

extension Lightning_kmpIncomingPayment {
	
	var isSpliceIn: Bool {
		return (self is Lightning_kmpSpliceInIncomingPayment)
	}
	
	var lightningPaymentFundingTxId: Bitcoin_kmpTxId? {
		if let lp = self as? Lightning_kmpLightningIncomingPayment {
			for part in lp.parts {
				if let htlc = part as? Lightning_kmpLightningIncomingPayment.PartHtlc {
					if let fundingFee = htlc.fundingFee {
						return fundingFee.fundingTxId
					}
				}
			}
		}
		return nil
	}
	
	var isLightningPaymentWithFundingTxId: Bool {
		return lightningPaymentFundingTxId != nil
	}
}

extension Lightning_kmpLightningIncomingPayment.Part {
	
	var receivedAtDate: Date {
		return self.receivedAt.toDate(from: .milliseconds)
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

extension Lightning_kmpLightningOutgoingPayment.StatusSucceeded {
	
	var completedAtDate: Date {
		return completedAt.toDate(from: .milliseconds)
	}
}

extension Lightning_kmpLightningOutgoingPayment.StatusFailed {
	
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

extension Lightning_kmpLiquidityAdsLiquidityTransactionDetails {
	
	var hidesFees: Bool {
		return (self.feePaidFromChannelBalance.total.sat <= 0)
	}
}
