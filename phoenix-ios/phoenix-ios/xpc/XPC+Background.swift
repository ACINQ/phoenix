import Foundation

/// This file is **ONLY** for the Notify-Service-Extension (background process)
///
extension XPC {
	public static let shared = XPC(actor: .notifySrvExt)
}
