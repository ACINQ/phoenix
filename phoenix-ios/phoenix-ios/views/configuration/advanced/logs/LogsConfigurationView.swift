import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LogsConfigurationView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct LogsConfigurationView: MVIView {
	
	@StateObject var mvi = MVIState({ $0.logsConfiguration() })
	
	@Environment(\.controllerFactory) var factoryEnv
	var factory: ControllerFactory { return factoryEnv }

	@State var share: NSURL? = nil

	@ViewBuilder
	var view: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Logs", comment: "Navigation bar title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Text("Here you can export the application logs, or share them.")
				.foregroundColor(Color.primary)
				.frame(maxWidth: .infinity)
				.padding()
				.background(Color.primaryBackground)
			
			List {
				Button {
					export()
				} label: {
					Label {
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Text("Share the logs")
							if isExporting() {
								Spacer()
								ProgressView().progressViewStyle(CircularProgressViewStyle())
							}
						}
					} icon: {
						Image(systemName: "square.and.arrow.up")
					}
				}
				.disabled(isExporting())
				.sharing($share)
				
			} // </List>
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
			
		} // </VStack>
		.onChange(of: mvi.model) { newModel in
			mviModelDidChange(model: newModel)
		}
	}
	
	private func isExporting() -> Bool {
		return mvi.model is LogsConfiguration.Model_Exporting
	}
	
	func mviModelDidChange(model newModel: LogsConfiguration.Model) {
		log.trace("mviModelDidChange()")
		
		if let model = newModel as? LogsConfiguration.Model_Ready {
			share = NSURL(fileURLWithPath: model.path)
		}
	}
	
	private func export() {
		log.trace("export()")
		
		mvi.intent(LogsConfiguration.Intent_Export())
	}
}
