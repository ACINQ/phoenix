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
			
			if let reason = reason as? PhoenixShared.Notification.PaymentRejected.FeeTooExpensive {
				body_feeTooExpensive(reason)
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
	func body_feeTooExpensive(
		_ reason: PhoenixShared.Notification.PaymentRejected.FeeTooExpensive
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			let amt = Utils.formatBitcoin(currencyPrefs, msat: reason.amount)
			Text("On-chain funds pending (+\(amt.string))")
				.font(.headline)
			
			let expectedFee = Utils.formatBitcoin(currencyPrefs, msat: reason.expectedFee)
			let maxAllowedFee = Utils.formatBitcoin(currencyPrefs, msat: reason.maxAllowedFee)
			Text("The fee was \(expectedFee.string) but your max fee was set to \(maxAllowedFee.string)")
				.font(.callout)
				.fixedSize(horizontal: false, vertical: true)
				.padding(.top, 10)
				.padding(.bottom, 15)
			
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
			
			if reason is PhoenixShared.Notification.PaymentRejected.FeePolicyDisabled {
				Text("Automated incoming liquidity is disabled in your incoming fee settings.")
				
			} else if reason is PhoenixShared.Notification.PaymentRejected.ChannelsInitializing {
				Text("Channels initializing...")
				
			} else if reason is PhoenixShared.Notification.PaymentRejected.RejectedManually {
				Text("Manually rejected paymented.")
				
			} else {
				Text("Unknown reason.")
				
			}
			
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
		} // </VStac>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_watchTowerSuccess(
		_ reason: PhoenixShared.WatchTowerOutcome.Nominal
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			// Todo: What should this actually say ?
			Text("Watchtower success")
				.font(.headline)
			
			Text("Your funds are safe.")
				.font(.callout)
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_watchTowerRevoked(
		_ reason: PhoenixShared.WatchTowerOutcome.RevokedFound
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			// Todo: What should this actually say ?
			Text("Watchtower: revoked commit found")
				.font(.headline)
			
			Text("Your funds are being rescued...")
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			// Todo: What should this actually say ?
			Text("Watchtower: process failed")
				.font(.headline)
			
			Text("Will retry again soon...")
				.font(.callout)
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	func timestamp() -> String {
		
		let date = item.notification.createdAt.toDate(from: .milliseconds)
		
		let formatter = RelativeDateTimeFormatter()
		return formatter.localizedString(for: date, relativeTo: Date.now)
	}
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		if let action {
			action()
		}
		deepLinkManager.broadcast(.liquiditySettings)
	}
}
