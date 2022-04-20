import SwiftUI

/// How to use:
///
/// ```
/// Button {
///   // button action...
/// } label: {
///   HStack {
///     Image()
///     Text()
///   }
///   .padding(...)
/// }
/// .buttonStyle(ScaleButtonStyle(
///   backgroundFill: Color.blue,
///   disabledBackgroundFill: Color.gray
/// ))
/// .disabled(stateVar)
/// ```
///
/// You can choose between a backgroundFill or borderStroke, or use both.
/// 

struct ScaleButtonStyle: ButtonStyle {

	let scaleAmount: CGFloat
	let cornerRadius: CGFloat
	
	let backgroundFill: Color
	let disabledBackgroundFill: Color
	
	let borderStroke: Color
	let disabledBorderStroke: Color
	
	let pressedOpacity: Double
	let disabledOpacity: Double
	
	init(
		scaleAmount: CGFloat = 0.98,
		cornerRadius: CGFloat,
		backgroundFill: Color = Color.clear,
		disabledBackgroundFill: Color = Color.clear,
		borderStroke: Color = Color.clear,
		disabledBorderStroke: Color = Color.clear,
		pressedOpacity: Double = 0.65,
		disabledOpacity: Double = 0.65
	) {
		self.scaleAmount = scaleAmount
		self.cornerRadius = cornerRadius
		self.backgroundFill = backgroundFill
		self.disabledBackgroundFill = disabledBackgroundFill
		self.borderStroke = borderStroke
		self.disabledBorderStroke = disabledBorderStroke
		self.pressedOpacity = pressedOpacity
		self.disabledOpacity = disabledOpacity
	}
	
	func makeBody(configuration: Self.Configuration) -> some View {
		ScaleButtonStyleView(
			configuration: configuration,
			scaleAmount: scaleAmount,
			cornerRadius: cornerRadius,
			backgroundFill: backgroundFill,
			disabledBackgroundFill: disabledBackgroundFill,
			borderStroke: borderStroke,
			disabledBorderStroke: disabledBorderStroke,
			pressedOpacity: pressedOpacity,
			disabledOpacity: disabledOpacity
		)
	}
	
	// Subclass of View is required to properly use @Environment variable.
	// To be more specific:
	//   You can put the @Environment variable directly within ButtonStyle,
	//   and reference it within `makeBody`. And it will compile fine.
	//   It just won't work, because it won't be updated properly.
	//
	struct ScaleButtonStyleView: View {
		
		let configuration: ButtonStyle.Configuration
		let scaleAmount: CGFloat
		let cornerRadius: CGFloat
		
		let backgroundFill: Color
		let disabledBackgroundFill: Color
		
		let borderStroke: Color
		let disabledBorderStroke: Color
		
		let pressedOpacity: Double
		let disabledOpacity: Double
		
		@Environment(\.isEnabled) private var isEnabled: Bool
		
		var body: some View {
			configuration.label
				.opacity(isEnabled ? (configuration.isPressed ? pressedOpacity : 1.0) : disabledOpacity)
				.scaleEffect(configuration.isPressed ? scaleAmount : 1.0)
				.background(isEnabled ? backgroundFill : disabledBackgroundFill)
				.cornerRadius(cornerRadius)
				.overlay(
					RoundedRectangle(cornerRadius: cornerRadius)
						.stroke(isEnabled ? borderStroke : disabledBorderStroke, lineWidth: 1.5)
				)
		}
	}
}
