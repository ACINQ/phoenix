import Foundation
import SwiftUI


struct GlobalEnvironment: ViewModifier {
	
	static var deviceInfo = DeviceInfo()
	static var deepLinkManager = DeepLinkManager()
	
	let popoverState: PopoverState
	let shortSheetState: ShortSheetState
	let smartModalState: SmartModalState
	
	private static var instance_main: GlobalEnvironment? = nil
	private static var instance_error: GlobalEnvironment? = nil
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
	
	static func errorInstance() -> GlobalEnvironment {
		if instance_error == nil {
			instance_error = GlobalEnvironment(
				popoverState: PopoverState(),
				shortSheetState: ShortSheetState()
			)
		}
		return instance_error!
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
		deepLinkManager = DeepLinkManager()
		instance_main = nil
		instance_error = nil
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
			.environmentObject(Self.deepLinkManager)
			.environmentObject(self.popoverState)
			.environmentObject(self.shortSheetState)
			.environmentObject(self.smartModalState)
	}
}
