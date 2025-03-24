/**
 * Special thanks to Jonathan Bartlett.
 * DnaCommunicator sources are derived from:
 * https://github.com/johnnyb/nfc-dna-kit
 */

import Foundation

struct CapabilitiesContainer {
	
	static let byteCount: Int = 7 + (CtrlTLV.byteCount * 2)
	
	var len: UInt16
	var t4tVNo: UInt8
	var mLe: UInt16
	var mLc: UInt16
	
	var file2: CtrlTLV
	var file3: CtrlTLV
	
	init(len: UInt16, t4tVno: UInt8, mLe: UInt16, mLc: UInt16, file2: CtrlTLV, file3: CtrlTLV) {
		self.len = len
		self.t4tVNo = t4tVno
		self.mLe = mLe
		self.mLc = mLc
		self.file2 = file2
		self.file3 = file3
	}
	
	init?(data: [UInt8]) {
		
		guard data.count >= CapabilitiesContainer.byteCount else { return nil }
		
		len = data.readBigEndian(offset: 0, as: UInt16.self)
		t4tVNo = data[2]
		mLe = data.readBigEndian(offset: 3, as: UInt16.self)
		mLc = data.readBigEndian(offset: 5, as: UInt16.self)
		
		guard let f2 = CtrlTLV(data: Array<UInt8>(data[7..<15])) else { return nil }
		guard let f3 = CtrlTLV(data: Array<UInt8>(data[15..<23])) else { return nil }
		
		file2 = f2
		file3 = f3
	}
	
	func encode() -> [UInt8] {
		
		var buffer: [UInt8] = Array<UInt8>()
		buffer.reserveCapacity(CapabilitiesContainer.byteCount)
		
		buffer.append(contentsOf: Helper.byteArrayBE(from: len))
		buffer.append(t4tVNo)
		buffer.append(contentsOf: Helper.byteArrayBE(from: mLe))
		buffer.append(contentsOf: Helper.byteArrayBE(from: mLc))
		
		buffer.append(contentsOf: file2.encode())
		buffer.append(contentsOf: file3.encode())
		
		return buffer
	}
	
	static func defaultValue() -> CapabilitiesContainer {
		return CapabilitiesContainer(
			len: 23,
			t4tVno: 0x20,
			mLe: 256,
			mLc: 255,
			file2: CtrlTLV.defaultFile2(),
			file3: CtrlTLV.defaultFile3()
		)
	}
}

struct CtrlTLV {
	
	static let byteCount = 8
	
	var t: UInt8
	let l: UInt8
	let fileId: [UInt8]
	let fileSize: UInt16
	var readAccess: UInt8
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
		buffer.append(contentsOf: Helper.byteArrayBE(from: fileSize))
		buffer.append(readAccess)
		buffer.append(writeAccess)
		
		return buffer
	}
	
	static func defaultFile2() -> CtrlTLV {
		return CtrlTLV(
			t: 4,
			l: 6,
			fileId: [0xe1, 0x04],
			fileSize: 256,
			readAccess: 0x00,
			writeAccess: 0x00
		)
	}
	
	static func defaultFile3() -> CtrlTLV {
		return CtrlTLV(
			t: 5,
			l: 6,
			fileId: [0xe1, 0x05],
			fileSize: 128,
			readAccess: 0x82,
			writeAccess: 0x83
		)
	}
}
