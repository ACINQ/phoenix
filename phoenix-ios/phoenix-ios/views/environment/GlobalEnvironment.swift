import Foundation
import SwiftUI


struct GlobalEnvironment: ViewModifier {
	
	static var deviceInfo = DeviceInfo()
	static var currencyPrefs = CurrencyPrefs()
	static var deepLinkManager = DeepLinkManager()
	static var controllerFactory = Biz.business.controllers
	
	static func reset() {
		deviceInfo = DeviceInfo()
		currencyPrefs = CurrencyPrefs()
		deepLinkManager = DeepLinkManager()
		controllerFactory = Biz.business.controllers
	}
	
	func body(content: Self.Content) -> some View {
		content
			.environmentObject(Self.deviceInfo)
			.environmentObject(Self.currencyPrefs)
			.environmentObject(Self.deepLinkManager)
			.environment(\.controllerFactory, Self.controllerFactory)
	}
}
