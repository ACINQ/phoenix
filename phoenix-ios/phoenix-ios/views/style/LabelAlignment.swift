import SwiftUI

struct LabelAlignment<TitleContent: View, IconContent: View>: View {
	
	let titleContent: () -> TitleContent
	let iconContent: () -> IconContent
	
	init(
		title: @escaping () -> TitleContent,
		icon: @escaping () -> IconContent
	) {
		self.titleContent = title
		self.iconContent = icon
	}
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.topLeading) {
			Label {
				Text(verbatim: "Label title").lineLimit(1).hidden()
			} icon: {
				iconContent()
			}
			Label {
				titleContent()
			} icon: {
				iconContent().hidden()
			}
		}
		
	}
}
