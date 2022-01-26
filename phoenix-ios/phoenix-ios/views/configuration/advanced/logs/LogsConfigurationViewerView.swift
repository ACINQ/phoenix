import SwiftUI
import PhoenixShared

struct LogsConfigurationViewerView: View {

	let filePath: String

	@State var share: NSURL? = nil
	@State var text: String? = nil

	var body: some View {
		VStack {
			if let text = text {
				ScrollView {
					Text(text.prefix(250000)) // SwiftUI Text seems to have issues displaying very large texts
						.font(.system(.callout, design: .monospaced))
						.frame(maxWidth: .infinity, alignment: .leading)
				}
				.environment(\.layoutDirection, .leftToRight) // issue #237
			} else {
				EmptyView()
			}
		} // </VStack>
		.navigationBarItems(
			trailing: Button {
				share = NSURL(fileURLWithPath: filePath)
			} label: {
				Image(systemName: "square.and.arrow.up")
			}
			.sharing($share)
		)
		.onAppear {
			DispatchQueue.global(qos: .userInitiated).async {
				do {
					let logs = try String(contentsOfFile: filePath)
					DispatchQueue.main.async { text = logs }
				} catch {
					DispatchQueue.main.async { text = "Could not load \(filePath)" }
				}
			}
		}
	}
}

class LogsConfigurationViewerView_Previews: PreviewProvider {
	
	static var previews: some View {
		
		NavigationView {
			LogsConfigurationViewerView(filePath: "fake")
		}
		.previewDevice("iPhone 11")
	}
}
