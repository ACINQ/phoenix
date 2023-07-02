import SwiftUI
import Combine
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "NotificationCell"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


class NotificationCell {
	
	@ViewBuilder
	static func backupSeed(action: (() -> Void)?) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				Text("Backup your recovery phrase to prevent losing your funds.")
				if let action {
					Button(action: action) {
						Group {
							Text("Let's go ").bold() +
							Text(Image(systemName: "arrowtriangle.forward")).bold()
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					}
					.foregroundColor(.appAccent)
				}
			} // </VStack>
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func electrumServer(action: (() -> Void)?) -> some View {
			
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.shield")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				Text("Custom electrum server: bad certificate !")
				if let action {
					Button(action: action) {
						Group {
							Text("Check it ").bold() +
							Text(Image(systemName: "arrowtriangle.forward")).bold()
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					}
					.foregroundColor(.appAccent)
				}
			} // </VStack>
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func backgroundPayments(action: (() -> Void)?) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				Text("Background payments disabled. ")
				if let action {
					Button(action: action) {
						Group {
							Text("Fix ").bold() +
							Text(Image(systemName: "arrowtriangle.forward")).bold()
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					}
					.foregroundColor(.appAccent)
				}
			} // </VStack>
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func watchTower(action: (() -> Void)?) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				Text("Watchtower disabled. ")
				if let action {
					Button(action: action) {
						Group {
							Text("More info ").bold() +
							Text(Image(systemName: "arrowtriangle.forward")).bold()
						}
						.multilineTextAlignment(.leading)
						.allowsTightening(true)
					}
					.foregroundColor(.appAccent)
				}
			} // </VStack>
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func mempoolFull(action: (() -> Void)?) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "tray.full")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 5) {
				Text("Bitcoin mempool is full and fees are high.")
				if let action {
					Button(action: action) {
						Text("See how Phoenix is affected").bold()
					}
					.foregroundColor(.appAccent)
				}
			} // </VStack>
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
}

struct BizNotificationCell: View {
	
	let action: (() -> Void)?
	let item: PhoenixShared.NotificationsManager.NotificationItem
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		
		if let reason = item.notification as? PhoenixShared.Notification.PaymentRejected {
			
			if let reason = reason as? PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee {
				body_overAbsoluteFee(reason)
			} else {
				body_paymentRejected(reason)
			}
			
		} else if let reason = item.notification as? PhoenixShared.WatchTowerOutcome {
			
			if let reason = reason as? PhoenixShared.WatchTowerOutcome.Nominal {
				body_watchTowerSuccess(reason)
				
			} else if let reason = reason as? PhoenixShared.WatchTowerOutcome.RevokedFound {
				body_watchTowerRevoked(reason)
				
			} else if let reason = reason as? PhoenixShared.WatchTowerOutcome.Unknown {
				body_watchTowerUnknown(reason)
			}
			
		} else {
			EmptyView()
		}
	}
	
	@ViewBuilder
	func body_overAbsoluteFee(
		_ reason: PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			let amt = Utils.formatBitcoin(currencyPrefs, msat: reason.amount)
			
			if reason.source == Lightning_kmpLiquidityEventsSource.onchainwallet {
				Text("On-chain funds pending (+\(amt.string))")
					.font(.headline)
			} else {
				Text("Payment rejected (+\(amt.string))")
					.font(.headline)
			}
			
			let expectedFee = Utils.formatBitcoin(currencyPrefs, msat: reason.fee)
			let maxAllowedFee = Utils.formatBitcoin(currencyPrefs, sat: reason.maxAbsoluteFee)
			Text("The fee was \(expectedFee.string) but your max fee was set to \(maxAllowedFee.string).")
				.font(.callout)
				.fixedSize(horizontal: false, vertical: true)
				.padding(.top, 10)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
				if action != nil {
					Button {
						navigateToLiquiditySettings()
					} label: {
						Text("Check fee settings")
							.font(.callout)
					}
				}
				Spacer(minLength: 0)
				Text(timestamp())
					.font(.caption)
					.foregroundColor(.secondary)
			} // </HStack>
			.padding(.top, action != nil ? 15 : 5)
			
		} // </VStac>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_paymentRejected(
		_ reason: PhoenixShared.Notification.PaymentRejected
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			let amt = Utils.formatBitcoin(currencyPrefs, msat: reason.amount)
			Text("Payment rejected (+\(amt.string))")
				.font(.headline)
			
			Group {
				if reason is PhoenixShared.Notification.PaymentRejected.FeePolicyDisabled {
					Text("Automated incoming liquidity is disabled in your incoming fee settings.")
					
				} else if reason is PhoenixShared.Notification.PaymentRejected.ChannelsInitializing {
					Text("Channels initializing...")
					
				} else {
					Text("Unknown reason.")
				}
			}
			.padding(.top, 10)
			
			HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
				if action != nil {
					Button {
						navigateToLiquiditySettings()
					} label: {
						Text("Check fee settings")
							.font(.callout)
					}
				}
				Spacer(minLength: 0)
				Text(timestamp())
					.font(.caption)
					.foregroundColor(.secondary)
			} // </HStack>
			.padding(.top, action != nil ? 15 : 5)
			
		} // </VStac>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_watchTowerSuccess(
		_ reason: PhoenixShared.WatchTowerOutcome.Nominal
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			Text("Watchtower report")
				.font(.headline)
			
			if reason.channelsWatchedCount == 1 {
				Text("1 channel was successfully checked. No issues were found.")
					.font(.callout)
			} else {
				Text("\(reason.channelsWatchedCount) channels were successfully checked. No issues were found.")
					.font(.callout)
			}
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_watchTowerRevoked(
		_ reason: PhoenixShared.WatchTowerOutcome.RevokedFound
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			Text("Watchtower alert")
				.font(.headline)
			
			Text("Revoked commits found on \(reason.channels.count) channel(s)!")
				.font(.callout)
			
			Text("Contact support if needed.")
				.font(.callout)
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_watchTowerUnknown(
		_ reason: PhoenixShared.WatchTowerOutcome.Unknown
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			Text("Watchtower unable to complete")
				.font(.headline)
			
			Text("A new attempt is scheduled in a few hours.")
				.font(.callout)
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	func timestamp() -> String {
		
		let date = item.notification.createdAt.toDate(from: .milliseconds)
		let now = Date.now
		
		if now.timeIntervalSince(date) < 1.0 {
			// The RelativeDateTimeFormatter will say something like "in 0 seconds",
			// which is wrong in this context.
			
			return NSLocalizedString("just now", comment: "Timestamp for notification")
			
		} else {
			
			let formatter = RelativeDateTimeFormatter()
			return formatter.localizedString(for: date, relativeTo: now)
		}
	}
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		if let action {
			action()
		}
		deepLinkManager.broadcast(.liquiditySettings)
	}
}
