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
	static func backupSeed() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			Text("Backup your recovery phrase to prevent losing your funds.")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func electrumServer() -> some View {
			
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.shield")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			Text("Custom electrum server: bad certificate !")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func swapInExpiration() -> some View {
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			Text("A deposit will expire soon.")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func backgroundPayments() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			Text("Background payments disabled.")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func watchTower() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "exclamationmark.triangle")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityLabel("Warning")
			
			Text("Watchtower disabled.")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
	
	@ViewBuilder
	static func mempoolFull() -> some View {
		
		HStack(alignment: VerticalAlignment.top, spacing: 0) {
			Image(systemName: "tray.full")
				.imageScale(.large)
				.padding(.trailing, 10)
				.accessibilityHidden(true)
				.accessibilityLabel("Warning")
			
			Text("Bitcoin mempool is full and fees are high.")
		} // </HStack>
		.accessibilityElement(children: .combine)
		.accessibilityAddTraits(.isButton)
		.accessibilitySortPriority(47)
	}
}
