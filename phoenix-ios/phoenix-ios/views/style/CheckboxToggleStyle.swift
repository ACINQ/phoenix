/// Inspiration for this code came from this wonderful blog:
/// https://swiftuirecipes.com/blog/custom-toggle-checkbox-in-swiftui

import Foundation
import SwiftUI

struct CheckboxToggleStyle<Img1, Img2>: ToggleStyle where Img1: View, Img2: View {
  
	@Environment(\.isEnabled) var isEnabled
	
	let onImage: Img1
	let offImage: Img2
	
	func makeBody(configuration: Configuration) -> some View {
		Button(action: {
				configuration.isOn.toggle()
		}, label: {
			Label {
				configuration.label
			} icon: {
				if configuration.isOn {
					onImage
				} else {
					offImage
				}
			}
		})
		.buttonStyle(PlainButtonStyle()) // remove any implicit styling from the button
		.disabled(!isEnabled)
	}
}
