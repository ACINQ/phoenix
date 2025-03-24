/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation
import CoreNFC

public struct NxpCommandResult {
    var data: [UInt8]
    var statusMajor: UInt8
    var statusMinor: UInt8
    
    static func emptyResult() -> NxpCommandResult {
        return NxpCommandResult(data: [], statusMajor: 0, statusMinor: 0)
    }
}

public class DnaCommunicator {
    
	public let tag: NFCISO7816Tag
	public var activeKeyNumber: KeySpecifier = .KEY_0
	
	public var debug: Bool = false
	
	var activeTransactionIdentifier: [UInt8] = [0,0,0,0]
	var commandCounter: Int = 0
	var sessionEncryptionMode: EncryptionMode?

	public init(tag: NFCISO7816Tag) {
		self.tag = tag
	}
	
	func debugPrint(_ value: String) {
		if debug {
			print(value)
		}
	}
    
	func makeErrorIfNotExpectedStatus(_ result: NxpCommandResult) -> Error? {
		if result.statusMajor != 0x91 || (result.statusMinor != 0x00 && result.statusMinor != 0xaf) {
			let major = String(format:"%02X", result.statusMajor)
			let minor = String(format:"%02X", result.statusMinor)
			return Helper.makeError(102, "Unexpected status: \(major) / \(minor)")
		} else {
			return nil
		}
	}
    
	func isoTransceive(
		packet: [UInt8]
	) async -> Result<NxpCommandResult, Error> {
		
		let data = Helper.dataFromBytes(bytes: packet)
		let apdu = NFCISO7816APDU(data: data)
		if debug {
			Helper.logBytes("Outbound", packet)
		}

		guard let apdu else {
			debugPrint("APDU Failure: Attempt")
			return .failure(Helper.makeError(100, "APDU Failure"))
		}
		
		do {
			let (data, sw1, sw2) = try await tag.sendCommand(apdu: apdu)
			
			let bytes = Helper.bytesFromData(data: data)
			if debug {
				Helper.logBytes("Inbound", bytes + [sw1] + [sw2])
			}
			
			let result = NxpCommandResult(data: bytes, statusMajor: sw1, statusMinor: sw2)
			return .success(result)
			
		} catch {
			self.debugPrint("An error occurred: \(error)")
			return .failure(error)
		}
	}

	func nxpNativeCommand(
		command : UInt8,
		header  : [UInt8],
		data    : [UInt8]?,
		macData : [UInt8]? = nil
	) async -> Result<NxpCommandResult, Error> {
		
		let data = data ?? [UInt8]()
		var packet: [UInt8] = [
			0x90,
			command,
			0x00,
			0x00,
			UInt8(header.count + data.count + (macData?.count ?? 0))
		]
		packet.append(contentsOf: header)
		packet.append(contentsOf: data)
		if let macData = macData {
			packet.append(contentsOf: macData)
		}
		packet.append(0x00)
		
		return await isoTransceive(packet: packet)
	}
    
	public func nxpPlainCommand(
		command : UInt8,
		header  : [UInt8],
		data    : [UInt8]?
	) async -> Result<NxpCommandResult, Error> {
		
      let result = await nxpNativeCommand(command: command, header: header, data: data)
		self.commandCounter += 1
		
		return result
	}
    
	public func nxpMacCommand(
		command : UInt8,
		header  : [UInt8],
		data    : [UInt8]?
	) async -> Result<NxpCommandResult, Error> {
		
		let data = data ?? [UInt8]()
		var macInputData: [UInt8] = [
			command,
			UInt8(commandCounter % 256),
			UInt8(commandCounter / 256),
			activeTransactionIdentifier[0],
			activeTransactionIdentifier[1],
			activeTransactionIdentifier[2],
			activeTransactionIdentifier[3],
		]
		macInputData.append(contentsOf: header)
		macInputData.append(contentsOf: data)
		let macData = sessionEncryptionMode!.generateMac(message: macInputData)
		
		let result = await nxpNativeCommand(command: command, header: header, data: data, macData: macData)
		self.commandCounter += 1
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			
			guard result.data.count >= 8 else {
				// No MAC available for this command
				let noDataResult = NxpCommandResult(
					data: [UInt8](),
					statusMajor: result.statusMajor,
					statusMinor: result.statusMinor
				)
				
				return .success(noDataResult)
			}
				
			let dataBytes = (result.data.count > 8) ? result.data[0...(result.data.count - 9)] : []
			let macBytes = result.data[(result.data.count - 8)...(result.data.count - 1)]
			
			// Check return MAC
			var returnMacInputData: [UInt8] = [
				result.statusMinor,
				UInt8(commandCounter % 256),
				UInt8(commandCounter / 256),
				activeTransactionIdentifier[0],
				activeTransactionIdentifier[1],
				activeTransactionIdentifier[2],
				activeTransactionIdentifier[3],
			]
			returnMacInputData.append(contentsOf: dataBytes)
			let returnMacData = self.sessionEncryptionMode!.generateMac(message: returnMacInputData)
			
			if returnMacData.elementsEqual(macBytes) {
				let finalResult = NxpCommandResult(
					data: [UInt8](dataBytes),
					statusMajor: result.statusMajor,
					statusMinor: result.statusMinor
				)
				return .success(finalResult)
			} else {
				self.debugPrint("Invalid MAC! (\(returnMacData)) / (\(macBytes)")
				return .failure(Helper.makeError(101, "Invalid MAC"))
			}
		}
	}
	
	public func nxpEncryptedCommand(
		command : UInt8,
		header  : [UInt8],
		data    : [UInt8]?
	) async -> Result<NxpCommandResult, Error> {
		
		let data = data ?? [UInt8]()
		if debug {
			Helper.logBytes("Unencryped outgoing data", data)
		}
		let encryptedData = data.count == 0 ? [UInt8]() : sessionEncryptionMode!.encryptData(message: data)
		
		let result = await nxpMacCommand(command: command, header: header, data: encryptedData)
		
		switch result {
		case .failure(let err):
			return .failure(err)
			
		case .success(let result):
			let decryptedResultData = result.data.count == 0 ? [UInt8]() : self.sessionEncryptionMode!.decryptData(message: result.data)
			if debug {
				Helper.logBytes("Unencrypted incoming data", decryptedResultData)
			}
			
			let finalResult = NxpCommandResult(
				data: decryptedResultData,
				statusMajor: result.statusMajor,
				statusMinor: result.statusMinor
			)
			return .success(finalResult)
		}
	}
	
	public func nxpSwitchedCommand(
		mode    : CommuncationMode,
		command : UInt8,
		header  : [UInt8],
		data    : [UInt8]
	) async -> Result<NxpCommandResult, Error> {
		
		switch mode {
		case .PLAIN:
			return await nxpPlainCommand(command: command, header: header, data: data)
		case .MAC:
			return await nxpMacCommand(command: command, header: header, data: data)
		case .FULL:
			return await nxpEncryptedCommand(command: command, header: header, data: data)
		}
	}
}
