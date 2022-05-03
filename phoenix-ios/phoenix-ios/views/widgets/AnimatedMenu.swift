/**
 * Class inspired by: Amos Gyamfi
 * https://github.com/amosgyamfi
 */

import SwiftUI

struct AnimatedMenu: View {
	
	enum Icon {
		case menuIcon
		case closeIcon
	}
	
	@Binding var icon: Icon
	
	private let color: Color
	private let lineWidth: CGFloat
	private let lineHeight: CGFloat
	private let lineSpacing: CGFloat
	
	init(icon: Binding<Icon>, color: Color, lineWidth: CGFloat, lineHeight: CGFloat, lineSpacing: CGFloat) {
		self._icon = icon
		self.color = color
		self.lineWidth = lineWidth
		self.lineHeight = lineHeight
		self.lineSpacing = lineSpacing
	}
	
	var isMenuIcon: Bool {
		return icon == .menuIcon
	}
	
	var isCloseIcon: Bool {
		return icon == .closeIcon
	}
	
	var body: some View {
		VStack(alignment: .center, spacing: lineSpacing){
				
			Rectangle() // top
				.fill(color)
				.frame(width: isCloseIcon ? diagonalLength : lineWidth, height: lineHeight)
				.cornerRadius(4)
				.rotationEffect(isCloseIcon ? diagonalAngle : .zero, anchor: .topLeading)
				.offset(x: isCloseIcon ? closeIconRect.origin.x : 0)
				.offset(x: isCloseIcon ? xOffset : 0, y: isCloseIcon ? -yOffset : 0)
				
			Rectangle() // middle
				.fill(color)
				.frame(width: lineWidth, height: lineHeight)
				.cornerRadius(4)
				.scaleEffect(isCloseIcon ? 0 : 1)
				.opacity(isCloseIcon ? 0 : 1)
			
			Rectangle() // bottom
				.fill(color)
				.frame(width: isCloseIcon ? diagonalLength : lineWidth, height: lineHeight)
				.cornerRadius(4)
				.rotationEffect(isCloseIcon ? diagonalAngle.negative : .zero, anchor: .bottomLeading)
				.offset(x: isCloseIcon ? closeIconRect.origin.x : 0)
				.offset(x: isCloseIcon ? xOffset : 0, y: isCloseIcon ? yOffset : 0)
		}
		.frame(width: lineWidth, height: menuIconSize.height, alignment: Alignment.leading)
	}
	
	var menuIconSize: CGSize {
		
		return CGSize(
			width: lineWidth,
			height: (lineHeight * 3) + (lineSpacing * 2)
		)
	}
	
	var closeIconRect: CGRect {
		
		let menuIconSize = menuIconSize
		if menuIconSize.width > menuIconSize.height {
			
			// If the menuIcon has longer width than height,
			// then we create a square within it for the closeIcon.
			
			let squareSize = menuIconSize.height
			let excessWidth = menuIconSize.width - squareSize
			
			return CGRect(x: (excessWidth / 2), y: 0, width: squareSize, height: squareSize)
			
		} else {
			
			return CGRect(origin: CGPoint(x: 0, y: 0), size: menuIconSize)
		}
	}
	
	var diagonalLength: CGFloat {
		
		//  |\
		//  | \
		// a|  \c <- this length
		//  |___\
		//    b
		//
		// a^2 + b^2 = c^2
		
		let closeIconRect = closeIconRect
		let width = closeIconRect.size.width
		let height = closeIconRect.size.height
		return sqrt(pow(width, 2) + pow(height, 2))
	}
	
	var diagonalAngle: Angle {
		
		//  |\ <-- this angle = ß
		//  | \
		// a|  \c
		//  |___\
		//    b
		//
		// ß = asin(a/c)
		
		let radians = asin(closeIconRect.size.height / diagonalLength)
		return Angle.radians(radians)
	}
	
	var yOffset: CGFloat {
		
		// Consider the bottom line.
		// It's not actually a line - it's a rectangle (with height = lineHeight).
		// And when we rotate it, we are using AnchorPoint.bottomLeading.
		//
		// ----------------
		// |              |
		// ----------------
		// ^
		// So it rotates from this bottom left point,
		// and then the left edge of the rectangle looks like this:
		//
		//
		// \  | <view frame
		//  \ |    is here>
		//   \|__________
		// ^^
		// Left edge is here, outside the view.
		//
		// So if we want it to be perfectly centered,
		// then we need to use an offset.
		//
		// So we have a bit more trigonometry to do:
		//
		//  |\
		// a| \c <- the left edge
		//  |__\ <- this angle = α
		//    b
		//
		// We know:
		// c = lineHeight
		// α = 180 - 90 - diagonalAngle
		//
		// Thus:
		// a = c × sin(α)
		
		let c = lineHeight
		let α = Angle(degrees: 90 - diagonalAngle.degrees)
		
		let a = c * sin(α.radians)
		return (a / 2)
	}

	var xOffset: CGFloat {
		
		//  |\
		// a| \c
		//  |__\
		//    b
		//
		// We already know a & c:
		//
		// b^2 = c^2 - a^2
		//
		
		let c = lineHeight
		let a = yOffset * 2
		
		let b = sqrt(pow(c, 2) - pow(a, 2))
		return (b / 2)
	}
}

extension Angle {
	
	var negative: Angle {
		return Angle.radians(self.radians * -1.0)
	}
}
