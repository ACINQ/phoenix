import SwiftUI

fileprivate let filename = "ScrollView_18"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct ScrollTargetLayoutViewModifier: ViewModifier {
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 18, *) {
			content.scrollTargetLayout()
		} else {
			content
		}
	}
}

struct ScrollTargetBehaviorViewModifier: ViewModifier {
	
	@ViewBuilder
	func body(content: Content) -> some View {
		if #available(iOS 18, *) {
			content.scrollTargetBehavior(.viewAligned)
		} else {
			content
		}
	}
}

enum ScrollAnchorRoleProxy {
	case alignment
	case initialOffset
	case sizeChanges
	
	@available(iOS 18.0, *)
	func convert() -> ScrollAnchorRole {
		switch self {
		case .alignment     : return ScrollAnchorRole.alignment
		case .initialOffset : return ScrollAnchorRole.initialOffset
		case .sizeChanges   : return ScrollAnchorRole.sizeChanges
		}
	}
}

@available(iOS 18.0, *)
struct DefaultScrollAnchorViewModifier: ViewModifier {
	let anchor: UnitPoint
	let role: ScrollAnchorRoleProxy
	
	@ViewBuilder
	func body(content: Content) -> some View {
		content
			.defaultScrollAnchor(anchor, for: role.convert())
	}
}

struct ScrollPositionProxy {
	
	private var _scrollPosition: Any?

	@available(iOS 18, *)
	var target: ScrollPosition {
		get { return _scrollPosition as! ScrollPosition }
		set { _scrollPosition = newValue }
	}

	init() {
		if #available(iOS 18, *) {
			target = ScrollPosition()
		}
	}
}

@available(iOS 18.0, *)
struct ScrollPositionViewModifier: ViewModifier {
	@Binding var position: ScrollPositionProxy
	let anchor: UnitPoint?
	
	@ViewBuilder
	func body(content: Content) -> some View {
		content.scrollPosition(_position.target)
	}
}

extension View {
	
	func _scrollTargetLayout() -> some View {
		modifier(ScrollTargetLayoutViewModifier())
	}
	
	func _scrollTargetBehavior() -> some View {
		modifier(ScrollTargetBehaviorViewModifier())
	}
	
	func _defaultScrollAnchor(_ anchor: UnitPoint, for role: ScrollAnchorRoleProxy) -> some View {
		modifier(__defaultScrollAnchor(anchor, role))
	}
	
	fileprivate func __defaultScrollAnchor(
		_ anchor: UnitPoint,
		_ role: ScrollAnchorRoleProxy
	) -> some ViewModifier {
		if #available(iOS 18, *) {
			return DefaultScrollAnchorViewModifier(anchor: anchor, role: role)
		} else {
			return EmptyModifier()
		}
	}
	
	func _scrollPosition(_ position: Binding<ScrollPositionProxy>, anchor: UnitPoint? = nil) -> some View {
		modifier(__scrollPosition(position, anchor: anchor))
	}
	
	fileprivate func __scrollPosition(
		_ position: Binding<ScrollPositionProxy>,
		anchor: UnitPoint?
	) -> some ViewModifier {
		if #available(iOS 18, *) {
			return ScrollPositionViewModifier(position: position, anchor: anchor)
		} else {
			return EmptyModifier()
		}
	}
}
