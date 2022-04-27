import Foundation
import Punycode

@objc
public class NativeIDN: NSObject {
	
	@objc
	public class func idnEncode(host: String) -> String? {
		return host.idnaEncoded
	}
	
	@objc
	public class func idnDecode(host: String) -> String? {
		return host.idnaDecoded
	}
}
