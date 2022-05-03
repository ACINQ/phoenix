import SwiftUI

struct TopTab: View {
	
	let color: Color
	let size: CGSize
	
	var body: some View {
		
		let radius = size.height
		Path { p in
			
			// Left arc
			p.move(to: CGPoint(x: radius, y: radius))
			p.addLine(to: CGPoint(x: 0, y: radius))
			p.addArc(
				center: CGPoint(x: radius, y: radius),
				radius: radius,
				startAngle: Angle.degrees(-180),
				endAngle: Angle.degrees(-90),
				clockwise: false
			)
			p.addLine(to: CGPoint(x: radius, y: radius))

			// Center square
			p.move(to: CGPoint(x: radius, y: 0))
			p.addRect(CGRect(
				x: radius,
				y: 0,
				width: size.width - (radius * 2),
				height: size.height
			))
			
			// Right arc
			p.move(to: CGPoint(x: size.width - radius, y: radius))
			p.addLine(to: CGPoint(x: size.width - radius, y: 0))
			p.addArc(
				center: CGPoint(x: size.width - radius, y: radius),
				radius: radius,
				startAngle: Angle.degrees(-90),
				endAngle: Angle.degrees(0),
				clockwise: false
			)
			p.addLine(to: CGPoint(x: size.width - radius, y: radius))
		}
		.fill(color)
		.frame(width: size.width, height: size.height, alignment: Alignment.center)
	}
}
