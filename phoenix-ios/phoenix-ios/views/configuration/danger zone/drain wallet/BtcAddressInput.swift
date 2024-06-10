import SwiftUI
import PhoenixShared

fileprivate let filename = "BtcAddressInput"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct BtcAddressInput: View {
	
	enum DetailedError: Error, Equatable {
		case emptyInput
		case invalidInput(localizedMsg: String)
		case invalidScannedInput(localizedMsg: String)
		case invalidChain(localizedMsg: String)
		
		func localizedErrorMessage() -> String? {
			switch self {
				case .emptyInput                   : return nil
				case .invalidInput(let msg)        : return msg
				case .invalidScannedInput(let msg) : return msg
				case .invalidChain(let msg)        : return msg
			}
		}
	} // </enum DetailedError>
	
	@Binding var result: Result<BitcoinUri, BtcAddressInput.DetailedError>
	
	@State var textFieldValue: String = ""
	@State var scannedValue: String? = nil
	@State var isScanningQrCode: Bool = false
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		// [(TextField::BtcAddr) (Button::X)] [Button::ScanQrCode]
		HStack(alignment: VerticalAlignment.center, spacing: 10) {
			
			// [TextField::BtcAddr (Button::X)]
			HStack(alignment: VerticalAlignment.center, spacing: 2) {
				
				// TextField::BtcAddr
				TextField(
					NSLocalizedString("Bitcoin address", comment: "TextField placeholder"),
					text: $textFieldValue
				)
				.truncationMode(.middle)
				.onChange(of: textFieldValue) { _ in
					checkBitcoinAddress()
				}
				
				// Button::X
				if !textFieldValue.isEmpty {
					Button {
						clearTextField()
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
					.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
					.accessibilityLabel("Clear textfield")
				}
				
			} // </HStack>
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(Color.textFieldBorder, lineWidth: 1)
			)
			
			// [Button::ScanQrCode]
			Button {
				didTapScanQrCodeButton()
			} label: {
				Image(systemName: "qrcode.viewfinder")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.buttonStyle(BorderlessButtonStyle()) // prevents trigger when row tapped
			
		} // </HStack>
		.sheet(isPresented: $isScanningQrCode) {
			
			ScanBitcoinAddressSheet(didScanQrCode: self.didScanQrCode)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didTapScanQrCodeButton() -> Void {
		log.trace("didTapScanQrCodeButton()")
		
		scannedValue = nil
		isScanningQrCode = true
	}
	
	func didScanQrCode(result: String) -> Void {
		log.trace("didScanQrCode()")
		
		isScanningQrCode = false
		scannedValue = result
		textFieldValue = result
	}
	
	func checkBitcoinAddress() -> Void {
		log.trace("checkBitcoinAddress()")
		
		let isScannedValue = textFieldValue == scannedValue
		
		let business = Biz.business
		let parseResult = Parser.shared.readBitcoinAddress(chain: business.chain, input: textFieldValue)
		
		if let error = parseResult.left {
			
			log.debug("result.error = \(error)")
			
			if isScannedValue {
				// If the user scanned a non-bitcoin QRcode
				result = .failure(.invalidScannedInput(localizedMsg: String(
					localized: "The scanned QR code is not a bitcoin address",
					comment: "Error message - parsing bitcoin address"
				)))
			}
			else {
				// They have entered an invalid bitcoin address in the text field.
				if textFieldValue.isEmpty {
					result = .failure(.emptyInput)
				} else {
					// We expect them to be using copy-paste.
					// Manually typing out a long bitcoin address should be discouraged.
					result = .failure(.invalidInput(localizedMsg: String(
						localized: "Invalid bitcoin address",
						comment: "Error message - parsing bitcoin address"
					)))
				}
			}
			
		} else {
			
			let bitcoinUri = parseResult.right!
			log.debug("result.info = \(bitcoinUri)")
			
			// Check to make sure the bitcoin address is for the correct chain.
			let parsedChain = bitcoinUri.chain
			let expectedChain = Biz.business.chain
			
			if parsedChain != expectedChain {
				
				result = .failure(.invalidChain(localizedMsg: String(
					localized: "The address is for \(parsedChain.name), but you're on \(expectedChain.name)",
					comment: "Error message - parsing bitcoin address"
				)))
				
			} else { // looks good
				
				result = .success(bitcoinUri)
			}
		}
		
		if !isScannedValue && scannedValue != nil {
			// The user has changed the textFieldValue,
			// so we're no longer dealing with the scanned value
			scannedValue = nil
		}
	}
	
	func clearTextField() {
		log.trace("clearTextField()")
		
		textFieldValue = ""
	}
}
