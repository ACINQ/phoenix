import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LnurlFlowErrorNotice"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


enum LnurlFlowError {
	case pay(error: Scan.LnurlPay_Error)
	case withdraw(error: Scan.LnurlWithdraw_Error)
}

struct LnurlFlowErrorNotice: View {
	
	let error: LnurlFlowError
	
	@EnvironmentObject var popoverState: PopoverState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Image(systemName: "exclamationmark.triangle")
					.imageScale(.medium)
					.padding(.trailing, 6)
					.foregroundColor(Color.appNegative)
				
				Text(title())
					.font(.headline)
				
				Spacer()
				
				Button {
					closeButtonTapped()
				} label: {
					Image("ic_cross")
						.resizable()
						.frame(width: 30, height: 30)
				}
				.accessibilityLabel("Close")
				.accessibilityHidden(popoverState.publisher.value?.dismissable ?? false)
			}
			.padding(.horizontal)
			.padding(.vertical, 8)
			.background(
				Color(UIColor.secondarySystemBackground)
					.cornerRadius(15, corners: [.topLeft, .topRight])
			)
			.padding(.bottom, 4)
			
			content
		}
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
		
			errorMessage()
		}
		.padding()
	}
	
	@ViewBuilder
	func errorMessage() -> some View {
		
		switch error {
		case .pay(let payError):
			errorMessage(payError)
		case .withdraw(let withdrawError):
			errorMessage(withdrawError)
		}
	}
	
	@ViewBuilder
	func errorMessage(_ payError: Scan.LnurlPay_Error) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			if let remoteError = payError as? Scan.LnurlPay_Error_RemoteError {
				
				errorMessage(remoteError.err)
				
			} else if let err = payError as? Scan.LnurlPay_Error_BadResponseError {
				
				if let details = err.err as? LnurlError.Pay_Invoice_Malformed {
					
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Malformed: \(details.context)")
						.font(.system(.subheadline, design: .monospaced))
					
				} else if let details = err.err as? LnurlError.Pay_Invoice_InvalidAmount {
				 
					Text("Host: \(details.origin)")
						.font(.system(.subheadline, design: .monospaced))
					Text("Error: invalid amount")
						.font(.system(.subheadline, design: .monospaced))
					
				} else {
					genericErrorMessage()
				}
			 
			} else if let err = payError as? Scan.LnurlPay_Error_ChainMismatch {
				
				Text("The invoice is not for \(err.expected.name)")
				
			} else if let _ = payError as? Scan.LnurlPay_Error_AlreadyPaidInvoice {
				
				Text("You have already paid this invoice.")
				
		 	} else {
				genericErrorMessage()
			}
		}
	}
	
	@ViewBuilder
	func errorMessage(_ withdrawError: Scan.LnurlWithdraw_Error) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			if let remoteError = withdrawError as? Scan.LnurlWithdraw_Error_RemoteError {
				
				errorMessage(remoteError.err)
				
			} else {
				genericErrorMessage()
			}
		}
	}
	
	@ViewBuilder
	func errorMessage(_ remoteFailure: LnurlError.RemoteFailure) -> some View {
		
		if let _ = remoteFailure as? LnurlError.RemoteFailure_CouldNotConnect {
			
			Text("Could not connect to host:")
			Text(remoteFailure.origin)
				.font(.system(.subheadline, design: .monospaced))
		
		} else if let details = remoteFailure as? LnurlError.RemoteFailure_Code {
			
			Text("Host returned status code \(details.code.value):")
			Text(remoteFailure.origin)
				.font(.system(.subheadline, design: .monospaced))
		 
		} else if let details = remoteFailure as? LnurlError.RemoteFailure_Detailed {
		
			Text("Host returned error response.")
			Text("Host: \(details.origin)")
				.font(.system(.subheadline, design: .monospaced))
			Text("Error: \(details.reason)")
				.font(.system(.subheadline, design: .monospaced))
	 
		} else if let _ = remoteFailure as? LnurlError.RemoteFailure_Unreadable {
		
			Text("Host returned unreadable response:", comment: "error details")
			Text(remoteFailure.origin)
				.font(.system(.subheadline, design: .monospaced))
			
		} else {
			genericErrorMessage()
		}
	}
	
	@ViewBuilder
	func genericErrorMessage() -> some View {
		
		Text("Please try again")
	}
	
	private func title() -> String {
		
		switch error {
		case .pay(let payError):
			return title(payError)
		case .withdraw(let withdrawError):
			return title(withdrawError)
		}
	}
	
	private func title(_ payError: Scan.LnurlPay_Error) -> String {
		
		if let remoteErr = payError as? Scan.LnurlPay_Error_RemoteError {
			return title(remoteErr.err)
			
		} else if let _ = payError as? Scan.LnurlPay_Error_BadResponseError {
			return NSLocalizedString("Invalid response", comment: "Error title")
			
		} else if let _ = payError as? Scan.LnurlPay_Error_ChainMismatch {
			return NSLocalizedString("Chain mismatch", comment: "Error title")
			
		} else if let _ = payError as? Scan.LnurlPay_Error_AlreadyPaidInvoice {
			return NSLocalizedString("Already paid", comment: "Error title")
			
		} else {
			return NSLocalizedString("Unknown error", comment: "Error title")
		}
	}
	
	private func title(_ withdrawError: Scan.LnurlWithdraw_Error) -> String {
		
		if let remoteErr = withdrawError as? Scan.LnurlWithdraw_Error_RemoteError {
			return title(remoteErr.err)
			
		} else {
			return NSLocalizedString("Unknown error", comment: "Error title")
		}
	}
	
	private func title(_ remoteFailure: LnurlError.RemoteFailure) -> String {
		
		if remoteFailure is LnurlError.RemoteFailure_CouldNotConnect {
			return NSLocalizedString("Connection failure", comment: "Error title")
		} else {
			return NSLocalizedString("Invalid response", comment: "Error title")
		}
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		popoverState.close()
	}
}
