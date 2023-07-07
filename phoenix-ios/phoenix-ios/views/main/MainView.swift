import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "MainView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum HeaderButtonHeight: Preference {}

struct MainView: View {
	
	static let idiom = UIDevice.current.userInterfaceIdiom
	
	@ViewBuilder
	var body: some View {
		if MainView.idiom == .pad {
			MainView_Big()
		} else {
			MainView_Small()
		}
	}
}

class MainViewHelper {
	
	public static let shared = MainViewHelper()
	private init() {/* must use shared instance */}
	
	private var temp: [AppScanController] = []
	
	func processExternalLightningUrl(
		_ urlStr: String,
		callback: @escaping (AppScanController) -> Void
	) -> Void {
		
		// We want to:
		// - Parse the incoming lightning url
		// - If it's invalid, we want to display a warning (using the Toast view)
		// - Otherwise we want to jump DIRECTLY to the "Confirm Payment" screen.
		//
		// In particular, we do **NOT** want the user experience to be:
		// - switch to SendView
		// - then again switch to ConfirmView
		// This feels jittery :(
		//
		// So we need to:
		// - get a Scan.ModelValidate instance
		// - pass this to SendView as the `firstModel` parameter
		
		let controllers = Biz.business.controllers
		guard let scanController = controllers.scan(firstModel: Scan.ModelReady()) as? AppScanController else {
			return
		}
		temp.append(scanController)
		
		var unsubscribe: (() -> Void)? = nil
		unsubscribe = scanController.subscribe { (model: Scan.Model) in
			
			// Ignore first subscription fire (Scan.ModelReady)
			if let _ = model as? Scan.ModelReady {
				return
			} else {
				callback(scanController)
			}
			
			// Cleanup
			if let idx = self.temp.firstIndex(where: { $0 === scanController }) {
				self.temp.remove(at: idx)
			}
			unsubscribe?()
		}
		
		scanController.intent(intent: Scan.IntentParse(request: urlStr))
	}
}
