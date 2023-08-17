import SwiftUI
import Combine
import AVFoundation
import PhoenixShared
import UIKit
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "SendView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct SendView: MVIView {
	
	@StateObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	init(controller: AppScanController? = nil) {
		
		if let controller = controller {
			self._mvi = StateObject(wrappedValue: MVIState(controller))
		} else {
			self._mvi = StateObject(wrappedValue: MVIState {
				$0.scan(firstModel: Scan.Model_Ready())
			})
		}
	}
	
	@ViewBuilder
	var view: some View {
		
		ZStack {
			content
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
	var content: some View {
		
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

			ScanView(mvi: mvi, toast: toast)
				.zIndex(4)

		case _ as Scan.Model_InvoiceFlow_InvoiceRequest,
		     _ as Scan.Model_OnChainFlow,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayRequest,
		     _ as Scan.Model_LnurlPayFlow_LnurlPayFetch,
		     _ as Scan.Model_LnurlWithdrawFlow_LnurlWithdrawRequest,
		     _ as Scan.Model_LnurlWithdrawFlow_LnurlWithdrawFetch:

			ValidateView(mvi: mvi)
				.zIndex(3)

		case _ as Scan.Model_InvoiceFlow_Sending,
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
	
	func modelDidChange(_ newModel: Scan.Model) {
		log.trace("modelDidChange()")
		
		switch newModel {
		case let model as Scan.Model_BadRequest:
			
			showErrorToast(model)
			
		case is Scan.Model_InvoiceFlow_Sending,
		     is Scan.Model_LnurlPayFlow_Sending:
			
			// Pop self from NavigationStack; Back to HomeView
			presentationMode.wrappedValue.dismiss()
			
		default:
			break
		}
	}
	
	func showErrorToast(_ model: Scan.Model_BadRequest) -> Void {
		log.trace("showErrorToast()")
		
		let msg: String
		if model.reason is Scan.BadRequestReason_Expired {
			
			msg = NSLocalizedString(
				"Invoice is expired",
				comment: "Error message - scanning lightning invoice"
			)
			
		} else if let reason = model.reason as? Scan.BadRequestReason_ChainMismatch {
			
			msg = NSLocalizedString(
				"The invoice is not for \(reason.expected.name)",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else if model.reason is Scan.BadRequestReason_UnsupportedLnurl {
			
			msg = NSLocalizedString(
				"Phoenix does not support this type of LNURL yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		} else if model.reason is Scan.BadRequestReason_AlreadyPaidInvoice {
			
			msg = NSLocalizedString(
				"You've already paid this invoice. Paying it again could result in stolen funds.",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else if let serviceError = model.reason as? Scan.BadRequestReason_ServiceError {
			
			let isLightningAddress = serviceError.url.description.contains("/.well-known/lnurlp/")
			let origin = serviceError.error.origin
			
			switch serviceError.error {
			case is LnurlError.RemoteFailure_CouldNotConnect:
				msg = NSLocalizedString(
					"Could not connect to service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
			case is LnurlError.RemoteFailure_Unreadable:
				msg = NSLocalizedString(
					"Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
			default:
				// is LnurlError.RemoteFailure_Code
				// is LnurlError.RemoteFailure_Detailed
				if isLightningAddress {
					msg = NSLocalizedString(
						"The service (\(origin)) doesn't support Lightning addresses, or doesn't know this user",
						comment: "Error message - scanning lightning invoice"
					)
				} else {
					msg = NSLocalizedString(
						"The service (\(origin)) appears to be offline, or they have a down server",
						comment: "Error message - scanning lightning invoice"
					)
				}
			}
			
		} else {
		
			msg = NSLocalizedString(
				"This doesn't appear to be a Lightning invoice",
				comment: "Error message - scanning lightning invoice"
			)
		}
		toast.pop(
			msg,
			colorScheme: colorScheme.opposite,
			style: .chrome,
			duration: 30.0,
			alignment: .middle,
			showCloseButton: true
		)
	}
	
	func didReceiveExternalLightningUrl(_ urlStr: String) -> Void {
		log.trace("didReceiveExternalLightningUrl()")
		
		mvi.intent(Scan.Intent_Parse(request: urlStr))
	}
}
