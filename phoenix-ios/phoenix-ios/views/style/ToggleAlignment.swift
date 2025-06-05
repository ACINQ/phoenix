import SwiftUI

struct ToggleAlignment<DescriptionContent: View, ToggleContent: View>: View {
	
	let descriptionContent: () -> DescriptionContent
	let toggleContent: () -> ToggleContent
	
	init(
		description: @escaping () -> DescriptionContent,
		toggle: @escaping () -> ToggleContent
	) {
		self.descriptionContent = description
		self.toggleContent = toggle
	}

	@ViewBuilder
	var body: some View {
		
		HStack(alignment: VerticalAlignment.centerTopLine) { // <- Custom VerticalAlignment
			
			ZStack(alignment: Alignment.topLeading) {
				Text(verbatim: "Toggle alignment")
					.lineLimit(1)
					.hidden()
					.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
						d[VerticalAlignment.center]
					}
				
				descriptionContent()
			}
			
			Spacer()
			
			toggleContent()
				.padding(.trailing, 2)
				.alignmentGuide(VerticalAlignment.centerTopLine) { (d: ViewDimensions) in
					d[VerticalAlignment.center]
				}
			
		} // </HStack>
	}
}
