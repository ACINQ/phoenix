import SwiftUI

import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MinerFeeSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct MinerFeeSheet: View {
	
	@Binding var satsPerByte: String
	@Binding var parsedSatsPerByte: Result<NSNumber, TextFieldNumberStylerError>
	@Binding var mempoolRecommendedResponse: MempoolRecommendedResponse?
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.smartModalState) var smartModalState: SmartModalState
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	
	enum Field: Hashable {
		case satsPerByteTextField
	}
	@FocusState private var focusedField: Field?
	
	enum PriorityBoxWidth: Preference {}
	let priorityBoxWidthReader = GeometryPreferenceReader(
		key: AppendValue<PriorityBoxWidth>.self,
		value: { [$0.size.width] }
	)
	@State var priorityBoxWidth: CGFloat? = nil
	
	enum PriorityBoxHeight: Preference {}
	let priorityBoxHeightReader = GeometryPreferenceReader(
		key: AppendValue<PriorityBoxHeight>.self,
		value: { [$0.size.height] }
	)
	@State var priorityBoxHeight: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Miner fee")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeButtonTapped()
			} label: {
				Image("ic_cross")
					.resizable()
					.frame(width: 30, height: 30)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.currentItem?.dismissable ?? false)
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			priorityBoxes()
				.padding(.horizontal, 8)
				.padding(.top)
				.padding(.bottom, 30)
			minerFeeFormula()
				.padding(.horizontal)
		}
	}
	
	@ViewBuilder
	func priorityBoxes() -> some View {
		
		if #available(iOS 16.0, *) {
			priorityBoxes_ios16()
		} else {
			priorityBoxes_ios15()
		}
	}
	
	@ViewBuilder
	@available(iOS 16.0, *)
	func priorityBoxes_ios16() -> some View {
		
		ViewThatFits {
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
			Grid(horizontalSpacing: 8, verticalSpacing: 8) {
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_economy()
					priorityBox_low()
					
				}
				GridRow(alignment: VerticalAlignment.center) {
					priorityBox_medium()
					priorityBox_high()
				}
			} // </Grid>
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBoxes_ios15() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 8) {
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_economy()
				priorityBox_low()
			}
			HStack(alignment: VerticalAlignment.center, spacing: 8) {
				priorityBox_medium()
				priorityBox_high()
			}
		}
		.assignMaxPreference(for: priorityBoxWidthReader.key, to: $priorityBoxWidth)
	}
	
	@ViewBuilder
	func priorityBox_economy() -> some View {
		
		GroupBox {
			Text("≈ 1+ days")
		} label: {
			Text("No Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.none),
			tapped: { priorityTapped(.none) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_low() -> some View {
		
		GroupBox {
			Text("≈ 1 hour")
		} label: {
			Text("Low Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.low),
			tapped: { priorityTapped(.low) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_medium() -> some View {
		
		GroupBox {
			Text("≈ 30 minutes")
		} label: {
			Text("Medium Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.medium),
			tapped: { priorityTapped(.medium) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func priorityBox_high() -> some View {
		
		GroupBox {
			Text("≈ 10 minutes")
		} label: {
			Text("High Priority")
		}
		.groupBoxStyle(PriorityBoxStyle(
			width: priorityBoxWidth,
			disabled: isPriorityDisabled(),
			selected: isPrioritySelected(.high),
			tapped: { priorityTapped(.high) }
		))
		.read(priorityBoxWidthReader)
	}
	
	@ViewBuilder
	func minerFeeFormula() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 8) {
			satsPerByteTextField()
			minerFeeAmounts()
		}
	}
	
	@ViewBuilder
	func satsPerByteTextField() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				
				TextField("", text: satsPerByteStyler().amountProxy)
					.keyboardType(.numberPad)
					.focused($focusedField, equals: .satsPerByteTextField)
					.frame(maxWidth: 60)
			}
			.padding(.vertical, 8)
			.padding(.horizontal, 12)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(isInvalidSatsPerByte ? Color.appNegative : Color.textFieldBorder, lineWidth: 1)
			)
			
			Text(verbatim: "sats/vByte")
				.font(.callout)
				.foregroundColor(.secondary)
				.padding(.leading, 4)
		}
	}
	
	@ViewBuilder
	func minerFeeAmounts() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			Text("= 1,142 sat")
			Text("≈ 1,200 COP")
		}
		.font(.callout)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var isInvalidSatsPerByte: Bool {
		switch parsedSatsPerByte {
		case .success(_):
			return false
			
		case .failure(let reason):
			switch reason {
				case .emptyInput   : return false
				case .invalidInput : return true
			}
		}
	}
	
	func satsPerByteFormater() -> NumberFormatter {
		
		let nf = NumberFormatter()
		nf.numberStyle = .decimal
		
		return nf
	}
	
	func satsPerByteStyler() -> TextFieldNumberStyler {
		return TextFieldNumberStyler(
			formatter: satsPerByteFormater(),
			amount: $satsPerByte,
			parsedAmount: $parsedSatsPerByte
		)
	}
	
	func isPriorityDisabled() -> Bool {
		
		return (mempoolRecommendedResponse == nil)
	}
	
	func isPrioritySelected(_ priority: MinerFeePriority) -> Bool {
		
		if let mempoolRecommendedResponse {
		
			switch parsedSatsPerByte {
			case .success(let amount):
				return amount.doubleValue == mempoolRecommendedResponse.feeForPriority(priority)
			case .failure(_):
				return false
			}
			
		} else {
			return false
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func priorityTapped(_ priority: MinerFeePriority) {
		log.trace("priorityTapped()")
		
		guard let mempoolRecommendedResponse else {
			return
		}
		
		let fee = mempoolRecommendedResponse.feeForPriority(priority)
		
		parsedSatsPerByte = .success(NSNumber(value: fee))
		satsPerByte = fee.description
	}
	
	func closeButtonTapped() {
		log.trace("closeButtonTapped()")
		
		smartModalState.close()
	}
}

// MARK: -

struct PriorityBoxStyle: GroupBoxStyle {
	
	let width: CGFloat?
	let disabled: Bool
	let selected: Bool
	let tapped: () -> Void
	
	func makeBody(configuration: GroupBoxStyleConfiguration) -> some View {
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			configuration.label
				.font(.headline)
			configuration.content
		}
		.frame(width: width?.advanced(by: -16.0))
		.padding(.all, 8)
		.background(RoundedRectangle(cornerRadius: 8, style: .continuous)
			.fill(Color(UIColor.quaternarySystemFill)))
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(selected ? Color.appAccent : Color(UIColor.quaternarySystemFill), lineWidth: 1)
		)
		.onTapGesture {
			tapped()
		}
	}
}
