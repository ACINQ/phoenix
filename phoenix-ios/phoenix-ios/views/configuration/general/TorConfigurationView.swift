import SwiftUI

struct TorConfigurationView: View {

    @State var isTorEnabled = Prefs.shared.isTorEnabled
    @State var theme = Prefs.shared.theme

    var body: some View {
        Form {
            Section(header: TorFormHeader(), content: {}).textCase(nil)

            Toggle(isOn: $isTorEnabled.animation()) {
                if isTorEnabled {
                    Text("Tor is enabled")
                } else {
                    Text("Tor is disabled")
                }
            }.onChange(of: isTorEnabled) { newValue in
                self.toggleTor(newValue)
            }
        }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .edgesIgnoringSafeArea(.bottom)
                .navigationBarTitle("Tor Settings", displayMode: .inline)
    }

	struct TorFormHeader: View {
		
		var body: some View {
			Text(
				"""
				You can improve your privacy by only using Tor when connecting to an Electrum server or \
				to your Lightning peer. This will slightly slow down your transactions.
				"""
			)
			.font(.body)
			.foregroundColor(Color.primary)
			.padding(.top, 10)
		}
	}

	func toggleTor(_ isEnabled: Bool) {
		Prefs.shared.isTorEnabled = isEnabled
	}
}

class TorConfigurationView_Previews: PreviewProvider {

    static var previews: some View {
        TorConfigurationView()
                .previewDevice("iPhone 11")
    }
}
