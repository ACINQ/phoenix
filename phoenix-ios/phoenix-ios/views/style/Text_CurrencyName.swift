import SwiftUI
import PhoenixShared

/// Standardized display of the currency name.
///
/// For most currencies, this is just simple text:
/// - "EUR"
///
/// But some currencies have special handling:
/// - "ARSbm"
///       ^^ we want to display this part with a condensed font
///
struct Text_CurrencyName: View {
	let currency: Currency
	let fontTextStyle: Font.TextStyle
	
	@ScaledMetric var leadingPadding: CGFloat
	
	init(currency: Currency, fontTextStyle: Font.TextStyle) {
		self.currency = currency
		self.fontTextStyle = fontTextStyle
		
		let value: CGFloat
		switch fontTextStyle {
			case .largeTitle  : value = 2.0
			case .title       : value = 1.5
			case .title2      : value = 1.5
			case .title3      : value = 1.5
			case .headline    : value = 1.0
			case .body        : value = 1.0
			case .callout     : value = 0.5
			case .subheadline : value = 0.5
			case .footnote    : value = 0.5
			case .caption     : value = 0.5
			case .caption2    : value = 0.5
			@unknown default  : value = 0.5
		}
		
		_leadingPadding = ScaledMetric(wrappedValue: value, relativeTo: fontTextStyle)
	}
	
	init(fiatCurrency: FiatCurrency, fontTextStyle: Font.TextStyle) {
		
		self.init(currency: Currency.fiat(fiatCurrency), fontTextStyle: fontTextStyle)
	}
	
	@ViewBuilder
	var body: some View {
		
		if #available(iOS 16, *) {
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 0) {
				let (currencyName1, currencyName2) = currency.splitShortName
				Text(currencyName1)
				if !currencyName2.isEmpty {
					Text(currencyName2).fontWidth(.condensed).padding(.leading, leadingPadding)
				}
			}
			.environment(\.layoutDirection, .leftToRight) // issue #237
			.font(.system(fontTextStyle))
		} else {
			Text(currency.shortName)
				.font(.system(fontTextStyle))
		}
	}
}
