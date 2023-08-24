import SwiftUI
import PhoenixShared
import EffectsLibrary
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MergeChannelsView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum MergeChannelsViewType {
	case standalone
	case sheet
}

struct MergeChannelsView: View {
	
	let type: MergeChannelsViewType
	
	@State var consolidationResult: ChannelsConsolidationResult? = nil
	@State var operationInProgress = false
	@State var operationCompleted = false
	@State var operationFailedAtLeastOnce = false
	@State var ignoreDust = false
	@State var allowAbortOverride = false // e.g. allows user to go force-close channels
	
	@State var initialButtonsRow_truncated_title3 = false
	@State var initialButtonsRow_truncated_headline = false
	
	@State var subsequentButtonsRow_truncated_title3 = false
	@State var subsequentButtonsRow_truncated_headline = false
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject private var deviceInfo: DeviceInfo
	
	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			FireworksView(config: FireworksConfig(intensity: Intensity.low, lifetime: Lifetime.long))
			content()
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			header()
				.padding(.horizontal)
				.padding(.top, 40)
				.padding(.bottom, 40)
				
			info()
				.padding(.horizontal, 40) // more inset
			
			Spacer()
			
			footer()
				.padding(.horizontal)
				.padding(.bottom, 40)
			
		} // </VStack>
		.frame(maxWidth: deviceInfo.textColumnMaxWidth)
	}
	
	@ViewBuilder
	func header() -> some View {
		
		Text("Phoenix v2 has arrived!")
			.font(.system(.largeTitle, design: .rounded))
			.allowsTightening(true)
			.foregroundColor(.appAccent)
			.onLongPressGesture(minimumDuration: 5) {
				didLongPressTitle()
			}
	}
	
	@ViewBuilder
	func info() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("What's new:")
				.foregroundColor(.secondary)
				.padding(.bottom, 20)
			
			bulletRows()
				.padding(.bottom, 40)
			
			Text("Learn more from our [blog post](https://acinq.co/blog/phoenix-splicing-update).")
		}
	}
	
	@ViewBuilder
	func bulletRows() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			bulletRow(
				title: "Splicing",
				subtitle: "3rd generation lightning tech"
			)
			bulletRow(
				title: "Trustless swaps",
				subtitle: "on-chain <-> lightning"
			)
			bulletRow(
				title: "Control on-chain fees",
				subtitle: "select miner fee when sending on-chain"
			)
			bulletRow(
				title: "Bump fess",
				subtitle: "to speed up on-chain payments"
			)
			bulletRow(
				title: "Better predictability",
				subtitle: "know when fees may occur"
			)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func bulletRow(title: LocalizedStringKey, subtitle: LocalizedStringKey) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "circle.fill")
					.imageScale(.small)
					.font(.caption2)
					.foregroundColor(.secondary)
				Text(title)
					.font(.headline)
			}
			.padding(.bottom, 4)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
				Image(systemName: "circle.fill")
					.imageScale(.small)
					.font(.caption2)
					.foregroundColor(.clear)
				Text(subtitle)
					.font(.subheadline)
					.foregroundColor(.secondary)
			}
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			if operationInProgress {
				
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
					Text("Upgrading channels...")
					
				} // </HStack>
				
			} else if operationCompleted {
					
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "checkmark.circle")
					Text("Upgraded channels!")
					
				} // </HStack>
				.foregroundColor(.appPositive)
				
			} else if let _ = consolidationResult as? ChannelsConsolidationResult.Failure {
				
				if let failure = consolidationResult as? ChannelsConsolidationResult.FailureDustChannels {
					let (btcAmt, fiatAmt) = dustAmount(failure)
					
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "wrench.adjustable")
						Text("Operation will cost \(btcAmt.string) (≈ \(fiatAmt.string))")
					}
					
				} else {
					
					HStack(alignment: VerticalAlignment.center, spacing: 4) {
						Image(systemName: "wrench.adjustable")
						
						if let _ = consolidationResult as? ChannelsConsolidationResult.FailureChannelsBeingCreated {
							Text("Failed: Existing channels found in mid-creation state")
						} else if let _ = consolidationResult as? ChannelsConsolidationResult.FailureInvalidClosingScript {
							Text("Failed: Invalid closing script")
						} else if let failure = consolidationResult as? ChannelsConsolidationResult.FailureGeneric {
							Text("Failure: \(failure.error.message ?? "Generic error")")
						} else {
							Text("Failure: Unknown error")
						}
					}
					.foregroundColor(.appNegative)
				}
				
			} else {
				
				Text("Upgrade your channels to get started now.")
			}
			
			buttonsRow()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func buttonsRow() -> some View {
		
		Group {
			if operationFailedAtLeastOnce {
				subsequentButtonsRow()
			} else {
				initialButtonsRow()
			}
		}
		.disabled(operationInProgress || operationCompleted)
	}
	
	@ViewBuilder
	func initialButtonsRow() -> some View {
		
		if initialButtonsRow_truncated_headline {

			initialButtonsRow(buttonFont: .callout, lineLimit: nil)

		} else if initialButtonsRow_truncated_title3 {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				initialButtonsRow(buttonFont: .headline, lineLimit: 1)
			} wasTruncated: {
				initialButtonsRow_truncated_headline = true
			}

		} else {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				initialButtonsRow(buttonFont: .title3, lineLimit: 1)
			} wasTruncated: {
				initialButtonsRow_truncated_title3 = true
			}
		}
	}
	
	@ViewBuilder
	func initialButtonsRow(buttonFont: Font, lineLimit: Int?) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			
			Button {
				getStarted()
			} label: {
				Label("Get Started", systemImage: "paperplane")
					.font(buttonFont)
					.lineLimit(lineLimit)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			
			if allowAbortOverride {
				Spacer(minLength: 0)
				
				Button {
					abortOperation()
				} label: {
					Label("Skip For Now", systemImage: "xmark.circle")
						.font(buttonFont)
						.lineLimit(lineLimit)
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.tint(.appNegative)
			}
			
			Spacer(minLength: 0)
		} // </HStack>
		.padding(.top, 16)
	}
	
	@ViewBuilder
	func subsequentButtonsRow() -> some View {
		
		if subsequentButtonsRow_truncated_headline {

			subsequentButtonsRow(buttonFont: .callout, lineLimit: nil)

		} else if subsequentButtonsRow_truncated_title3 {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				subsequentButtonsRow(buttonFont: .headline, lineLimit: 1)
			} wasTruncated: {
				subsequentButtonsRow_truncated_headline = true
			}

		} else {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				subsequentButtonsRow(buttonFont: .title3, lineLimit: 1)
			} wasTruncated: {
				subsequentButtonsRow_truncated_title3 = true
			}
		}
	}
	
	@ViewBuilder
	func subsequentButtonsRow(buttonFont: Font, lineLimit: Int?) -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			
			Button {
				retryOperation()
			} label: {
				Label("Retry", systemImage: "paperplane")
					.font(buttonFont)
					.lineLimit(lineLimit)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			
			Spacer(minLength: 0)
			
			Button {
				abortOperation()
			} label: {
				Label("Abort", systemImage: "xmark.circle")
					.font(buttonFont)
					.lineLimit(lineLimit)
			}
			.buttonStyle(.borderedProminent)
			.buttonBorderShape(.capsule)
			.tint(.appNegative)
			
			Spacer(minLength: 0)
		} // </HStack>
		.padding(.top, 16)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func dustAmount(
		_ failure: ChannelsConsolidationResult.FailureDustChannels
	) -> (FormattedAmount, FormattedAmount) {
		
		let allChannels: [String: LocalChannelInfo] = Biz.business.peerManager.channelsFlowValue().mapKeys { $0.toHex() }
		let dustChannels: [LocalChannelInfo] = failure.dustChannels.compactMap { allChannels[$0] }
		let dustChannelsMsat = dustChannels.map { $0.localBalance?.msat ?? Int64(0) }.sum()
		
		let btc = Utils.formatBitcoin(currencyPrefs, msat: dustChannelsMsat)
		let fiat = Utils.formatFiat(currencyPrefs, msat: dustChannelsMsat)
		return (btc, fiat)
	}
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------

	func startConsolidationOperation() {
		log.trace("startConsolidationOperation()")
		
		guard !operationInProgress else {
			return
		}
		
		let _biz = Biz.business
		let _ignoreDust = ignoreDust
		
		operationInProgress = true
		Task { @MainActor in
			
			do {
				consolidationResult = try await ChannelsConsolidationHelper.shared.consolidateChannels(
					biz: _biz,
					ignoreDust: _ignoreDust
				)
				operationInProgress = false
				
				if let _ = consolidationResult as? ChannelsConsolidationResult.Success {
					log.debug("consolidationResult: Success")
					
					operationCompleted = true
					closeView()
					
				} else if let _ = consolidationResult as? ChannelsConsolidationResult.FailureNoChannelsAvailable {
					log.debug("consolidationResult: FailureNoChannelsAvailable")
					
					// This is treated as success, because it could be that the user has a wallet, but zero channels.
					operationCompleted = true
					closeView()
					
				} else {
					operationFailedAtLeastOnce = true
					
					if let _ = consolidationResult as? ChannelsConsolidationResult.FailureDustChannels {
						log.debug("consolidationResult: FailureDustChannels")
						
						// If the user chooses to continue the operation after this,
						// they're agreeing to send the dust in their channels to the miners.
						ignoreDust = true
						
					} else if let _ = consolidationResult as? ChannelsConsolidationResult.FailureChannelsBeingCreated {
						log.debug("consolidationResult: FailureChannelsBeingCreated")
						
					} else if let _ = consolidationResult as? ChannelsConsolidationResult.FailureInvalidClosingScript {
						log.debug("consolidationResult: FailureInvalidClosingScript")
						
					} else if let _ = consolidationResult as? ChannelsConsolidationResult.FailureGeneric {
						log.debug("consolidationResult: FailureGeneric")
					}
				}
				
			} catch {
				log.error("Error in consolidateChannels: \(error)")
				
				consolidationResult = ChannelsConsolidationResult.FailureGeneric(
					error: KotlinThrowable(message: "Exception thrown")
				)
				operationInProgress = false
				operationFailedAtLeastOnce = true
			}
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func didLongPressTitle() {
		log.trace("didLongPressTitle()")
		
		allowAbortOverride = true
	}
	
	func getStarted() {
		log.trace("getStarted()")
		
		startConsolidationOperation()
	}
	
	func retryOperation() {
		log.trace("retryOperation()")
		
		startConsolidationOperation()
	}
	
	func abortOperation() {
		log.trace("abortOperation()")
		
		closeView()
	}
	
	func closeView() {
		log.trace("closeView()")
		
		Prefs.shared.hasMergedChannelsForSplicing = true
		if type == .sheet {
			presentationMode.wrappedValue.dismiss()
		}
	}
}
