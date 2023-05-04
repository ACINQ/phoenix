import SwiftUI


struct AnimatedClock: View {
	
	enum ClockState {
		case past
		case present
		
		mutating func toggle() {
			switch self {
				case .past    : self = .present
				case .present : self = .past
			}
		}
	}
	
	@Binding var state: ClockState
	
	let size: CGFloat
	let animationDuration: TimeInterval
	
	private let lineThickness: CGFloat
	private let hourHandleLength: CGFloat
	private let minuteHandleLength: CGFloat
	private let centralPointSize: CGFloat
	
	init(state: Binding<ClockState>, size: CGFloat, animationDuration: TimeInterval) {
		
		self._state = state
		self.size = size
		self.animationDuration = animationDuration
		
		self.lineThickness = max(CGFloat(1.6), CGFloat(size * (8.0 / 300.0)))
		self.hourHandleLength = CGFloat(size * (80.0 / 300.0))
		self.minuteHandleLength = CGFloat(size * (120.0 / 300.0))
		self.centralPointSize = CGFloat(size * (12.0 / 300.0))
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			Circle()
				.frame(width: size, height: size)
				.foregroundColor(Color(UIColor.systemGray6))
			
			Circle()
				.stroke(lineWidth: lineThickness)
				.frame(width: size, height: size)
			
			// Minutes handle
			Rectangle()
				.frame(width: lineThickness, height: minuteHandleLength)
				.cornerRadius(.infinity)
				.rotationEffect(
					.degrees(state == .past ? -2160 : 0),
					anchor: .bottom
				)
				.offset(y: (-minuteHandleLength / 2.0))
				.animation(.linear(duration: animationDuration), value: state)
			
			// Hours handle
			Rectangle()
				.frame(width: lineThickness, height: hourHandleLength)
				.cornerRadius(.infinity)
				.rotationEffect(
					.degrees(state == .past ? -270 : -90),
					anchor: .top
				)
				.offset(y: (hourHandleLength / 2.0))
				.animation(.linear(duration: animationDuration), value: state)
			
			// Central point (looks good for bigger sizes)
			if size > 45 {

				let overlayMedium = centralPointSize / 3.0 * 2.0
				let overlaySmall = centralPointSize / 3.0 * 1.0
				Circle()
					.frame(width: centralPointSize, height: centralPointSize)
					.overlay(
						Circle()
							.frame(width: overlayMedium, height: overlayMedium)
							.foregroundColor(Color(UIColor.systemGray2))
					)
					.overlay(
						Circle()
							.frame(width: overlaySmall, height: overlaySmall)
							.foregroundColor(Color(UIColor.systemGray4))
					)
			}
		}
		.onTapGesture {
			state.toggle()
		}
	}
}
