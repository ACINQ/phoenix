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
	
	@ViewBuilder
	static func bizNotification(
		_ notification: PhoenixShared.NotificationsManager.NotificationItem,
		action: (() -> Void)?
	) -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			Text("Todo...")
			
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
}
