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
	
	enum Location {
		case HomeView_Single(preAction: ()->Void)
		case HomeView_Multiple
		case NotificationsView(preAction: ()->Void)
	}
	
	let item: PhoenixShared.NotificationsManager.NotificationItem
	let location: Location
	
	@EnvironmentObject var currencyPrefs: CurrencyPrefs
	@EnvironmentObject var deepLinkManager: DeepLinkManager
	
	@ViewBuilder
	var body: some View {
		if let reason = item.notification as? PhoenixShared.Notification.PaymentRejected {

			if let reason = reason as? PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee {
				body_paymentRejected_overFee(Either.Left(reason))
				
			} else if let reason = reason as? PhoenixShared.Notification.PaymentRejected.OverRelativeFee {
				body_paymentRejected_overFee(Either.Right(reason))
				
			} 	else {
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
	func body_paymentRejected_overFee(
		_ either: Either<
			PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee,
			PhoenixShared.Notification.PaymentRejected.OverRelativeFee
		>
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			// Title
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Group {
					let amt = Utils.formatBitcoin(currencyPrefs, msat: amount(either))
					if isOnChain(either) {
						Text("On-chain funds pending (+\(amt.string))")
					} else {
						Text("Payment rejected (+\(amt.string))")
					}
				}
				.font(.headline)
				
				if isDismissable() {
					Spacer(minLength: 0)
					Button {
						dismiss()
					} label: {
						Image(systemName: "xmark")
					}
				}
			} // </HStack>
			
			switch either {
			case .Left(let reason):
				
				let actualFee = Utils.formatBitcoin(currencyPrefs, msat: reason.fee)
				let maxAllowedFee = Utils.formatBitcoin(currencyPrefs, sat: reason.maxAbsoluteFee)
				
				Text("The fee was \(actualFee.string) but your max fee was set to \(maxAllowedFee.string).")
					.font(.callout)
					.fixedSize(horizontal: false, vertical: true)
					.padding(.top, 10)
				
			case .Right(let reason):
				
				let actualFee = Utils.formatBitcoin(currencyPrefs, msat: reason.fee)
				let percent = basisPointsAsPercent(reason.maxRelativeFeeBasisPoints)
				
				Text("The fee was \(actualFee.string) which is more than \(percent) of the amount.")
					.font(.callout)
					.fixedSize(horizontal: false, vertical: true)
					.padding(.top, 10)
				
			} // </switch>
			
			body_paymentRejected_footer()
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_paymentRejected(
		_ reason: PhoenixShared.Notification.PaymentRejected
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			// Title
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				let amt = Utils.formatBitcoin(currencyPrefs, msat: reason.amount)
				Text("Payment rejected (+\(amt.string))")
					.font(.headline)
				
				if isDismissable() {
					Spacer(minLength: 0)
					Button {
						dismiss()
					} label: {
						Image(systemName: "xmark")
					}
				}
			} // </HStack>
			
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
			
			body_paymentRejected_footer()
			
		} // </VStac>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	func body_paymentRejected_footer() -> some View {
		
		let showAction = shouldShowAction()
		HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
			if showAction {
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
		.padding(.top, showAction ? 15 : 5)
	}
	
	@ViewBuilder
	func body_watchTowerSuccess(
		_ reason: PhoenixShared.WatchTowerOutcome.Nominal
	) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 10) {
			
			// Title
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Watchtower report")
					.font(.headline)
				
				if isDismissable() {
					Spacer(minLength: 0)
					Button {
						dismiss()
					} label: {
						Image(systemName: "xmark")
					}
				}
				
			} // </HStack>
			
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
			
			// Title
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Watchtower alert")
					.font(.headline)
				
				if isDismissable() {
					Spacer(minLength: 0)
					Button {
						dismiss()
					} label: {
						Image(systemName: "xmark")
					}
				}
			} // </HStack>
			
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
			
			// Title
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Text("Watchtower unable to complete")
					.font(.headline)
				
				if isDismissable() {
					Spacer(minLength: 0)
					Button {
						dismiss()
					} label: {
						Image(systemName: "xmark")
					}
				}
			} // </HStack>
			
			Text("A new attempt is scheduled in a few hours.")
				.font(.callout)
			
		} // </VStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	func isDismissable() -> Bool {
		
		switch location {
			case .HomeView_Single   : return true
			case .HomeView_Multiple : return false
			case .NotificationsView : return false
		}
	}
	
	func shouldShowAction() -> Bool {
		
		switch location {
			case .HomeView_Single   : return true
			case .HomeView_Multiple : return false
			case .NotificationsView : return true
		}
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
	
	func amount(_ either: Either<
		PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee,
		PhoenixShared.Notification.PaymentRejected.OverRelativeFee
	>) -> Lightning_kmpMilliSatoshi {
	
		switch either {
			case .Left(let reason) : return reason.amount
			case .Right(let reason): return reason.amount
		}
	}
	
	func isOnChain(_ either: Either<
		PhoenixShared.Notification.PaymentRejected.OverAbsoluteFee,
		PhoenixShared.Notification.PaymentRejected.OverRelativeFee
	>) -> Bool {
		
		let source: Lightning_kmpLiquidityEventsSource
		switch either {
			case .Left(let reason) : source = reason.source
			case .Right(let reason): source = reason.source
		}
		
		return source == Lightning_kmpLiquidityEventsSource.onchainwallet
	}
	
	func basisPointsAsPercent(_ basisPoints: Int32) -> String {
		
		// Example: 30% == 3,000 basis points
		//
		// 3,000 / 100       => 30.0 => 3000%
		// 3,000 / 100 / 100 =>  0.3 => 30%
		
		let percent = Double(basisPoints) / Double(10_000)
		
		let formatter = NumberFormatter()
		formatter.numberStyle = .percent
		formatter.minimumFractionDigits = 0
		formatter.maximumFractionDigits = 2
		
		return formatter.string(from: NSNumber(value: percent)) ?? "?%"
	}
	
	func navigateToLiquiditySettings() {
		log.trace("navigateToLiquiditySettings()")
		
		switch location {
			case .HomeView_Single(let preAction)   : preAction()
			case .HomeView_Multiple                : break
			case .NotificationsView(let preAction) : preAction()
		}
		deepLinkManager.broadcast(.liquiditySettings)
	}
		
	func dismiss() {
		log.trace("dismiss()")
		
		Biz.business.notificationsManager.dismissNotifications(ids: item.ids)
	}
}
