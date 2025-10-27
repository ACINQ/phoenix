import Foundation

enum ReadSecurityFileError: Error, CustomStringConvertible {
	case fileNotFound
	case errorReadingFile(underlying: Error)
	case errorDecodingFile(underlying: Error)
	
	var description: String {
		switch self {
		case .fileNotFound:
			"<ReadSecurityFileError: fileNotFound>"
		case .errorReadingFile(let underlying):
			"<ReadSecurityFileError: errorReadingFile: \(underlying)>"
		case .errorDecodingFile(let underlying):
			"<ReadSecurityFileError: errorDecodingFile: \(underlying)>"
		}
	}
}

enum WriteSecurityFileError: Error, CustomStringConvertible {
	case errorEncodingFile(underlying: Error)
	case errorWritingFile(underlying: Error)
	
	var description: String {
		switch self {
		case .errorEncodingFile(let underlying):
			"<WriteSecurityFileError: errorEncodingFile: \(underlying)>"
		case .errorWritingFile(let underlying):
			"<WriteSecurityFileError: errorWritingFile: \(underlying)>"
		}
	}
}

enum ReadKeychainError: Error, CustomStringConvertible {
	case keychainBoxCorrupted(underlying: Error)
	case errorReadingKey(underlying: Error)
	case keyNotFound
	case errorOpeningBox(underlying: Error)
	
	var description: String {
		switch self {
		case .keychainBoxCorrupted(let underlying):
			"<ReadKeychainError: keychainBoxCorrupted: \(underlying)>"
		case .errorReadingKey(let underlying):
			"<ReadKeychainError: errorReadingKey: \(underlying)>"
		case .keyNotFound:
			"<ReadKeychainError: keyNotFound>"
		case .errorOpeningBox(let underlying):
			"<ReadKeychainError: errorOpeningBox: \(underlying)>"
		}
	}
}

enum ReadRecoveryPhraseError: Error, CustomStringConvertible {
	case invalidCiphertext
	case invalidJSON
	
	var description: String {
		switch self {
		case .invalidCiphertext:
			"<ReadRecoveryPhraseError: invalidCiphertext>"
		case .invalidJSON:
			"<ReadRecoveryPhraseError: invalidJSON>"
		}
	}
}

enum AddEntryError: Error, CustomStringConvertible {
	case existingSecurityFileV0
	case errorEncodingRecoveryPhrase(underlying: Error)
	case errorWritingToKeychain(underlying: Error)
	case errorWritingSecurityFile(underlying: WriteSecurityFileError)
	
	var description: String {
		switch self {
		case .existingSecurityFileV0:
			"<AddEntryError: existingSecurityFileV0>"
		case .errorEncodingRecoveryPhrase(let underlying):
			"<AddEntryError: errorEncodingRecoveryPhrase: \(underlying)>"
		case .errorWritingToKeychain(let underlying):
			"<AddEntryError: errorWritingToKeychain: \(underlying)>"
		case .errorWritingSecurityFile(let underlying):
			"<AddEntryError: errorWritingSecurityFile: \(underlying)>"
		}
	}
}

enum RemoveEntryError: Error, CustomStringConvertible {
	case existingSecurityFileV0
	case errorWritingSecurityFile(underlying: WriteSecurityFileError)
	
	var description: String {
		switch self {
		case .existingSecurityFileV0:
			"<RemoveEntryError: existingSecurityFileV0>"
		case .errorWritingSecurityFile(let underlying):
			"<RemoveEntryError: errorWritingSecurityFile: \(underlying)>"
		}
	}
}

enum UnlockError: Error, CustomStringConvertible {
	case readSecurityFileError(underlying: ReadSecurityFileError)
	case readKeychainError(underlying: ReadKeychainError)
	case readRecoveryPhraseError(underlying: ReadRecoveryPhraseError)
	
	var description: String {
		switch self {
		case .readSecurityFileError(let underlying):
			"<UnlockError: readSecurityFileError: \(underlying)>"
		case .readKeychainError(let underlying):
			"<UnlockError: readKeychainError: \(underlying)>"
		case .readRecoveryPhraseError(let underlying):
			"<UnlockError: readRecoveryPhraseError: \(underlying)>"
		}
	}
}
