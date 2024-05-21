import SwiftUI

enum VibrationFeedbackType {
	case error
	
	@available(iOS 17.0, *)
	func convert() -> SensoryFeedback {
		switch self {
			case .error : return SensoryFeedback.error
		}
	}
}

struct VibrationFeedback<T>: ViewModifier where T: Equatable {
	let type: VibrationFeedbackType
	let trigger: T
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 17.0, *) {
			content
				.sensoryFeedback(type.convert(), trigger: trigger)
		} else {
			content
		}
	}
}

extension View {
	func vibrationFeedback<T>(_ type: VibrationFeedbackType, trigger: T) -> some View where T : Equatable {
		ModifiedContent(
			content: self,
			modifier: VibrationFeedback(type: type, trigger: trigger)
		)
	}
}
