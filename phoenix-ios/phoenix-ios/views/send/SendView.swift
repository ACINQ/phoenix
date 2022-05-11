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

	@State var paymentRequest: String? = nil
	
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
		     _ as Scan.Model_InvoiceFlow_DangerousRequest,
		     _ as Scan.Model_LnurlServiceFetch:

			ScanView(
				mvi: mvi,
				toast: toast,
				paymentRequest: $paymentRequest
			)
			.zIndex(4)

		case _ as Scan.Model_InvoiceFlow_InvoiceRequest,
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
		
		if let newModel = newModel as? Scan.Model_BadRequest {
			showErrorToast(newModel)
		}
		else if let model = newModel as? Scan.Model_InvoiceFlow_DangerousRequest {
			paymentRequest = model.request
		}
		else if let model = newModel as? Scan.Model_InvoiceFlow_InvoiceRequest {
			paymentRequest = model.request
		}
		else if newModel is Scan.Model_InvoiceFlow_Sending ||
		        newModel is Scan.Model_LnurlPayFlow_Sending
		{
			// Pop self from NavigationStack; Back to HomeView
			presentationMode.wrappedValue.dismiss()
		}
	}
	
	func showErrorToast(_ model: Scan.Model_BadRequest) -> Void {
		log.trace("showErrorToast()")
		
		let msg: String
		if let reason = model.reason as? Scan.BadRequestReason_ChainMismatch {
			
			let requestChain = reason.requestChain?.name ?? "unknown"
			msg = NSLocalizedString(
				"The invoice is for \(requestChain), but you're on \(reason.myChain.name)",
				comment: "Error message - scanning lightning invoice"
			)
		
		} else if model.reason is Scan.BadRequestReason_UnsupportedLnUrl {
			
			msg = NSLocalizedString(
				"Phoenix does not support this type of LNURL yet",
				comment: "Error message - scanning lightning invoice"
			)
			
		} else if model.reason is Scan.BadRequestReason_IsBitcoinAddress {
			
			msg = NSLocalizedString(
				"""
				You scanned a bitcoin address. Phoenix currently only supports sending Lightning payments. \
				You can use a third-party service to make the offchain->onchain swap.
				""",
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
			case is LNUrl.Error_RemoteFailure_CouldNotConnect:
				msg = NSLocalizedString(
					"Could not connect to service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
			case is LNUrl.Error_RemoteFailure_Unreadable:
				msg = NSLocalizedString(
					"Unreadable response from service: \(origin)",
					comment: "Error message - scanning lightning invoice"
				)
			default:
				// is LNUrl.Error_RemoteFailure_Code
				// is LNUrl.Error_RemoteFailure_Detailed
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
			Text(msg).multilineTextAlignment(.center).anyView,
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
