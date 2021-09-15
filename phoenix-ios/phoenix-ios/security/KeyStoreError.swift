/**
 * Apple Sample Code:
 * https://developer.apple.com/documentation/cryptokit/storing_cryptokit_keys_in_the_keychain
 *
 * Abstract:
 * The interface required for conversion to a generic password keychain item.
*/

import Foundation

/// An error we can throw when something goes wrong.
struct KeyStoreError: Error, CustomStringConvertible {
    var message: String
    
    init(_ message: String) {
        self.message = message
    }
    
    public var description: String {
        return message
    }
}

extension OSStatus {
    
    /// A human readable message for the status.
    var message: String {
        return (SecCopyErrorMessageString(self, nil) as String?) ?? String(self)
    }
}
