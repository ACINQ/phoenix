import SwiftUI
import PhoenixShared

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
			
			Text("Here you can extract and visualize application logs, as well as share them.")
				.foregroundColor(Color.primary)
				.frame(maxWidth: .infinity)
				.padding()
				.background(Color.primaryBackground)
			
			List {
				if let model = mvi.model as? LogsConfiguration.ModelReady {
					Button {
						share = NSURL(fileURLWithPath: model.path)
					} label: {
						Label {
							Text("Share the logs")
						} icon: {
							Image(systemName: "square.and.arrow.up")
						}
					}
					.sharing($share)
					
					NavigationLink(destination: LogsConfigurationViewerView(filePath: model.path)) {
						Label {
							Text("Look at the logs")
						} icon: {
							Image(systemName: "eye")
						}
					}
				}
			} // </List>
			.listStyle(.insetGrouped)
			.listBackgroundColor(.primaryBackground)
			
		} // </VStack>
	}
}

class LogsConfigurationView_Previews: PreviewProvider {

	static var previews: some View {
		
		NavigationView {
			LogsConfigurationView().mock(LogsConfiguration.ModelLoading())
		}
		.previewDevice("iPhone 11")
		
		NavigationView {
			LogsConfigurationView().mock(LogsConfiguration.ModelReady(
				path: "/tmp/fake-logs-file"
			))
		}
		.previewDevice("iPhone 11")
	}
}
