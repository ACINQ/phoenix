import Foundation

// In earlier versions of Swift, you could setup an enum with Void type:
// ```
// enum Result<T> {
//   case success(T)
//   case error(Error?)
// }
// ```
//
// And then you could omit the associated value of type Void:
// ```
// finish(.success())
// ```
//
// This no longer works as of Swift 4, and now you have to do one of:
// ```
// finish(.success(()))
// finish(.success(Void()))
// ```
//
// The community consensus is that this is "ugly".
// And this extension restores the cleanliness of previous Swift versions.
//
extension Result where Success == Void {
	static var success: Result {
		return .success(())
	}
}

// These should exist in the standard library, but they don't.
extension Result {
	
	var successValue: Success? {
		switch self {
		case .success(let value):
			return value
		case .failure:
			return nil
		}
	}
	
	var isSuccess: Bool {
		if case .success = self {
			return true
		} else {
			return false
		}
	}

	var isError: Bool {
		return !isSuccess
	}
}
