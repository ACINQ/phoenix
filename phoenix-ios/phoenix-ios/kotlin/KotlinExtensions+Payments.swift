import Foundation
import PhoenixShared

extension PaymentsPage {
	
	func forceRefresh() -> PaymentsPage {
		
		// What we want to do is ensure a fetch (via the PaymentsFetcher) will return
		// a new version of the payment (+ metadata + contact).
		// To accomplish this we simply tweak `metadataModifiedAt` for each item.
		
		let newRows: [WalletPaymentOrderRow] = self.rows.map { (row: WalletPaymentOrderRow) in
			
			let newMetadataModifiedAt: Int64
			if let oldMetadataModifiedAt = row.metadataModifiedAt {
				newMetadataModifiedAt = oldMetadataModifiedAt.int64Value + 1
			} else {
				newMetadataModifiedAt = 0
			}
			
			return WalletPaymentOrderRow(
				id: row.kotlinId(),
				createdAt: row.createdAt,
				completedAt: row.completedAt,
				metadataModifiedAt: KotlinLong(value: newMetadataModifiedAt)
			)
		}
		
		return PaymentsPage(offset: self.offset, count: self.count, rows: newRows)
	}
}

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
			
			if let invoice = incomingPayment.origin.asInvoice() {
				return sanitize(invoice.paymentRequest.description_)
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
			
			if let _ = incomingPayment.origin.asSwapIn() {
				return String(localized: "On-chain deposit", comment: "Payment description for received deposit")
			} else if let _ = incomingPayment.origin.asOnChain() {
				return String(localized: "On-chain deposit", comment: "Payment description for received deposit")
			}
			
		} else if let outgoingPayment = payment as? Lightning_kmpOutgoingPayment {
			
			if let _ = outgoingPayment as? Lightning_kmpChannelCloseOutgoingPayment {
				return String(localized: "Channel closing", comment: "Payment description for channel closing")
				
			} else if let _ = outgoingPayment as? Lightning_kmpSpliceCpfpOutgoingPayment {
				return String(localized: "Bump fees", comment: "Payment description for splice CPFP")
				
			} else if let il = outgoingPayment as? Lightning_kmpInboundLiquidityOutgoingPayment {
				let amount = Utils.formatBitcoin(sat: il.purchase.amount, bitcoinUnit: .sat)
				return String(
					localized: "+\(amount.string) inbound liquidity",
					comment: "Payment description for inbound liquidity"
				)
			}
		}
	
		return String(localized: "No description", comment: "placeholder text")
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

	func isIncoming() -> Bool {
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
