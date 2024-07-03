import SwiftUI
import Combine
import AVFoundation
import PhoenixShared
import UIKit

fileprivate let filename = "SendView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct SendView: MVIView {
	
	enum Location {
		case MainView
		case ReceiveView
	}
	
	let location: Location
	
	@StateObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@State var needsAcceptWarning = true
	
	@StateObject var toast = Toast()
	
	@EnvironmentObject var popoverState: PopoverState
	
	@Environment(\.openURL) var openURL
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(location: Location, controller: AppScanController? = nil) {
		
		self.location = location
		
		if let controller = controller {
			self._mvi = StateObject(wrappedValue: MVIState(controller))
		} else {
			self._mvi = StateObject(wrappedValue: MVIState {
				$0.scan(firstModel: Scan.Model_Ready())
			})
		}
	}
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			if location == .ReceiveView && needsAcceptWarning {
				content_limited()
			} else {
				content_normal()
			}
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onChange(of: mvi.model) { newModel in
			modelDidChange(newModel)
		}
		.onReceive(AppDelegate.get().externalLightningUrlPublisher) { (url: String) in
			didReceiveExternalLightningUrl(url)
		}
	}

	@ViewBuilder
	func content_normal() -> some View {
		
		// ZIndex: [
		//   0: LoginView
		//   1: PaymentRequestedView
		//   2: PaymentInFlightView
		//   3: ValidateView
		//   4: ScanView
		// ]
		
		switch mvi.model {
		case _ as Scan.Model_Ready,
		     _ as Scan.Model_BadRequest,
		     _ as Scan.Model_LnurlServiceFetch:

			ScanView(location: location, mvi: mvi, toast: toast)
				.zIndex(4)

		case _ as Scan.Model_Bolt11InvoiceFlow_InvoiceRequest,
		     _ as Scan.Model_OfferFlow,
		     _ as Scan.Model_OnChainFlow,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayRequest,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayFetch,
		     _ as Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest,
		     _ as Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch:

			ValidateView(mvi: mvi)
				.zIndex(3)

		case _ as Scan.Model_Bolt11InvoiceFlow_Sending,
		     _ as Scan.Model_LnurlPayFlow_Sending:

			PaymentInFlightView(mvi: mvi)
				.zIndex(2)
			
		case _ as Scan.Model_LnurlWithdrawFlow_Receiving:
			
			PaymentRequestedView(mvi: mvi)
				.zIndex(1)

		case _ as Scan.Model_LnurlAuthFlow_LoginRequest,
		     _ as Scan.Model_LnurlAuthFlow_LoggingIn,
		     _ as Scan.Model_LnurlAuthFlow_LoginResult:

			LoginView(mvi: mvi)
				.zIndex(0)

		default:
			fatalError("Unknown model \(mvi.model)")
		}
	}
	
	@ViewBuilder
	func content_limited() -> some View {
		
		ScanView(location: location, mvi: mvi, toast: toast)
			.zIndex(4)
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func modelDidChange(_ newModel: Scan.Model) {
		log.trace("modelDidChange()")
		
		switch newModel {
		case let model as Scan.Model_BadRequest:
			
			showErrorToast(model)
		
		case _ as Scan.Model_LnurlWithdrawFlow,
		     _ as Scan.Model_LnurlAuthFlow:
			
			if location == .ReceiveView {
				needsAcceptWarning = false
			}
			
		case _ as Scan.Model_Bolt11InvoiceFlow_InvoiceRequest,
		     _ as Scan.Model_OnChainFlow,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayRequest,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayFetch:
			
			if location == .ReceiveView {
				showSendPaymentWarning()
			}
			
		case is Scan.Model_Bolt11InvoiceFlow_Sending,
		     is Scan.Model_LnurlPayFlow_Sending:
			
			// Pop self from NavigationStack; Back to HomeView
			presentationMode.wrappedValue.dismiss()
			
		default:
			break
		}
	}
	
	func didReceiveExternalLightningUrl(_ urlStr: String) {
		log.trace("didReceiveExternalLightningUrl()")
		
		mvi.intent(Scan.Intent_Parse(request: urlStr))
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func showErrorToast(_ model: Scan.Model_BadRequest) -> Void {
		log.trace("showErrorToast()")
		
		let msg: String
		var websiteLink: URL? = nil
		
		switch model.reason {
		case is Scan.BadRequestReason_Expired:
			
			msg = NSLocalizedString(
				"Invoice is expired",
				comment: "Error message - scanning lightning invoice"
			)
			
		case let chainMismatch as Scan.BadRequestReason_ChainMismatch:
			
			msg = NSLocalizedString(
				"The invoice is not for \(chainMismatch.expected.name)",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is Scan.BadRequestReason_UnsupportedLnurl:
			
			msg = NSLocalizedString(
				"Phoenix does not support this type of LNURL yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		case is Scan.BadRequestReason_AlreadyPaidInvoice:
			
			msg = NSLocalizedString(
				"You've already paid this invoice. Paying it again could result in stolen funds.",
				comment: "Error message - scanning lightning invoice"
			)
			
		case let serviceError as Scan.BadRequestReason_ServiceError:
			
			let remoteFailure: LnurlError.RemoteFailure = serviceError.error
			let origin = remoteFailure.origin
			
			let isLightningAddress = serviceError.url.description.contains("/.well-known/lnurlp/")
			let lightningAddressErrorMessage = NSLocalizedString(
				"The service (\(origin)) doesn't support Lightning addresses, or doesn't know this user",
				comment: "Error message - scanning lightning invoice"
			)
			
			switch remoteFailure {
			case is LnurlError.RemoteFailure_CouldNotConnect:
				msg = NSLocalizedString(
					"Could not connect to service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case is LnurlError.RemoteFailure_Unreadable:
				let scheme = serviceError.url.protocol.name.lowercased()
				if scheme == "https" || scheme == "http" {
					websiteLink = URL(string: serviceError.url.description())
				}
				msg = NSLocalizedString(
					"Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
				
			case let rfDetailed as LnurlError.RemoteFailure_Detailed:
				if isLightningAddress {
					msg = lightningAddressErrorMessage
				} else {
					msg = NSLocalizedString(
						"The service (\(origin)) returned error message: \(rfDetailed.reason)",
						comment: "Error message - scanning lightning invoice"
					)
				}
				
			case let rfCode as LnurlError.RemoteFailure_Code:
				if isLightningAddress {
					msg = lightningAddressErrorMessage
				} else {
					msg = NSLocalizedString(
						"The service (\(origin)) returned error code: \(rfCode.code.value)",
						comment: "Error message - scanning lightning invoice"
					)
				}
				
			default:
				msg = NSLocalizedString(
					"The service (\(origin)) appears to be offline, or they have a down server",
					comment: "Error message - scanning lightning invoice"
				)
			}
			
		default:
			
			msg = NSLocalizedString(
				"This doesn't appear to be a Lightning invoice",
				comment: "Error message - scanning lightning invoice"
			)
		}
		
		if let websiteLink {
			popoverState.display(dismissable: true) {
				WebsiteLinkPopover(
					link: websiteLink,
					copyAction: copyLink,
					openAction: openLink
				)
			}
			
		} else {
			toast.pop(
				msg,
				colorScheme: colorScheme.opposite,
				style: .chrome,
				duration: 30.0,
				alignment: .middle,
				showCloseButton: true
			)
		}
	}
	
	func showSendPaymentWarning() {
		log.trace("showSendPaymentWarning()")
		
		popoverState.display(dismissable: false) {
			PaymentWarningPopover(
				cancelAction: {
					mvi.intent(Scan.IntentReset())
				},
				continueAction: {
					needsAcceptWarning = false
				}
			)
		}
	}
	
	func copyLink(_ url: URL) {
		log.trace("copyLink()")
		
		UIPasteboard.general.string = url.absoluteString
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite
		)
	}
	
	func openLink(_ url: URL) {
		log.trace("openLink()")
		
		openURL(url)
	}
}
