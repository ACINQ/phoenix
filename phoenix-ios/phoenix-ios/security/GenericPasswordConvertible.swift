/**
 * Apple Sample Code:
 * https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain
 *
 * Abstract:
 * The interface required for conversion to a generic password keychain item.
*/

import Foundation
import CryptoKit

/// The interface needed for SecKey conversion.
protocol GenericPasswordConvertible: CustomStringConvertible {
    /// Creates a key from a raw representation.
    init<D>(rawRepresentation data: D) throws where D: ContiguousBytes
    
    /// A raw representation of the key.
    var rawRepresentation: Data { get }
}

extension GenericPasswordConvertible {
    /// A string version of the key for visual inspection.
    /// IMPORTANT: Never log the actual key data.
    public var description: String {
        return self.rawRepresentation.withUnsafeBytes { bytes in
            return "Key representation contains \(bytes.count) bytes."
        }
    }
}

// Declare that the Curve25519 keys are generic password convertible.
extension Curve25519.KeyAgreement.PrivateKey: @retroactive CustomStringConvertible {}
extension Curve25519.KeyAgreement.PrivateKey: GenericPasswordConvertible {}

extension Curve25519.Signing.PrivateKey: @retroactive CustomStringConvertible {}
extension Curve25519.Signing.PrivateKey: GenericPasswordConvertible {}

// Extend SymmetricKey to conform to GenericPasswordConvertible.
extension SymmetricKey: @retroactive CustomStringConvertible {}
extension SymmetricKey: GenericPasswordConvertible {
	
    init<D>(rawRepresentation data: D) throws where D: ContiguousBytes {
        self.init(data: data)
    }
    
    var rawRepresentation: Data {
        return dataRepresentation  // Contiguous bytes repackaged as a Data instance.
    }
}

// Extend SecureEnclave keys to conform to GenericPasswordConvertible.
extension SecureEnclave.P256.KeyAgreement.PrivateKey: @retroactive CustomStringConvertible {}
extension SecureEnclave.P256.KeyAgreement.PrivateKey: GenericPasswordConvertible {
	
    init<D>(rawRepresentation data: D) throws where D: ContiguousBytes {
        try self.init(dataRepresentation: data.dataRepresentation)
    }
    
    var rawRepresentation: Data {
        return dataRepresentation  // Contiguous bytes repackaged as a Data instance.
    }
}
extension SecureEnclave.P256.Signing.PrivateKey: @retroactive CustomStringConvertible {}
extension SecureEnclave.P256.Signing.PrivateKey: GenericPasswordConvertible {
	
    init<D>(rawRepresentation data: D) throws where D: ContiguousBytes {
        try self.init(dataRepresentation: data.dataRepresentation)
    }
    
    var rawRepresentation: Data {
        return dataRepresentation  // Contiguous bytes repackaged as a Data instance.
    }
}

extension ContiguousBytes {
	
	/// A Data instance created from the contiguous bytes.
	///
	/// Note: In the original version of this code, Apple used `CFDataCreateWithBytesNoCopy`,
	/// which creates non-owned pointer to the bytes.
	/// However, this is unacceptably dangerous (could lead to dangling pointers), and has been removed.
	/// During code analysis, we discovered that we were using `dataRepresentation` in certain cases,
	/// where the resulting `Data` object would out-live the owner (such as as `ChaChaPoly.SealedBox`).
	///
	var dataRepresentation: Data {
		return self.withUnsafeBytes { bytes in
			Data(bytes: bytes.baseAddress!, count: bytes.count)
		}
	}
}

