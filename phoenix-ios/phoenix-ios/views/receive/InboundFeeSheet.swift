import SwiftUI
import PhoenixShared

fileprivate let filename = "InboundFeeSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct InboundFeeSheet: View {
	
	let warning: InboundFeeWarning
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var smartModalState: SmartModalState
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Group {
				switch warning.type {
				case .willFail:
					Text("Payment may fail")
				case .feeExpected:
					Text("On-chain fee expected")
				}
			}
			.font(.title3)
			.accessibilityAddTraits(.isHeader)
			.accessibilitySortPriority(100)
			
			Spacer()
			
			Button {
				closeSheet()
			} label: {
				Image(systemName: "xmark").imageScale(.medium).font(.title2)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			content_message()
			content_button()
		}
		.padding(.all)
	}
	
	@ViewBuilder
	func content_message() -> some View {
		
		switch warning {
		case .liquidityPolicyDisabled:
			Text(
				"""
				Inbound liquidity is insufficient for this amount, \
				and you have disabled automated channel management.
				"""
			)
			
		case .overAbsoluteFee(let canRequestLiquidity, let maxAbsoluteFeeSats, let swapFeeSats):
			let fee = Utils.formatBitcoin(currencyPrefs, sat: swapFeeSats).string
			let limit = Utils.formatBitcoin(currencyPrefs, sat: maxAbsoluteFeeSats).string
			if canRequestLiquidity {
				Text(
					"""
					A fee of \(fee) is expected to receive this amount, \
					and that fee is above your limit of \(limit).

					Increase this limit in the channel management settings, \
					or request additional liquidity.
					"""
				)
			} else {
				Text(
					"""
					A fee of \(fee) is expected to receive this amount, \
					and that fee is above your limit of \(limit).

					Increase this limit in the channel management settings.
					"""
				)
			}
				
		case .overRelativeFee(let canRequestLiquidity, let maxRelativeFeePercent, let swapFeeSats):
			let fee = Utils.formatBitcoin(currencyPrefs, sat: swapFeeSats).string
			let percent = percentToString(maxRelativeFeePercent)
			if canRequestLiquidity {
				Text(
					"""
					A fee of \(fee) is expected to receive this amount, \
					and that fee is more than \(percent) of the amount.

					Increase this limit in the channel management settings, \
					or request additional liquidity.
					"""
				)
			} else {
				Text(
					"""
					A fee of \(fee) is expected to receive this amount, \
					and that fee is more than \(percent) of the amount.

					Increase this limit in the channel management settings.
					"""
				)
			}
				
		case .feeExpected(let swapFeeSats):
			let fee = Utils.formatBitcoin(currencyPrefs, sat: swapFeeSats).string
			Text(
				"""
				An on-chain operation will be likely required for you to receive this amount.

				The fee is estimated to be around \(fee).
				"""
			)
				
		case .unknownFeeExpected:
			Text(
				"""
				An on-chain operation will likely be required for you to receive this amount.
				"""
			)
		}
	}
	
	@ViewBuilder
	func content_button() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer()
			Button {
				navigateToLiquiditySettings()
			} label: {
				switch warning {
				case .liquidityPolicyDisabled:
					Text("Enable automated channels")
				case .overAbsoluteFee(_, _, _):
					Text("Configure fee limit")
				case .overRelativeFee(_, _, _):
					Text("Configure fee limit")
				case .feeExpected(_):
					Text("Configure fee limit")
				case .unknownFeeExpected:
					Text("Configure fee limit")
				}
			} // </Button>
		} // </HStack>
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func percentToString(_ percent: Double) -> String {
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 2
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		smartModalState.close(animationCompletion: {
			deepLinkManager.broadcast(DeepLink.liquiditySettings)
		})
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
	}
}
