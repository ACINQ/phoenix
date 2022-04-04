import SwiftUI

struct ChevronShape: Shape {
	let width: CGFloat
	let height: CGFloat
	var offset: CGFloat
	
	init(width: CGFloat, height: CGFloat, isPointingUp: Bool) {
		self.width = width
		self.height = height
		self.offset = isPointingUp ? 0 : height
	}
	
	var animatableData: CGFloat {
		get { offset }
		set { offset = newValue }
	}
	
	func path(in rect: CGRect) -> Path {
		Path { path in
			path.move(to: CGPoint(x: 0, y: height/2))
			path.addLine(to: CGPoint(x: width/2, y: offset))
			path.move(to: CGPoint(x: width/2, y: offset))
			path.addLine(to: CGPoint(x: width, y: height/2))
		}
	}
}

struct AnimatedChevron: View {
	
	enum Position {
		case pointingUp
		case pointingDown
		
		mutating func toggle() {
			switch self {
				case .pointingUp   : self = .pointingDown
				case .pointingDown : self = .pointingUp
			}
		}
	}
	
	@Binding var position: Position
	
	let color: Color
	let lineWidth: CGFloat
	let lineThickness: CGFloat
	let verticalOffset: CGFloat
	
	var isPointingUp: Bool {
		return position == .pointingUp
	}
	
	var isPointingDown: Bool {
		return position == .pointingDown
	}
	
	var shapeSize: CGSize {
		return CGSize(
			width: lineWidth,
			height: verticalOffset * 2
		)
	}
	
	var viewSize: CGSize {
		return CGSize(
			width: lineWidth + lineThickness,
			height: (verticalOffset * 2) + lineThickness
		)
	}
	
	var body: some View {
		
		ChevronShape(
			width: shapeSize.width,
			height: shapeSize.height,
			isPointingUp: isPointingUp
		)
		.stroke(style: StrokeStyle(lineWidth: lineThickness, lineCap: .round))
		.foregroundColor(color)
		.frame(width: shapeSize.width, height: shapeSize.height, alignment: Alignment.center)
		.frame(width: viewSize.width, height: viewSize.height, alignment: Alignment.center)
	}
}
