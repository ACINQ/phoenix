import Foundation

/// This file is **ONLY** for the main Phoenix app
///
extension XPC {
	public static let shared = XPC(actor: .mainApp)
}
