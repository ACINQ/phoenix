import SwiftUI
import PhoenixShared

class ParseResultHelper {
	
	static func processBadRequest(
		_ result: SendManager.ParseResult_BadRequest
	) -> Either<String, URL> {
		
		let msg: String
		var websiteLink: URL? = nil
		
		switch result.reason {
		case is SendManager.BadRequestReason_Expired:
			
			msg = String(
				localized: "Invoice is expired",
				comment: "Error message - scanning lightning invoice"
			)
			
		case let chainMismatch as SendManager.BadRequestReason_ChainMismatch:
			
			msg = String(
				localized: "The invoice is not for \(chainMismatch.expected.name)",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is SendManager.BadRequestReason_UnsupportedLnurl:
			
			msg = String(
				localized: "Phoenix does not support this type of LNURL yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is SendManager.BadRequestReason_AlreadyPaidInvoice:
			
			msg = String(
				localized: "You've already paid this invoice. Paying it again could result in stolen funds.",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is SendManager.BadRequestReason_PaymentPending:
			
			msg = String(
				localized: "This payment is already being processed. Please wait for it to complete.",
				comment: "Error message - scanning lightning invoice"
			)

		case is SendManager.BadRequestReason_Bip353InvalidOffer:
			
			msg = String(
				localized: "This address uses an invalid Bolt12 offer.",
				comment: "Error message - dns record contains an invalid offer"
			)

		case is SendManager.BadRequestReason_Bip353Unresolved:

			msg = String(
				localized: "Unable to retrieve data for this address. You may be experiencing a connectivity issue.",
				comment: "Error message - could not retrieve dns records for that address"
			)

		case is SendManager.BadRequestReason_Bip353NoDNSSEC:
			
			msg = String(
				localized: "This address is hosted on an unsecure DNS. DNSSEC must be enabled.",
				comment: "Error message - dns issue"
			)

		case let serviceError as SendManager.BadRequestReason_ServiceError:
			
			let remoteFailure: LnurlError.RemoteFailure = serviceError.error
			let origin = remoteFailure.origin
			
			switch remoteFailure {
			case is LnurlError.RemoteFailure_IsWebsite:
				websiteLink = URL(string: serviceError.url.description())
				msg = String(
					localized: "Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case is LnurlError.RemoteFailure_LightningAddressError:
				msg = String(
					localized: "The service (\(origin)) doesn't support Lightning addresses, or doesn't know this user",
					comment: "Error message - scanning lightning invoice"
				)
				
			case is LnurlError.RemoteFailure_CouldNotConnect:
				msg = String(
					localized: "Could not connect to service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case is LnurlError.RemoteFailure_Unreadable:
				msg = String(
					localized: "Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case let rfDetailed as LnurlError.RemoteFailure_Detailed:
				msg = String(
					localized: "The service (\(origin)) returned error message: \(rfDetailed.reason)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case let rfCode as LnurlError.RemoteFailure_Code:
				msg = String(
					localized: "The service (\(origin)) returned error code: \(rfCode.code.value)",
					comment: "Error message - scanning lightning invoice"
				)
				
			default:
				msg = String(
					localized: "The service (\(origin)) appears to be offline, or they have a down server",
					comment: "Error message - scanning lightning invoice"
				)
			}
			
		default:
			
			msg = String(
				localized: "This doesn't appear to be a Lightning invoice",
				comment: "Error message - scanning lightning invoice"
			)
		}
		
		if let websiteLink {
			return Either.Right(websiteLink)
		} else {
			return Either.Left(msg)
		}
	}
}
