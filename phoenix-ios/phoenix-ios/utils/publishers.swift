import Combine
import UIKit

struct KeyboardInfo {
	var height: CGFloat = 0
	var animationCurve: UIView.AnimationCurve = UIView.AnimationCurve.easeInOut
	var animationDuration: TimeInterval = 0.0
}

extension Publishers {
	static var keyboardInfo: AnyPublisher<KeyboardInfo, Never> {
		let willShow = NotificationCenter.default.publisher(for: UIApplication.keyboardWillShowNotification)
				.map { $0.keyboardInfo }

		let willHide = NotificationCenter.default.publisher(for: UIApplication.keyboardWillHideNotification)
				.map { _ in KeyboardInfo() }

		return MergeMany(willShow, willHide)
			.eraseToAnyPublisher()
	}
}

extension Notification {
	
	var keyboardInfo: KeyboardInfo {
		var info = KeyboardInfo()
		if let value = userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
			info.height = value.height
		}
		if let value = userInfo?[UIResponder.keyboardAnimationCurveUserInfoKey] as? NSNumber {
			if let curve = UIView.AnimationCurve(rawValue: value.intValue) {
				info.animationCurve = curve
			}
		}
		if let value = userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber {
			info.animationDuration = value.doubleValue
		}
		return info
	}
}
