import SwiftUI

struct ComingSoonView: View {
	
	let title: String
	
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.center) {
			
			Text("Coming Soon")
				.padding(.top, 40)
			
			Spacer()
		}
		.navigationBarTitle(title)
	}
}
