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
	
	@State var consolidationResult: IosMigrationResult? = nil
	@State var operationInProgress = false
	@State var operationCompleted = false
	@State var operationFailedAtLeastOnce = false
	@State var allowAbortOverride = false // e.g. allows user to go force-close channels
	
	@State var connections: Connections = Biz.business.connectionsManager.currentValue
	@State var channels = Biz.business.peerManager.channelsValue()
	
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
		.onReceive(Biz.business.connectionsManager.publisher()) {
			connectionsChanged($0)
		}
		.onReceive(Biz.business.peerManager.channelsPublisher()) {
			channelsChanged($0)
		}
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
			
			info_bulletRows()
				.padding(.bottom, 40)
			
			Text("Learn more from our [blog post](https://acinq.co/blog/phoenix-splicing-update).")
		}
	}
	
	@ViewBuilder
	func info_bulletRows() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 8) {
			
			info_bulletRow(
				title: "Splicing",
				subtitle: "3rd generation lightning tech"
			)
			info_bulletRow(
				title: "Trustless swaps",
				subtitle: "on-chain <-> lightning"
			)
			info_bulletRow(
				title: "Control on-chain fees",
				subtitle: "select miner fee when sending on-chain"
			)
			info_bulletRow(
				title: "Bump fess",
				subtitle: "to speed up on-chain payments"
			)
			info_bulletRow(
				title: "Better predictability",
				subtitle: "know when fees may occur"
			)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func info_bulletRow(title: LocalizedStringKey, subtitle: LocalizedStringKey) -> some View {
		
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
			
			footer_operationStatus()
				.padding(.bottom, 16)
			footer_buttonsRow()
				.padding(.bottom, 16)
			footer_connectionStatus()
			
		} // </VStack>
	}
	
	@ViewBuilder
	func footer_operationStatus() -> some View {
		
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
			
		} else if let _ = consolidationResult as? IosMigrationResult.Failure {

			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "wrench.adjustable")
				
				if let _ = consolidationResult as? IosMigrationResult.FailureChannelsBeingCreated {
					Text("Failed: Existing channels found in mid-creation state")
				} else if let _ = consolidationResult as? IosMigrationResult.FailureInvalidClosingScript {
					Text("Failed: Invalid closing script")
				} else if let failure = consolidationResult as? IosMigrationResult.FailureGeneric {
					Text("Failure: \(failure.error.message ?? "Generic error")")
				} else {
					Text("Failure: Unknown error")
				}
			}
			.foregroundColor(.appNegative)

		} else {
			
			Text("Upgrade your channels to get started now.")
		}
	}
	
	@ViewBuilder
	func footer_buttonsRow() -> some View {
		
		Group {
			if operationFailedAtLeastOnce {
				footer_subsequentButtonsRow()
			} else {
				footer_initialButtonsRow()
			}
		}
	}
	
	@ViewBuilder
	func footer_initialButtonsRow() -> some View {
		
		if initialButtonsRow_truncated_headline {

			footer_initialButtonsRow(buttonFont: .callout, lineLimit: nil)

		} else if initialButtonsRow_truncated_title3 {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_initialButtonsRow(buttonFont: .headline, lineLimit: 1)
			} wasTruncated: {
				initialButtonsRow_truncated_headline = true
			}

		} else {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_initialButtonsRow(buttonFont: .title3, lineLimit: 1)
			} wasTruncated: {
				initialButtonsRow_truncated_title3 = true
			}
		}
	}
	
	@ViewBuilder
	func footer_initialButtonsRow(buttonFont: Font, lineLimit: Int?) -> some View {
		
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
			.disabled(startRetryButtonDisabled())
			
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
				.disabled(skipAbortButtonDisabled())
			}
			
			Spacer(minLength: 0)
		} // </HStack>
	}
	
	@ViewBuilder
	func footer_subsequentButtonsRow() -> some View {
		
		if subsequentButtonsRow_truncated_headline {

			footer_subsequentButtonsRow(buttonFont: .callout, lineLimit: nil)

		} else if subsequentButtonsRow_truncated_title3 {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_subsequentButtonsRow(buttonFont: .headline, lineLimit: 1)
			} wasTruncated: {
				subsequentButtonsRow_truncated_headline = true
			}

		} else {

			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				footer_subsequentButtonsRow(buttonFont: .title3, lineLimit: 1)
			} wasTruncated: {
				subsequentButtonsRow_truncated_title3 = true
			}
		}
	}
	
	@ViewBuilder
	func footer_subsequentButtonsRow(buttonFont: Font, lineLimit: Int?) -> some View {
		
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
			.disabled(startRetryButtonDisabled())
			
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
			.disabled(skipAbortButtonDisabled())
			
			Spacer(minLength: 0)
		} // </HStack>
	}
	
	@ViewBuilder
	func footer_connectionStatus() -> some View {
		
		if let pendingStatus = notReadyString() {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				Text(verbatim: pendingStatus)
				
			} // </HStack>
			
		} else {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.clear))
				
				Text(verbatim: "")
				
			} // </HStack>
			.accessibilityHidden(true)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func startRetryButtonDisabled() -> Bool {
		
		return operationInProgress || operationCompleted || pendingConnectionsOrChannels()
	}
	
	func skipAbortButtonDisabled() -> Bool {
		
		return operationInProgress || operationCompleted
	}
	
	func pendingConnectionsOrChannels() -> Bool {
		
		return notReadyString() != nil
	}
	
	func notReadyString() -> String? {
		
		if !(connections.internet is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("waiting for internet", comment: "")
		}
		if !(connections.peer is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("connecting to peer", comment: "")
		}
		if !(connections.electrum is Lightning_kmpConnection.ESTABLISHED) {
			return NSLocalizedString("connecting to electrum", comment: "")
		}
		
		if !operationInProgress {
			let allChannelsReady = channels.allSatisfy { $0.isTerminated || $0.isUsable }
			if !allChannelsReady {
				return NSLocalizedString("restoring connections", comment: "")
			}
		}
		
		return nil
	}
	
	// --------------------------------------------------
	// MARK: Notifications
	// --------------------------------------------------
	
	func connectionsChanged(_ newConnections: Connections) {
		log.trace("connectionsChanged()")
		
		connections = newConnections
	}
	
	func channelsChanged(_ newChannels: [LocalChannelInfo]) {
		log.trace("channelsChanged()")
		
		channels = newChannels
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
	
	// --------------------------------------------------
	// MARK: Tasks
	// --------------------------------------------------

	func startConsolidationOperation() {
		log.trace("startConsolidationOperation()")
		
		guard !operationInProgress else {
			return
		}
		
		let _biz = Biz.business
		
		operationInProgress = true
		Task { @MainActor in
			
			do {
				consolidationResult = try await IosMigrationHelper.shared.doMigrateChannels(
					biz: _biz
				)
				operationInProgress = false
				
				if let _ = consolidationResult as? IosMigrationResult.Success {
					log.debug("migrationResult: Success")
					
					operationCompleted = true
					closeView()
					
				} else if let _ = consolidationResult as? IosMigrationResult.FailureNoChannelsAvailable {
					log.debug("migrationResult: FailureNoChannelsAvailable")
					
					// This is treated as success, because it could be that the user has a wallet, but zero channels.
					operationCompleted = true
					closeView()
					
				} else {
					operationFailedAtLeastOnce = true
					
					if let _ = consolidationResult as? IosMigrationResult.FailureChannelsBeingCreated {
						log.debug("migrationResult: FailureChannelsBeingCreated")
						
					} else if let _ = consolidationResult as? IosMigrationResult.FailureInvalidClosingScript {
						log.debug("migrationResult: FailureInvalidClosingScript")
						
					} else if let _ = consolidationResult as? IosMigrationResult.FailureGeneric {
						log.debug("migrationResult: FailureGeneric")
					}
				}
				
			} catch {
				log.error("Error in doMigrateChannels: \(error)")
				
				consolidationResult = IosMigrationResult.FailureGeneric(
					error: KotlinThrowable(message: "Exception thrown")
				)
				operationInProgress = false
				operationFailedAtLeastOnce = true
			}
		}
	}
}
