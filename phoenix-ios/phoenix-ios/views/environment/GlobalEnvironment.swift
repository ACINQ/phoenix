import Foundation
import SwiftUI


struct GlobalEnvironment: ViewModifier {
	
	static var deviceInfo = DeviceInfo()
	static var currencyPrefs = CurrencyPrefs()
	static var deepLinkManager = DeepLinkManager()
	static var controllerFactory = Biz.business.controllers
	
	let popoverState: PopoverState
	let shortSheetState: ShortSheetState
	let smartModalState: SmartModalState
	
	private static var instance_main: GlobalEnvironment? = nil
	private static var instance_sheet: GlobalEnvironment? = nil
	
	static func mainInstance() -> GlobalEnvironment {
		if instance_main == nil {
			instance_main = GlobalEnvironment(
				popoverState: PopoverState(),
				shortSheetState: ShortSheetState()
			)
		}
		return instance_main!
	}
	
	static func sheetInstance() -> GlobalEnvironment {
		if instance_sheet == nil {
			instance_sheet = GlobalEnvironment(
				popoverState: PopoverState(),
				shortSheetState: ShortSheetState()
			)
		}
		return instance_sheet!
	}
	
	static func reset() {
		deviceInfo = DeviceInfo()
		currencyPrefs = CurrencyPrefs()
		deepLinkManager = DeepLinkManager()
		controllerFactory = Biz.business.controllers
		instance_main = nil
		instance_sheet = nil
	}
	
	private init(
		popoverState: PopoverState,
		shortSheetState: ShortSheetState
	) {
		self.popoverState = popoverState
		self.shortSheetState = shortSheetState
		self.smartModalState = SmartModalState(popoverState: popoverState, shortSheetState: shortSheetState)
	}
	
	func body(content: Self.Content) -> some View {
		content
			.environmentObject(Self.deviceInfo)
			.environmentObject(Self.currencyPrefs)
			.environmentObject(Self.deepLinkManager)
			.environment(\.controllerFactory, Self.controllerFactory)
			.environmentObject(self.popoverState)
			.environmentObject(self.shortSheetState)
			.environmentObject(self.smartModalState)
	}
}
