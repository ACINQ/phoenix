import Foundation
import CoreNFC
import DnaCommunicator

fileprivate let filename = "NfcWriter"
#if DEBUG
fileprivate let log = LoggerFactory.shared.logger(filename, .trace)
fileprivate let dnaLog = LoggerFactory.shared.logger("DnaCommunicator", .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
fileprivate let dnaLog = LoggerFactory.shared.logger("DnaCommunicator", .warning)
#endif

class NfcWriter: NSObject, NFCTagReaderSessionDelegate {
	
	// --------------------------------------------------
	// MARK: Struct's & Enum's
	// --------------------------------------------------
	
	struct WriteInput {
		let template: Ndef.Template
		let key0: [UInt8]
		let piccDataKey: [UInt8]
		let cmacKey: [UInt8]
	}

	struct WriteOutput {
		let chipUid: [UInt8]
	}

	enum WriteError: Error {
		case readingNotAvailable
		case alreadyStarted
		case couldNotConnect
		case couldNotAuthenticate
		case keySlotsUnavailable
		case protocolError(WriteStep, Error)
		case scanningTerminated(NFCReaderError)
	}
	
	enum WriteStep: Int {
		case readChipUid
		case writeFile2Settings
		case writeFile2Data
		case writeKey0
	}
	
	struct ResetInput {
		let key0: [UInt8]
		let piccDataKey: [UInt8]
		let cmacKey: [UInt8]
	}

	enum DebugError: Error {
		case readingNotAvailable
		case alreadyStarted
		case couldNotConnect
		case couldNotAuthenticate
		case readChipUid(Error)
		case readFile1Settings(Error)
		case readFile1Data(Error)
		case readFile2Settings(Error)
		case readFile2Data(Error)
		case readFile3Settings(Error)
		case readFile3Data(Error)
		case scanningTerminated(NFCReaderError)
	}

	// --------------------------------------------------
	// MARK: Variables
	// --------------------------------------------------
	
	static let shared = NfcWriter()
	
	private let queue: DispatchQueue
	
	private var session: NFCTagReaderSession? = nil
	
	private var writeInput: WriteInput? = nil
	private var writeCallback: ((Result<WriteOutput, WriteError>) -> Void)? = nil
	
	private var resetInput: ResetInput? = nil
	private var resetCallback: ((Result<Void, WriteError>) -> Void)? = nil
	
	private var debugCallback: ((Result<Void, DebugError>) -> Void)? = nil
		
	// --------------------------------------------------
	// MARK: General
	// --------------------------------------------------
	
	override init() {
		queue = DispatchQueue(label: "NfcWriter")
	}
	
	private var isWriting: Bool {
		return (writeCallback != nil)
	}
	
	private var isResetting: Bool {
		return (resetCallback != nil)
	}
	
	private func connectToTag(_ tag: NFCTag) async {
		log.trace("connectToTag()")
		
		guard case let .iso7816(isoTag) = tag else {
			preconditionFailure("invalid tag parameter")
		}
		
		guard let session else {
			log.warning("connectToTag: ignoring: session is nil")
			return
		}
		
		do {
			try await session.connect(to: tag)
			
			log.debug("session.connect(): success")
			
			let dnaLogger = {(msg: String) -> Void in
				dnaLog.debug("\(msg)")
			}
			let dna = DnaCommunicator(tag: isoTag, logger: dnaLogger)

			Task {
				await authenticate(dna)
			}
			
		} catch {
			log.debug("session.connect(): failed: \(error)")
			if isWriting {
				writeDisconnect(error: .couldNotConnect)
			} else if isResetting {
				resetDisconnect(error: .couldNotConnect)
			} else {
				debugDisconnect(error: .couldNotConnect)
			}
		}
	}
	
	private func authenticate(
		_ dna: DnaCommunicator
	) async {
		
		log.trace("authenticateToTag()")
		
		let key0: [UInt8]
		if writeInput != nil {
			// We are expecting an empty card here.
			key0 = DnaCommunicator.defaultKey
		} else if let input = resetInput {
			// We are expecting a non-empty card, so we need to use proper key.
			key0 = input.key0
		} else {
			// We're debugging, and expecting an empty card.
			key0 = DnaCommunicator.defaultKey
		}
		
		let result = await dna.authenticateEV2First(
			keyNum  : .KEY_0,
			keyData : key0
		)
		
		switch result {
		case .failure(let error):
			log.debug("dna.authenticateEV2First(keyNum: 0): failed: \(error)")
			if isWriting {
				writeDisconnect(error: .couldNotAuthenticate)
			} else if isResetting {
				resetDisconnect(error: .couldNotAuthenticate)
			} else {
				debugDisconnect(error: .couldNotAuthenticate)
			}
			
		case .success(_):
			log.debug("dna.authenticateEV2First(keyNum: 0): success")
			
			Task {
				if isWriting {
					await writeDriver(dna)
				} else if isResetting {
					await resetDriver(dna)
				} else {
					await debugDriver(dna)
				}
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Write Logic
	// --------------------------------------------------
	
	func writeCard(
		_ input: WriteInput,
		_ callback: @escaping (Result<WriteOutput, WriteError>) -> Void
	) {
		log.trace("startWriting()")
		
		let fail = { (error: WriteError) in
			DispatchQueue.main.async {
				callback(.failure(error))
			}
		}
		
		queue.async { [self] in
			
			guard NFCReaderSession.readingAvailable else {
				log.error("NFCReaderSession.readingAvailable is false")
				return fail(.readingNotAvailable)
			}
			
			guard session == nil else {
				log.error("session is already started")
				return fail(.alreadyStarted)
			}
			
			session = NFCTagReaderSession(pollingOption: .iso14443, delegate: self, queue: queue)
			session?.alertMessage = String(
				localized: "Hold your card near the device to program it.",
				comment: "Message in iOS NFC dialog"
			)
			
			self.writeInput = input
			self.writeCallback = callback
			session?.begin()
			
			log.info("session is ready")
		}
	}
	
	private func writeDriver(
		_ dna: DnaCommunicator
	) async {
		
		log.trace("writeDriver()")
		
		guard let input = writeInput else {
			fatalError("input is nil")
		}
		
		// Step 1 of 5:
		// Read the chip UID
		
		let chipUid: [UInt8]
		do {
			chipUid = try await readChipUid(dna).get()
		} catch {
			return writeDisconnect(error: .protocolError(.readChipUid, error))
		}
		
		// Step 2 of 5:
		// Write piccDataKey & cmacKey to the card.
		//
		// Ideally we'll put:
		// - piccDataKey=key1, cmacKey=key2
		//
		// But that may not be possible.
		// If the card was reset incorrectly, then certain keys may not be available to us.
		//
		// However, other key configurations are perfectly acceptable for our use case:
		// - piccDataKey=key1, cmacKey=key3
		// - piccDataKey=key1, cmacKey=key4
		// - piccDataKey=key2, cmacKey=key3
		// - piccDataKey=key2, cmacKey=key4
		// - piccDataKey=key3, cmacKey=key4
		//
		// So we'll try our best to program the card with the keys that are available to us.
		
		var position = await writeKey(dna, input.piccDataKey, startingPosition: .KEY_1)
		guard let piccDataKeyPosition = position else {
			return writeDisconnect(error: .keySlotsUnavailable)
		}
		log.debug("piccDataKeyPosition: \(piccDataKeyPosition.description)")
		
		position = await writeKey(dna, input.cmacKey, startingPosition: position?.next())
		guard let cmacKeyPosition = position else {
			return writeDisconnect(error: .keySlotsUnavailable)
		}
		log.debug("cmacKeyPosition: \(cmacKeyPosition.description)")
		
		// Step 3 of 5:
		// Write file2 settings.
		
		let file2Settings: FileSettings
		do {
			file2Settings = try await writeFile2Settings(dna, input.template,
				piccDataKeyPosition: piccDataKeyPosition,
				cmacKeyPosition: cmacKeyPosition
			).get()
		} catch {
			return writeDisconnect(error: .protocolError(.writeFile2Settings, error))
		}
		
		// Step 4 of 5:
		// Write file2 data.
		
		do {
			let data = input.template.data
			try await writeFile2Data(dna, data, file2Settings).get()
		} catch {
			return writeDisconnect(error: .protocolError(.writeFile2Data, error))
		}
		
		// Step 5 of 5:
		// Change key0
		//
		// Note that after you perform this step,
		// if you wanted to make other changes to the card,
		// then you would need to reauthenticate.
		
		do {
			let _ = try await changeKey(dna, .KEY_0,
				oldKey : DnaCommunicator.defaultKey,
				newKey : input.key0
			).get()
		} catch {
			return writeDisconnect(error: .protocolError(.writeKey0, error))
		}
		
		writeDisconnect(output: WriteOutput(chipUid: chipUid))
	}
	
	private func writeDisconnect(error: WriteError) {
		writeDisconnect(result: .failure(error))
	}
	
	private func writeDisconnect(output: WriteOutput) {
		writeDisconnect(result: .success(output))
	}
	
	private func writeDisconnect(result: Result<WriteOutput, WriteError>) {
		
		queue.async {
			
			guard let session = self.session,
					let callback = self.writeCallback
			else {
				return
			}
			log.trace("disconnect()")
			
			session.invalidate()
			self.session = nil
			self.writeInput = nil
			self.writeCallback = nil
			
			DispatchQueue.main.async {
				callback(result)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Reset Logic
	// --------------------------------------------------
	
	func resetCard(
		_ input: ResetInput,
		_ callback: @escaping (Result<Void, WriteError>) -> Void
	) {
		log.trace("resetCard()")
		
		let fail = { (error: WriteError) in
			DispatchQueue.main.async {
				callback(.failure(error))
			}
		}

		queue.async { [self] in

			guard NFCReaderSession.readingAvailable else {
				log.error("NFCReaderSession.readingAvailable is false")
				return fail(.readingNotAvailable)
			}

			guard session == nil else {
				log.error("session is already started")
				return fail(.alreadyStarted)
			}

			session = NFCTagReaderSession(pollingOption: .iso14443, delegate: self, queue: queue)
			session?.alertMessage = String(
				localized: "Hold your card near the device to reset it.",
				comment: "Message in iOS NFC dialog"
			)

			self.resetInput = input
			self.resetCallback = callback
			session?.begin()

			log.info("session is ready")
		}
	}
	
	private func resetDriver(
		_ dna: DnaCommunicator
	) async {
		
		log.trace("resetDriver()")
		
		guard let input = resetInput else {
			fatalError("input is nil")
		}
		
		// Step 1 of 4:
		// Reset piccDataKey & cmacKey.
		//
		// As documented in the `writeDriver` above, there are a number of combinations that are possible:
		// - piccDataKey=key1, cmacKey=key2
		// - piccDataKey=key1, cmacKey=key3
		// - piccDataKey=key1, cmacKey=key4
		// - piccDataKey=key2, cmacKey=key3
		// - piccDataKey=key2, cmacKey=key4
		// - piccDataKey=key3, cmacKey=key4
		//
		// For our purposes here, we will consider the card properly reset
		// if we are able to reset 2 key positions.
		
		var position = await resetKey(dna, input.piccDataKey, startingPosition: .KEY_1)
		guard let piccDataKeyPosition = position else {
			return resetDisconnect(error: .keySlotsUnavailable)
		}
		log.debug("piccDataKeyPosition: \(piccDataKeyPosition.description)")
		
		position = await resetKey(dna, input.cmacKey, startingPosition: position?.next())
		guard let cmacKeyPosition = position else {
			return resetDisconnect(error: .keySlotsUnavailable)
		}
		log.debug("cmacKeyPosition: \(cmacKeyPosition.description)")
		
		// Step 2 of 4:
		// Reset file2 settings.
		
		let file2Settings = FileSettings.defaultFile2()
		do {
			let _ = try await writeFile2Settings(dna, file2Settings).get()
		} catch {
			return resetDisconnect(error: .protocolError(.writeFile2Settings, error))
		}
		
		// Step 3 of 4:
		// Reset file2 data.
		
		do {
			let url = URL(string: "https://phoenix.acinq.co")!
			let dataInfo = Ndef.ndefDataForUrl(url)
			try await writeFile2Data(dna, dataInfo.data, file2Settings).get()
		} catch {
			return resetDisconnect(error: .protocolError(.writeFile2Data, error))
		}
		
		// Step 4 of 4:
		// Change key0
		//
		// Note that after you perform this step,
		// if you wanted to make other changes to the card,
		// then you would need to reauthenticate.
		
		do {
			let _ = try await changeKey(dna, .KEY_0,
				oldKey : input.key0,
				newKey : DnaCommunicator.defaultKey
			).get()
		} catch {
			return resetDisconnect(error: .protocolError(.writeKey0, error))
		}
		
		resetDisconnect(result: .success(()))
	}
	
	private func resetDisconnect(error: WriteError) {
		resetDisconnect(result: .failure(error))
	}
	
	private func resetDisconnect(result: Result<Void, WriteError>) {
		
		queue.async {
			
			guard let session = self.session,
					let callback = self.resetCallback
			else {
				return
			}
			log.trace("disconnect()")
			
			session.invalidate()
			self.session = nil
			self.debugCallback = nil
			
			DispatchQueue.main.async {
				callback(result)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Debug Logic
	// --------------------------------------------------
	
#if DEBUG
	func debugSession(
		_ callback: @escaping (Result<Void, DebugError>) -> Void
	) {
		log.trace("debugSession()")
		
		let fail = { (error: DebugError) in
			DispatchQueue.main.async {
				callback(.failure(error))
			}
		}
		
		queue.async { [self] in
			
			guard NFCReaderSession.readingAvailable else {
				log.error("NFCReaderSession.readingAvailable is false")
				return fail(.readingNotAvailable)
			}
			
			guard session == nil else {
				log.error("session is already started")
				return fail(.alreadyStarted)
			}
			
			session = NFCTagReaderSession(pollingOption: .iso14443, delegate: self, queue: queue)
			session?.alertMessage = "Hold your card near the device to start debugging."
			
			self.debugCallback = callback
			session?.begin()
			
			log.info("session is ready")
		}
	}
#endif
	
	private func debugDriver(
		_ dna: DnaCommunicator
	) async {
		
		log.trace("debugDriver()")
		
		do {
			let _ = try await readChipUid(dna).get()
		} catch {
			return debugDisconnect(error: .readChipUid(error))
		}
		
		let file1Settings: FileSettings
		do {
			file1Settings = try await readFile1Settings(dna).get()
		} catch {
			return debugDisconnect(error: .readFile1Settings(error))
		}
		
		do {
			let _ = try await readFile1Data(dna, file1Settings).get()
		} catch {
			return debugDisconnect(error: .readFile1Data(error))
		}
		
		let file2Settings: FileSettings
		do {
			file2Settings = try await readFile2Settings(dna).get()
		} catch {
			return debugDisconnect(error: .readFile2Settings(error))
		}
		
		do {
			let _ = try await readFile2Data(dna, file2Settings).get()
		} catch {
			return debugDisconnect(error: .readFile2Data(error))
		}
		
		do {
			let _ = try await readFile3Settings(dna).get()
		} catch {
			return debugDisconnect(error: .readFile3Settings(error))
		}
		
		debugDisconnect(result: .success(()))
	}
	
	private func debugDisconnect(error: DebugError) {
		debugDisconnect(result: .failure(error))
	}
	
	private func debugDisconnect(result: Result<Void, DebugError>) {
		
		queue.async {
			
			guard let session = self.session,
					let callback = self.debugCallback
			else {
				return
			}
			log.trace("disconnect()")
			
			session.invalidate()
			self.session = nil
			self.debugCallback = nil
			
			DispatchQueue.main.async {
				callback(result)
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Reading
	// --------------------------------------------------
	
	private func readChipUid(
		_ dna: DnaCommunicator
	) async -> Result<[UInt8], Error> {
		
		log.trace("readChipUid()")
		
		let result = await dna.getChipUid()
		
		switch result {
		case .failure(let error):
			log.debug("dna.getChipUid: failed: \(error)")
			return .failure(error)
		
		case .success(let uid):
			log.debug("dna.getChipUid: success")
			log.debug("UID: \(uid.toHex())")
			
			return .success(uid)
		}
	}
	
	private func readFile1Settings(
		_ dna: DnaCommunicator
	) async -> Result<FileSettings, Error> {
		
		log.trace("readFile1Settings()")
		
		let result = await dna.getFileSettings(fileNum: .CC_FILE)
		
		switch result {
		case .failure(let error):
			log.error("dna.getFileSettings(1): error: \(error)")
			return .failure(error)
		
		case .success(let settings):
			log.debug("dna.getFileSettings(1): success")
			self.printFileSettings(settings, fileNum: 1)
			
		//	let result = settings.encode(mode: .GetFileSettings)
		//	switch result {
		//	case .success(let encoded):
		//		log.debug("Encoded: \(encoded.toHex())")
		//
		//	case .failure(let reason):
		//		fatalError("FileSettings.encode(): \(reason)")
		//	}
			
			return .success(settings)
		}
	}
	
	private func readFile1Data(
		_ dna: DnaCommunicator,
		_ settings: FileSettings
	) async -> Result<CapabilitiesContainer, Error> {
		
		log.trace("readFile1Data()")
		
		let length = 32
		let result = await dna.readFileData(
			fileNum: .CC_FILE,
			length: length,
			mode: settings.communicationMode
		)
		
		switch result {
		case .failure(let error):
			log.error("dna.readFileData(1): error: \(error)")
			return .failure(error)
			
		case .success(let data):
			log.debug("dna.readFileData(1): success")
			
			var fileData: [UInt8] = data
			if fileData.count > length {
				fileData = Array(fileData[0..<length])
			}
			
			log.debug("File(1): \(fileData.toHex())")
			
			if let cc = CapabilitiesContainer(data: fileData) {
				
			//	let encoded = cc.encode()
			//	log.debug("Rvrs(1): \(encoded.toHex())")
				
				printCapabilitiesContainer(cc)
				return .success(cc)
				
			} else {
				log.debug("File(1): Could not parse CapabilitiesContainer")
				
				let altErr = NSError(
					domain   : "NfcWriter",
					code     : 501,
					userInfo : ["message": "Invalid CapabilitiesContainer file"]
				)
				return .failure(altErr)
			}
		}
	}
	
	private func readFile2Settings(
		_ dna: DnaCommunicator
	) async -> Result<FileSettings, Error> {
		
		log.trace("readFile2Settings()")
		
		let result = await dna.getFileSettings(fileNum: .NDEF_FILE)
		
		switch result {
		case .failure(let error):
			log.error("dna.getFileSettings(2): error: \(error)")
			return .failure(error)
			
		case .success(let settings):
			log.debug("dna.getFileSettings(2): success")
			self.printFileSettings(settings, fileNum: 2)
			
		//	let result = settings.encode(mode: .GetFileSettings)
		//	switch result {
		//	case .success(let encoded):
		//		log.debug("Encoded: \(encoded.toHex())")
		//
		//	case .failure(let reason):
		//		fatalError("FileSettings.encode(): \(reason)")
		//	}
			
			return .success(settings)
		}
	}
	
	private func readFile2Data(
		_ dna      : DnaCommunicator,
		_ settings : FileSettings,
		_ prvData  : [UInt8]? = nil
	) async -> Result<[UInt8], Error> {
		
		log.trace("readFile2Data()")
		
		let length = 128 // this appears to be the max
		let offset = prvData?.count ?? 0
		
		let result = await dna.readFileData(
			fileNum : .NDEF_FILE,
			offset  : offset,
			length  : length,
			mode    : settings.communicationMode
		)
		
		switch result {
		case .failure(let error):
			log.error("dna.readFileData(2): error: \(error)")
			return .failure(error)
			
		case .success(let data):
			log.debug("dna.readFileData(2): success")
			log.debug("data.count = \(data.count)")
			
			var fixedData: [UInt8] = data
			if fixedData.count > length {
				fixedData = Array(data[0..<length])
			}
			
			log.debug("fixedData.count = \(fixedData.count)")
			
			if offset == 0 && fixedData.count == length {
				return await readFile2Data(dna, settings, fixedData)
				
			} else {
				let fileData = (prvData ?? []) + fixedData
				
				log.debug("fileData.count = \(fileData.count)")
				log.debug("File(2): \(fileData.toHex())")
				
				return .success(fileData)
			}
		}
	}
	
	private func readFile3Settings(
		_ dna: DnaCommunicator
	) async -> Result<FileSettings, Error> {
		
		log.trace("readFile3Settings()")
		
		let result = await dna.getFileSettings(fileNum: .PROPRIETARY)
		
		switch result {
		case .failure(let error):
			log.error("dna.getFileSettings(3): error: \(error)")
			return .failure(error)
			
		case .success(let settings):
			log.debug("dna.getFileSettings(3): success")
			self.printFileSettings(settings, fileNum: 3)
			
		//	let result = settings.encode(mode: .GetFileSettings)
		//	switch result {
		//	case .success(let encoded):
		//		log.debug("Encoded: \(encoded.toHex())")
		//
		//	case .failure(let reason):
		//		fatalError("FileSettings.encode(): \(reason)")
		//	}
			
			return .success(settings)
		}
	}
	
	private func readFile3Data(
		_ dna: DnaCommunicator,
		_ settings: FileSettings
	) async -> Result<[UInt8], Error> {
		
		log.trace("readFile3Data()")
		
		let length = 128
		let result = await dna.readFileData(
			fileNum: .PROPRIETARY,
			length: length,
			mode: settings.communicationMode
		)
		
		switch result {
		case .failure(let error):
			log.error("dna.readFileData(3): error: \(error)")
			return .failure(error)
			
		case .success(let data):
			log.debug("dna.readFileData(3): success")
			
			var fileData: [UInt8] = data
			if fileData.count > length {
				fileData = Array(fileData[0..<length])
			}
			
			log.debug("File(3): \(fileData.toHex())")
			return .success(fileData)
		}
	}
	
	// --------------------------------------------------
	// MARK: Writing
	// --------------------------------------------------
	
	private func writeKey(
		_ dna            : DnaCommunicator,
		_ newKey         : [UInt8],
		startingPosition : KeySpecifier?
	) async -> KeySpecifier? {
		
		guard var position = startingPosition else {
			return nil
		}
		
		while true {
			do {
				try await changeKey(dna, position,
					oldKey : DnaCommunicator.defaultKey,
					newKey : newKey
				).get()
				return position
				
			} catch {
				log.info("Unable to write to: \(position.description)")
				if let nextPosition = position.next() {
					position = nextPosition
				} else {
					return nil
				}
			}
		}
	}
	
	private func resetKey(
		_ dna            : DnaCommunicator,
		_ oldKey         : [UInt8],
		startingPosition : KeySpecifier?
	) async -> KeySpecifier? {
		
		guard var position = startingPosition else {
			return nil
		}
		
		while true {
			do {
				try await changeKey(dna, position,
					oldKey : oldKey,
					newKey : DnaCommunicator.defaultKey
				).get()
				return position
				
			} catch {
				log.info("Unable to write to: \(position.description)")
				if let nextPosition = position.next() {
					position = nextPosition
				} else {
					return nil
				}
			}
		}
	}
	
	private func changeKey(
		_ dna    : DnaCommunicator,
		_ keyNum : KeySpecifier,
		oldKey   : [UInt8],
		newKey   : [UInt8]
	) async -> Result<Void, Error> {
		
		log.trace("changeKey(\(keyNum))")
		
		let currentKeyVersion: UInt8
		let resultA = await dna.getKeyVersion(keyNum: keyNum)
		
		switch resultA {
		case .failure(let error):
			log.error("dna.getKeyVersion(\(keyNum)): error: \(error)")
			return .failure(error)
			
		case .success(let version):
			log.debug("dna.getKeyVersion(\(keyNum)): success: \(version)")
			currentKeyVersion = version
		}
		
		let newKeyVersion = currentKeyVersion + 1
		
		let result = await dna.changeKey(
			keyNum     : keyNum,
			oldKey     : oldKey,
			newKey     : newKey,
			keyVersion : newKeyVersion
		)
		
		switch result {
		case .failure(let error):
			log.error("dna.changeKey(\(keyNum)): error: \(error)")
			return .failure(error)
			
		case .success(_):
			log.debug("dna.changeKey(\(keyNum)): success")
			return .success(())
		}
	}
	
	private func writeFile2Settings(
		_ dna               : DnaCommunicator,
		_ template          : Ndef.Template,
		piccDataKeyPosition : KeySpecifier,
		cmacKeyPosition     : KeySpecifier
	) async -> Result<FileSettings, Error> {
		
		log.debug("writeFile2Settings()")
		
		var settings = FileSettings.defaultFile2()
		settings.sdmEnabled = true
		settings.communicationMode = .FULL
		settings.readPermission = .ALL
		settings.writePermission = .KEY_0
		settings.readWritePermission = .KEY_0
		settings.changePermission = .KEY_0
		settings.sdmOptionUid = true
		settings.sdmOptionReadCounter = true
		settings.sdmOptionUseAscii = true
		settings.sdmMetaReadPermission = piccDataKeyPosition.toPermission()
		settings.sdmFileReadPermission = cmacKeyPosition.toPermission()
		settings.sdmPiccDataOffset = UInt32(template.piccDataOffset)
		settings.sdmMacOffset = UInt32(template.cmacOffset)
		settings.sdmMacInputOffset = UInt32(template.cmacOffset)

		return await writeFile2Settings(dna, settings)
	}
	
	private func writeFile2Settings(
		_ dna               : DnaCommunicator,
		_ settings          : FileSettings
	) async -> Result<FileSettings, Error> {
		
		log.debug("writeFile2Settings()")

		printFileSettings(settings, fileNum: 2)
		
		var data: [UInt8] = []
		switch settings.encode(mode: .ChangeFileSettings) {
		case .failure(let error):
			log.error("FileSettings.encode(): error: \(error)")
			return .failure(error)
		
		case .success(let bytes):
			log.debug("FileSettings.encode(): success: \(bytes.toHex())")
			data = bytes
		}
		
		let result = await dna.changeFileSettings(fileNum: .NDEF_FILE, data: data)
		
		switch result {
		case .failure(let error):
			log.error("dna.changeFileSettings(2): error: \(error)")
			return .failure(error)
			
		case .success(_):
			log.debug("dna.changeFileSettings(2): success")
			return .success(settings)
		}
	}
	
	private func writeFile2Data(
		_ dna      : DnaCommunicator,
		_ data     : [UInt8],
		_ settings : FileSettings
	) async -> Result<Void, Error> {
		
		log.debug("writeFile2Data()")
		log.debug("data.count = \(data.count)")
		
		let result = await dna.writeFileData(
			fileNum : .NDEF_FILE,
			data    : data,
			mode    : settings.communicationMode
		)
		
		switch result {
		case .failure(let error):
			log.error("dna.writeFileData(2): error: \(error)")
			return .failure(error)
			
		case .success(_):
			log.debug("dna.writeFileData(2): success")
			return .success(())
		}
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func printFileSettings(_ fileSettings: FileSettings, fileNum: Int) {
		
		var output: String = ""
		output += "FileSettings(\(fileNum)):\n"
		output += " - fileType: \(fileSettings.fileType)\n"
		output += " - sdmEnabled: \(fileSettings.sdmEnabled)\n"
		output += " - communicationMode: \(fileSettings.communicationMode)\n"
		output += " - readPermission: \(fileSettings.readPermission)\n"
		output += " - writePermission: \(fileSettings.writePermission)\n"
		output += " - readWritePermission: \(fileSettings.readWritePermission)\n"
		output += " - changePermission: \(fileSettings.changePermission)\n"
		output += " - fileSize: \(fileSettings.fileSize)\n"
		output += " - sdmOptionUid: \(fileSettings.sdmOptionUid)\n"
		output += " - sdmOptionReadCounter: \(fileSettings.sdmOptionReadCounter)\n"
		output += " - sdmOptionReadCounterLimit: \(fileSettings.sdmOptionReadCounterLimit)\n"
		output += " - sdmOptionEncryptFileData: \(fileSettings.sdmOptionEncryptFileData)\n"
		output += " - sdmOptionUseAscii: \(fileSettings.sdmOptionUseAscii)\n"
		output += " - sdmMetaReadPermission: \(fileSettings.sdmMetaReadPermission)\n"
		output += " - sdmFileReadPermission: \(fileSettings.sdmFileReadPermission)\n"
		output += " - sdmReadCounterRetrievalPermission: \(fileSettings.sdmReadCounterRetrievalPermission)\n"
		output += " - sdmUidOffset: \(fileSettings.sdmUidOffset?.description ?? "nil")\n"
		output += " - sdmReadCounterOffset: \(fileSettings.sdmReadCounterOffset?.description ?? "nil")\n"
		output += " - sdmPiccDataOffset: \(fileSettings.sdmPiccDataOffset?.description ?? "nil")\n"
		output += " - sdmMacInputOffset: \(fileSettings.sdmMacInputOffset?.description ?? "nil")\n"
		output += " - sdmMacOffset: \(fileSettings.sdmMacOffset?.description ?? "nil")\n"
		output += " - sdmEncOffset: \(fileSettings.sdmEncOffset?.description ?? "nil")\n"
		output += " - sdmEncLength: \(fileSettings.sdmEncLength?.description ?? "nil")\n"
		output += " - sdmReadCounterLimit: \(fileSettings.sdmReadCounterLimit?.description ?? "nil")"
		
		log.debug("\(output)")
	}
	
	func printCapabilitiesContainer(_ cc: CapabilitiesContainer) {
		
		log.debug("\(cc.description)")
	}
	
	// --------------------------------------------------
	// MARK: NFCTagReaderSessionDelegate
	// --------------------------------------------------
	
	func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
		log.trace("tagReaderSessionDidBecomeActive(_):")
	}
	
	func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: any Error) {
		log.trace("tagReaderSession(_, didInvalidateWithError:)")
		log.trace("error: \(error)")
		
		let nfcError = (error as? NFCReaderError) ??                                   // this is always the case
			NFCReaderError(NFCReaderError.readerSessionInvalidationErrorSessionTimeout) // but just to be safe
		
		if isWriting {
			writeDisconnect(error: .scanningTerminated(nfcError))
		} else if isResetting {
			resetDisconnect(error: .scanningTerminated(nfcError))
		} else {
			debugDisconnect(error: .scanningTerminated(nfcError))
		}
	}
	
	func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
		log.trace("tagReaderSession(_, didDetect:): \(tags)")
		log.trace("tags.count = \(tags.count)")
		
		for tag in tags {
			log.debug("tag: \(tag)")
		}
		
		var properTag: NFCTag? = nil
		for tag in tags {
			if case .iso7816 = tag {
				if properTag == nil {
					properTag = tag
				}
			}
		}
		
		if let properTag {
			Task {
				await connectToTag(properTag)
			}
		} else {
			session.restartPolling()
			log.debug("did NOT find properTag")
		}
	}
}
