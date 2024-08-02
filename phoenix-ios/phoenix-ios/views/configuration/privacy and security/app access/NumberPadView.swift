import SwiftUI

fileprivate let filename = "NumberPadView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

enum NumberPadButton: String {
	case _0 = "0"
	case _1 = "1"
	case _2 = "2"
	case _3 = "3"
	case _4 = "4"
	case _5 = "5"
	case _6 = "6"
	case _7 = "7"
	case _8 = "8"
	case _9 = "9"
	case hide = "hide"
	case delete = "delete"
}

struct NumberPadView: View {
	
	let buttonPressed: (NumberPadButton) -> Void
	let showHideButton: Bool
	@Binding var disabled: Bool
	
	enum NumberPadButtonWidth: Preference {}
	let numberPadButtonWidthReader = GeometryPreferenceReader(
		key: AppendValue<NumberPadButtonWidth>.self,
		value: { [$0.size.width] }
	)
	@State var numberPadButtonWidth: CGFloat? = nil
	
	enum NumberPadButtonHeight: Preference {}
	let numberPadButtonHeightReader = GeometryPreferenceReader(
		key: AppendValue<NumberPadButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var numberPadButtonHeight: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		let showAlphabet = isLatinScript
		
		VStack(alignment: HorizontalAlignment.trailing, spacing: 20) {
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				numberPadButton(._1, showAlphabet)
				numberPadButton(._2, showAlphabet)
				numberPadButton(._3, showAlphabet)
			}
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				numberPadButton(._4, showAlphabet)
				numberPadButton(._5, showAlphabet)
				numberPadButton(._6, showAlphabet)
			}
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				numberPadButton(._7, showAlphabet)
				numberPadButton(._8, showAlphabet)
				numberPadButton(._9, showAlphabet)
			}
			HStack(alignment: VerticalAlignment.center, spacing: 20) {
				if showHideButton {
					numberPadButton(.hide, showAlphabet)
				}
				numberPadButton(._0, showAlphabet)
				numberPadButton(.delete, showAlphabet)
			}
		} // </VStack>
		.assignMaxPreference(for: numberPadButtonWidthReader.key, to: $numberPadButtonWidth)
		.assignMaxPreference(for: numberPadButtonHeightReader.key, to: $numberPadButtonHeight)
	}
	
	@ViewBuilder
	func numberPadButton(_ identifier: NumberPadButton, _ showAlphabet: Bool) -> some View {
		
		if identifier == .hide {
			
			Button {
				buttonPressed(identifier)
			} label: {
				Group {
					if #available(iOS 17, *) {
						Image(systemName: "arrowshape.down")
							.resizable()
							.aspectRatio(contentMode: .fit)
					} else {
						Image(systemName: "arrowtriangle.down")
							.resizable()
							.aspectRatio(contentMode: .fit)
					}
				}
				.frame(width: 34, height: 34, alignment: .center)
				.frame(width: buttonSize, height: buttonSize, alignment: .center)
				.padding(.all, 10)
			} // </Button>
			.tint(.secondary)
			
		} else if identifier == .delete {
			
			Button {
				buttonPressed(identifier)
			} label: {
				Image(systemName: "delete.backward")
					.resizable()
					.aspectRatio(contentMode: .fit)
					.frame(width: 34, height: 34, alignment: .center)
					.frame(width: buttonSize, height: buttonSize, alignment: .center)
					.padding(.all, 10)
			} // </Button>
			.tint(.secondary)
			.disabled(self.disabled)
			
		} else {
			
			Button {
				buttonPressed(identifier)
			} label: {
				VStack(alignment: HorizontalAlignment.center, spacing: 2) {
					
					Text(verbatim: identifier.rawValue)
						.font(.system(size: 34, weight: .medium))
						.padding(showAlphabet ? 0 : 4)
					
					if showAlphabet {
						Group {
							switch identifier {
							case ._2 : Text(verbatim: "ABC")
							case ._3 : Text(verbatim: "DEF")
							case ._4 : Text(verbatim: "GHI")
							case ._5 : Text(verbatim: "JKL")
							case ._6 : Text(verbatim: "MNO")
							case ._7 : Text(verbatim: "PQRS")
							case ._8 : Text(verbatim: "TUV")
							case ._9 : Text(verbatim: "WXYZ")
							default  : Text(verbatim: "-").foregroundStyle(.clear)
							}
						}
						.font(.system(size: 12, weight: .regular))
						.tracking(1)
					}
				} // </VStack>
				.opacity(disabled ? 0.5 : 1.0)
				.read(numberPadButtonWidthReader)
				.read(numberPadButtonHeightReader)
				.frame(width: buttonSize, height: buttonSize, alignment: .center)
				.padding(.all, 10)
			} // </Button>
			.tint(.primary)
			.buttonStyle(NumberPadButtonStyle(
				backgroundFill: Color(uiColor: .systemGray4),
				pressedBackgroundFill: Color(uiColor: .systemGray2),
				disabledBackgroundFill: Color(uiColor: .systemGray5)
			))
			.disabled(self.disabled)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var buttonSize: CGFloat? {
		
		guard let numberPadButtonWidth, let numberPadButtonHeight else {
			return nil
		}
		
		return max(numberPadButtonWidth, numberPadButtonHeight)
	}
	
	var isLatinScript: Bool {
		
		if let script = Locale.current.language.script {
			return script == Locale.Script.latin
		}
		
		return false
	}
}

// --------------------------------------------------
// MARK: -
// --------------------------------------------------

struct NumberPadButtonStyle: ButtonStyle {
	
	let backgroundFill: Color
	let pressedBackgroundFill: Color
	let disabledBackgroundFill: Color
	
	init(
		backgroundFill: Color,
		pressedBackgroundFill: Color,
		disabledBackgroundFill: Color
	) {
		self.backgroundFill = backgroundFill
		self.pressedBackgroundFill = pressedBackgroundFill
		self.disabledBackgroundFill = disabledBackgroundFill
	}
	
	func makeBody(configuration: Self.Configuration) -> some View {
		NumberPadButtonStyleView(
			configuration: configuration,
			backgroundFill: backgroundFill,
			pressedBackgroundFill: pressedBackgroundFill,
			disabledBackgroundFill: disabledBackgroundFill
		)
	}
	
	// Subclass of View is required to properly use @Environment variable.
	// To be more specific:
	//   You can put the @Environment variable directly within ButtonStyle,
	//   and reference it within `makeBody`. And it will compile fine.
	//   It just won't work, because it won't be updated properly.
	//
	struct NumberPadButtonStyleView: View {
		
		let configuration: ButtonStyle.Configuration
		
		let backgroundFill: Color
		let pressedBackgroundFill: Color
		let disabledBackgroundFill: Color
		
		@Environment(\.isEnabled) private var isEnabled: Bool
		
		var body: some View {
			configuration.label
				.background(isEnabled
				  ? (configuration.isPressed ? pressedBackgroundFill : backgroundFill)
				  : disabledBackgroundFill,
				  in: Circle()
				)
		}
	}
}
