import Foundation
import CoreNFC
import DnaCommunicator

fileprivate let filename = "HceWriter"
#if DEBUG
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

/**
 * The following document is referenced in the comments below:
 *
 * > NFC Forum: Type 4 Tag Operation Specification
 * > Technical Specification
 * > T4TOP 2.0
 * > NFCForum-TS-Type-4-Tag_2.0
 * > 2011-06-28
 */

enum HceWriterError: Error {
	case nfcNotAvailable
	case hceNotAvailable
	case hceNotEligible
	case sessionError(HceSessionError, Error)
}

enum HceSessionError {
	case acquirePresentmentIntent
	case initializeSession
	case startEmulation
	case eventStream
}

@available(iOS 17.4, *)
class HceWriter {
	
	static let shared = HceWriter()
	
	private enum FileSpecifier: UInt8, CustomStringConvertible {
		case CC_FILE   = 1
		case NDEF_FILE = 2
		
		var identifier: [UInt8] {
			switch self {
				case .CC_FILE   : return [0xE1, 0x03]
				case .NDEF_FILE : return [0xE1, 0x04]
			}
		}
		
		public var description: String {
			switch self {
				case .CC_FILE   : return "CC File (#1)"
				case .NDEF_FILE : return "NDEF File (#2)"
			}
		}
	}
	
	private struct HceWriterState {
		let ndefFile: [UInt8]
		var selectedFile: FileSpecifier?
	}
	
	func start(ndefFile: [UInt8]) async -> HceWriterError? {
		log.trace("start()")
		
		guard NFCReaderSession.readingAvailable else {
			log.error("NFCReaderSession.readingAvailable is false")
			return .nfcNotAvailable
		}
		
		guard CardSession.isSupported else {
			log.error("CardSession.isSupported is false")
			return .hceNotAvailable
		}
			
		guard await CardSession.isEligible else {
			log.error("CardSession.isEligible is false")
			return .hceNotEligible
		}
			
		// Hold a presentment intent assertion reference to prevent the
		// default contactless app from launching. In a real app, monitor
		// presentmentIntent.isValid to ensure the assertion remains active.
		var presentmentIntent: NFCPresentmentIntentAssertion? = nil
		do {
			presentmentIntent = try await NFCPresentmentIntentAssertion.acquire()
		} catch {
			log.error("Failed to acquire NFCPresentmentIntentAssertion: \(error)")
			return .sessionError(.acquirePresentmentIntent, error)
		}
		
		defer {
			log.debug("Releasing presentmentIntent...")
			withExtendedLifetime(presentmentIntent) {
				presentmentIntent = nil // Release presentment intent assertion
			}
		}
		
		let session: CardSession
		do {
			session = try await CardSession()
		} catch {
			log.error("Failed initialize CardSession: \(error)")
			return .sessionError(.initializeSession, error)
		}
		
		defer {
			log.debug("Invalidating CardSession...")
			session.invalidate()
		}
		
		do {
			session.alertMessage = String(localized: "Hold your device near the reader.")
			try await session.startEmulation()
		} catch {
			log.error("session.startEmulation: error: \(error)")
			return .sessionError(.startEmulation, error)
		}
		
		do {
			var state = HceWriterState(ndefFile: ndefFile, selectedFile: nil)
			for try await event in session.eventStream {
				
				var isDoneProcessingEventStream = false
				switch event {
				case .sessionStarted:
					log.debug("session.event: sessionStarted")
					break
					
				case .readerDetected:
					log.debug("session.event: readerDetected")
					session.alertMessage = String(localized: "Communicating with card reader.")
					
				case .readerDeselected:
					log.debug("session.event: readerDeselected")
					// Stop emulation on first notification of RF link loss.
					await session.stopEmulation(status: .success)
					log.debug("session.stopEmulation(): completed")
					isDoneProcessingEventStream = true
					break
					
				case .received(let cmd):
					log.debug("session.event: received(cmd)")
					
					let response = handleReceivedCommand(cmd, &state)
					while true {
						do {
							// Call handler to process received input and produce a response.
							try await cmd.respond(response: response.toData())
							break
						} catch {
							var tryAgain = false
							if let cardError = error as? CardSession.Error {
								log.error("msg.respond(): error: \(cardError.localizedDescription)")
								if cardError == .transmissionError {
									tryAgain = true
								}
							}
							if !tryAgain {
								break
							}
						}
					} // </while true>
					
				case .sessionInvalidated(reason: _):
					log.debug("session.event: sessionInvalidated")
					session.alertMessage = String(localized: "Ending communication with card reader.")
					isDoneProcessingEventStream = true
					break
					
				@unknown default:
					log.debug("session.event: unknown")
					break
					
				} // </switch event>
				
				if isDoneProcessingEventStream {
					break
				}
			} // </for try await event in session.eventStream>
			
		} catch {
			log.error("session.eventStream: error: \(error)")
			return .sessionError(.eventStream, error)
		}
		
		return nil
	}
	
	private func handleReceivedCommand(
		_ cmd: CardSession.APDU,
		_ state: inout HceWriterState
	) -> [UInt8] {
		log.trace("handleReceivedCommand()")
		
		guard let command = CommandAPDU(cmd: cmd) else {
			log.debug("RECV: \(cmd.payload.toHex())")
			log.error("Unable to parse command")
			return aError()
		}
		
		// Command 1:
		// - NDEF Tag Application select
		// - Section 5.4.2
		//
		if command.isNdefTagApplicationSelect() {
			log.debug("RECV: NDEF Tag Select")
			log.debug("SEND: OK")
			
			return aOkay()
		}
		
		// Command 2:
		// - SelectFile: Capability Container
		// - Section 5.4.3
		//
		// Command 4:
		// - SelectFile: NDEF File
		// - Section 5.4.5
		//
		if let selectFile = command.asSelectFileCommand() {
			log.debug("RECV: SelectFile: \(selectFile.fileId.toHex())")
			
			if selectFile.fileId == FileSpecifier.CC_FILE.identifier {
				log.debug("Selecting CC_FILE...")
				state.selectedFile = .CC_FILE
				
				log.debug("SEND: OK")
				return aOkay()
				
			} else if selectFile.fileId == FileSpecifier.NDEF_FILE.identifier {
				log.debug("Selecting NDEF_FILE...")
				state.selectedFile = .NDEF_FILE
				
				log.debug("SEND: OK")
				return aOkay()
				
			} else {
				log.debug("Unknown file...")
				state.selectedFile = nil
				
				log.debug("SEND: ERROR")
				return aError()
			}
		}
		
		// Command 3:
		// - ReadBinary data from CC file
		// - Section 5.4.4
		//
		// Command 5:
		// - ReadBinary data from NDEF file
		// - Section 5.4.6
		//
		if let readBinary = command.asReadBinaryCommand() {
			log.debug("RECV: ReadBinary: offset(\(readBinary.offset)) length(\(readBinary.length))")
			
			if state.selectedFile == .CC_FILE {
				log.debug("Reading CC_FILE...")
				
				let ccFile = CapabilitiesContainer.hce_defaultValue().encode()
				let prefix: [UInt8] = readFile(ccFile, readBinary)
				let suffix: [UInt8] = aOkay()
				
				let response = prefix + suffix
				log.debug("SEND: \(response.toHex())")
				return response
				
			} else if state.selectedFile == .NDEF_FILE {
				log.debug("Reading NDEF_FILE...")
				
				let prefix: [UInt8] = readFile(state.ndefFile, readBinary)
				let suffix: [UInt8] = aOkay()
				
				let response = prefix + suffix
				log.debug("SEND: \(response.toHex())")
				return response
				
			} else {
				log.debug("No file selected...")
				log.debug("SEND: aError")
				
				return aError()
			}
		}
		
		log.debug("RECV: \(cmd.payload.toHex())")
		log.error("Unknown command")
		return aError()
	}
	
	private func readFile(_ file: [UInt8], _ cmd: ReadBinaryCommand) -> [UInt8] {
		
		let startOffset = Int(cmd.offset)
		if startOffset >= file.count {
			log.debug("startOffset >= file.count")
			return []
		}
		
		let endOffset = startOffset + Int(cmd.length)
		if endOffset < file.count {
			log.debug("range: \(startOffset)..<\(endOffset)")
			return Array(file[startOffset..<endOffset])
		} else {
			log.debug("range: \(startOffset)..<\(file.count)")
			return Array(file[startOffset..<file.count])
		}
	}
	
	private func aOkay() -> [UInt8] {
		return [
			0x90, // SW1 - Status word 1
			0x00  // SW2 - Status word 2
		]
	}
	
	private func aError() -> [UInt8] {
		return [
			0x6A, // SW1 Status byte 1 - Command processing status
			0x82  // SW2 Status byte 2 - Command processing qualifier
		]
	}
}

extension CapabilitiesContainer {
	
	static func hce_defaultValue() -> CapabilitiesContainer {
		
		let file2 = CtrlTLV.hce_defaultFile2()
		
		return CapabilitiesContainer(
			len: 15,
			version: 0x20,
			mLe: 256,
			mLc: 255,
			files: [file2]
		)
	}
}

extension CtrlTLV {
	
	static func hce_defaultFile2() -> CtrlTLV {
		return CtrlTLV(
			t: 4,
			l: 6,
			fileId: [0xe1, 0x04],
			fileSize: 512,
			readAccess: 0x00,
			writeAccess: 0xFF
		)
	}
}
