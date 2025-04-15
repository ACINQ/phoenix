import Foundation

struct CapabilitiesContainer {
	
	static let minByteCount: Int = 7
	
	/// Capabilities container length
	/// Indicates the size of this capability container (including this field).
	/// Valid values are: 000Fh-FFFEh (15-65534)
	///
	var len: UInt16
	
	/// Mapping version
	/// Indicates the mapping specification version the tag is compliant with.
	/// The most significant nibble (4 most significant bits) indicate the major version number.
	/// The least significant nibble (4 least significant bits) indicate the minor version number.
	///
	var version: UInt8
	
	/// Maximum R-APDU data size
	/// Defines the maximum data size that can be read from
	/// the tag using a single ReadBinary command.
	/// Valid values are: 000Fh-FFFFh (15-65535)
	///
	var mLe: UInt16
	
	/// Maximum C-APDU data size
	/// Defines the maximum data size that can be sent to
	/// the tag using a single UpdateBinary command.
	/// Valid values are: 0001h-FFFFh (1-65535)
	///
	var mLc: UInt16
	
	/// NDEF files
	/// TLV blocks that contain information to control and manage each available NDEF file.
	///
	var files: [CtrlTLV]
	
	init(len: UInt16, version: UInt8, mLe: UInt16, mLc: UInt16, files: [CtrlTLV]) {
		self.len = len
		self.version = version
		self.mLe = mLe
		self.mLc = mLc
		self.files = files
	}
	
	init?(data: [UInt8]) {
		
		guard data.count >= CapabilitiesContainer.minByteCount else { return nil }
		
		len = data.readBigEndian(offset: 0, as: UInt16.self)
		version = data[2]
		mLe = data.readBigEndian(offset: 3, as: UInt16.self)
		mLc = data.readBigEndian(offset: 5, as: UInt16.self)
		
		var fileList: [CtrlTLV] = []
		var start = 7
		var end = start + CtrlTLV.byteCount
		while (data.count >= end) {
			guard let file = CtrlTLV(data: Array<UInt8>(data[start..<end])) else {
				return nil
			}
			fileList.append(file)
			start = end
			end = start + CtrlTLV.byteCount
		}
		
		files = fileList
	}
	
	func encode() -> [UInt8] {
		
		var buffer: [UInt8] = Array<UInt8>()
		buffer.reserveCapacity(CapabilitiesContainer.minByteCount + (CtrlTLV.byteCount * files.count))
		
		buffer.append(contentsOf: len.bigEndian.toByteArray())
		buffer.append(version)
		buffer.append(contentsOf: mLe.bigEndian.toByteArray())
		buffer.append(contentsOf: mLc.bigEndian.toByteArray())
		
		files.forEach { file in
			buffer.append(contentsOf: file.encode())
		}
		
		return buffer
	}
	
	static func ntag424Dna_defaultValue() -> CapabilitiesContainer {
		
		let file2 = CtrlTLV.ntag424Dna_defaultFile2()
		let file3 = CtrlTLV.ntag424Dna_defaultFile3()
		
		return CapabilitiesContainer(
			len: 23,
			version: 0x20,
			mLe: 256,
			mLc: 255,
			files: [file2, file3]
		)
	}
	
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

struct CtrlTLV {
	
	static let byteCount = 8
	
	/// Type of TLV block
	/// Valid values are:
	/// - 4: NDEF File Control TLV
	/// - 5: Proprietary File Control TLV
	///
	var t: UInt8
	
	/// Size in bytes of the value field.
	/// Must be 6 for this implementation.
	///
	let l: UInt8
	
	/// File Identifier
	/// The valid ranges are:
	/// - 0001h - E101h
	/// - E104h - 3EFFh
	/// - 3F01h - 3FFEh
	/// - 4000h - FFFEh
	/// Other ranges are reserved for future use.
	///
	let fileId: [UInt8]
	
	/// Maximum file size (i.e. max storage capacity).
	///
	let fileSize: UInt16
	
	/// NDEF file read access condition:
	/// - 00h: indicates read access granted without any security
	/// - 80h-FEh: proprietary (card-specific protocol)
	///
	var readAccess: UInt8
	
	/// NDEF file write access condition:
	/// - 00h: indicates write access granted without any security
	/// - FFh: indicates no write access granted at all (read-only)
	/// - 80h-FEh: proprietary (card-specific protocol)
	///
	var writeAccess: UInt8
	
	init(t: UInt8, l: UInt8, fileId: [UInt8], fileSize: UInt16, readAccess: UInt8, writeAccess: UInt8) {
		self.t = t
		self.l = l
		self.fileId = fileId
		self.fileSize = fileSize
		self.readAccess = readAccess
		self.writeAccess = writeAccess
	}
	
	init?(data: [UInt8]) {
		
		guard data.count >= CtrlTLV.byteCount else { return nil }
		
		t = data[0]
		l = data[1]
		fileId = Array<UInt8>(data[2...3])
		fileSize = data.readBigEndian(offset: 4, as: UInt16.self)
		readAccess = data[6]
		writeAccess = data[7]
	}
	
	func encode() -> [UInt8] {
		
		var buffer: [UInt8] = Array<UInt8>()
		buffer.reserveCapacity(CtrlTLV.byteCount)
		
		buffer.append(t)
		buffer.append(l)
		buffer.append(contentsOf: fileId)
		buffer.append(contentsOf: fileSize.bigEndian.toByteArray())
		buffer.append(readAccess)
		buffer.append(writeAccess)
		
		return buffer
	}
	
	static func ntag424Dna_defaultFile2() -> CtrlTLV {
		return CtrlTLV(
			t: 4,
			l: 6,
			fileId: [0xe1, 0x04],
			fileSize: 256,
			readAccess: 0x00,
			writeAccess: 0x00
		)
	}
	
	static func ntag424Dna_defaultFile3() -> CtrlTLV {
		return CtrlTLV(
			t: 5,
			l: 6,
			fileId: [0xe1, 0x05],
			fileSize: 128,
			readAccess: 0x82,
			writeAccess: 0x83
		)
	}
	
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
