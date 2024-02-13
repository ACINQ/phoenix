//
// Inspired by: Karthick Selvaraj
// https://github.com/karthironald/ActivityAnimations
//

import SwiftUI
import Combine

fileprivate let filename = "HorizontalActivity"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct HorizontalActivity: View {
	
	init(color: Color, diameter: CGFloat, speed: TimeInterval = 1.6) {
		self.color = color
		self.diameter = diameter
		self.speed = speed
		
		self.timer = Timer.publish(every: speed, on: .main, in: .common).autoconnect()
	}
	
	let color: Color
	let diameter: CGFloat
	let speed: TimeInterval
	
	let timer: Publishers.Autoconnect<Timer.TimerPublisher>
	
	@State private var viewWidth: CGFloat? = nil
	@State private var offsetA: CGFloat = 0
	@State private var offsetB: CGFloat = 0
	@State private var showCircles = false
	
	struct HorizontalActivityWidth: PreferenceKey {
		typealias Value = [CGFloat]
		static var defaultValue: Value { [] }
		static func reduce(value: inout Value, nextValue: () -> Value) {
			value.append(contentsOf: nextValue())
		}
	}
	
	@ViewBuilder
	var body: some View {
		
		GeometryReader { proxy in
			ZStack {
				Color.clear.preference(key: HorizontalActivityWidth.self, value: [proxy.size.width])
				
				if showCircles {
					Circle()
						.fill(color)
						.frame(width: diameter, height: diameter)
						.offset(x: offsetA)
						.opacity(0.7)
						.animation(Animation.easeInOut(duration: 1), value: UUID())
					Circle()
						.fill(color)
						.frame(width: diameter, height: diameter)
						.offset(x: offsetA)
						.opacity(0.7)
						.animation(Animation.easeInOut(duration: 1).delay(0.2), value: UUID())
					Circle()
						.fill(color)
						.frame(width: diameter, height: diameter)
						.offset(x: offsetA)
						.opacity(0.7)
						.animation(Animation.easeInOut(duration: 1).delay(0.4), value: UUID())
				}
			}
		}
		.onPreferenceChange(HorizontalActivityWidth.self) { (values: [CGFloat]) in
			let maxValue = values.reduce(0, max)
			if maxValue > 0 {
				viewWidth = maxValue
			}
		}
		.onChange(of: viewWidth) { _ in
			initializeCircles()
		}
		.onChange(of: showCircles) { _ in
			performAnimation()
		}
		.onReceive(timer) { _ in
			performAnimation()
		}
	}
	
	func initializeCircles() {
		guard let fullWidth = viewWidth else {
			return
		}
		
		offsetB = (fullWidth / 2.0) - (diameter / 2.0)
		offsetA = offsetB * -1.0
		showCircles = true
	}
	
	func performAnimation() {
		if viewWidth != nil {
			swap(&self.offsetA, &self.offsetB)
		}
	}
}
