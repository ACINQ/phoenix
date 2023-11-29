import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "BigPatternSketchView"
)

fileprivate struct Line {
	var points = [CGPoint]()
}

fileprivate enum CanvasAction {
	case drawLine
	case tap
}

struct BigPatternSketchView: View {
	
	/// The number of rows in the grid.
	let gridWidth: UInt16 = 12
	
	/// The number of columns in the grid.
	let gridHeight: UInt16 = 18
	
	/// Every item on the grid is represented by a dot.
	/// This is the size (diameter) of the dot.
	let dotSize: CGFloat = 4
	
	/// When a user taps or draws on the grid, a "hit" is detected on a dot if
	/// the tap/line falls within an invisible dot of this size (diameter).
	let dotDetectionSize: CGFloat = 15
	
	/// Color of line when user draws on the canvas
	let lineColor: Color = .blue
	
	/// Width of line when user draws on the canvas
	let lineWidth: CGFloat = 5
	
	/// When the user taps, this is the size (diameter) of the tap mark
	let tapDotSize: CGFloat = 5
	
	/// The size of the Canvas area the user draws/taps in
	let canvasFrameWidth: CGFloat = 300
	let canvasFrameHeight: CGFloat = 450
	
	/// The width of the border surrounding the canvas
	let canvasBorderSize: CGFloat = 4
	
	@State private var currentLine: Line = Line()
	@State private var lines: [Line] = []
	@State private var taps: [CGPoint] = []
	
	@State private var activeDots: [DotPoint: Bool] = [:]
	
	@State private var actionOrder: [CanvasAction] = []
	
	@State private var linesHidden = false
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle("Mobile Border Wallet")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer(minLength: 0)
			
			canvas()
				.padding(.bottom, 20)
			canvasControlOptions()
			
			Spacer(minLength: 0)
			
		} // </VStack>
	}
	
	@ViewBuilder
	func canvas() -> some View {
		
		Canvas { context, size in
			
			let hSpace = size.width / CGFloat(gridWidth + 1)
			let vSpace = size.height / CGFloat(gridHeight + 1)

			for gridY in 0..<gridHeight {
				for gridX in 0..<gridWidth {
					
					let detected = activeDots[DotPoint(x: gridX, y: gridY)] ?? false
					if detected {
						let dotDetectionRect = CGRect(
							x: (CGFloat(gridX + 1) * hSpace) - (dotDetectionSize / 2),
							y: (CGFloat(gridY + 1) * vSpace) - (dotDetectionSize / 2),
							width: dotDetectionSize,
							height: dotDetectionSize
						)
						context.fill(
							Circle().path(in: dotDetectionRect),
							with: .color(Color(uiColor: .systemMint))
						)
					}
					
					let dotRect = CGRect(
						x: (CGFloat(gridX + 1) * hSpace) - (dotSize / 2),
						y: (CGFloat(gridY + 1) * vSpace) - (dotSize / 2),
						width: dotSize,
						height: dotSize
					)
					context.fill(
						Circle().path(in: dotRect),
						with: .color(Color(uiColor: .systemGray4))
					)
				}
			}
	
			var plusImage = context.resolve(Image(systemName: "plus"))
			plusImage.shading = .color(Color(uiColor: .systemGray6))
			context.draw(plusImage, at: CGPoint(x: size.width / 2, y: size.height / 2))
			
			if !linesHidden {
				
				for tap in taps {
					let tapRect = CGRect(
						x: tap.x - (tapDotSize / 2),
						y: tap.y - (tapDotSize / 2),
						width: tapDotSize,
						height: tapDotSize
					)
					context.fill(
						Circle().path(in: tapRect),
						with: .color(lineColor)
					)
				}
				
				let allLines = lines + [currentLine]
				for line in allLines {
					var path = Path()
					path.addLines(line.points)
					context.stroke(path, with: .color(lineColor), lineWidth: lineWidth)
				}
			}
			
		} // </Canvas>
		.gesture(dragGesture())
		.frame(width: canvasFrameWidth, height: canvasFrameHeight)
		.padding(.all, canvasBorderSize)
		.border(Color(uiColor: .darkGray), width: canvasBorderSize)
	}
	
	@ViewBuilder
	func canvasControlOptions() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 32) {
			
			Button {
				toggleLines()
			} label: {
				VStack(alignment: HorizontalAlignment.center, spacing: 4) {
					Image(systemName: linesHidden ? "eye.slash.circle" : "eye.circle")
						.font(.title2)
						.imageScale(.large)
					Text("lines")
						.font(.callout)
						.foregroundColor(Color(uiColor: .darkGray))
				}
			}
			
			Spacer()
			
			Button {
				undoLast()
			} label: {
				VStack(alignment: HorizontalAlignment.center, spacing: 4) {
					Image(systemName: "arrowshape.turn.up.backward.circle")
						.font(.title2)
						.imageScale(.large)
					Text("undo")
						.font(.callout)
						.foregroundColor(Color(uiColor: .darkGray))
				}
			}
			
			Button {
				clearCanvas()
			} label: {
				VStack(alignment: HorizontalAlignment.center, spacing: 4) {
					Image(systemName: "trash.circle")
						.font(.title2)
						.imageScale(.large)
					Text("clear")
						.font(.callout)
						.foregroundColor(Color(uiColor: .darkGray))
				}
			}
		} // </HStack>
		.frame(maxWidth: 300)
		.padding(.horizontal, 20)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	func dragGesture() -> some Gesture {
		
		return DragGesture(minimumDistance: 0, coordinateSpace: .local)
			.onChanged { value in
				
				self.currentLine.points.append(value.location)
				self.updateActiveDots()
			}
			.onEnded { value in
				
				// The drag gesture also works as a tap gesture.
				// In the case of a tap, both `onChanged` and then `onEnded` fire, both with the same point.
				
				let isTap: Bool
				if currentLine.points.isEmpty {
					isTap = true
				} else if currentLine.points.count == 1 {
					isTap = currentLine.points[0] == value.location
				} else {
					isTap = false
				}
				
				if isTap {
					self.taps.append(value.location)
					self.currentLine = Line()
					self.actionOrder.append(.tap)
					
				} else {
					self.currentLine.points.append(value.location)
					self.lines.append(currentLine)
					self.currentLine = Line()
					self.actionOrder.append(.drawLine)
				}
				
				self.updateActiveDots()
			}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func updateActiveDots() {
		
		let allLines = lines + [currentLine]
		
		let hSpace = canvasFrameWidth / CGFloat(gridWidth + 1)
		let vSpace = canvasFrameHeight / CGFloat(gridHeight + 1)

		for gridY in 0..<gridHeight {
			for gridX in 0..<gridWidth {
				
				// Do any of the drawn lines intersect with this dot ?
				// Remember that the "dot detection circle" is bigger than the dot itself.
				
				let dotDetectionRect = CGRect(
					x: (CGFloat(gridX + 1) * hSpace) - (dotDetectionSize / 2),
					y: (CGFloat(gridY + 1) * vSpace) - (dotDetectionSize / 2),
					width: dotDetectionSize,
					height: dotDetectionSize
				)
				
				var detected = false
				if #available(iOS 16, *) {

					// On iOS 16, there is a nice API we can use:
					// CGPath.intersect(otherPath)
					
					let dotPath = Circle().path(in: dotDetectionRect).cgPath
					
					// Step 1 of 2:
					// Check to see if any of the taps intersect with the dot.
					for tapPoint in taps {
						
						let tapDotRect = CGRect(
							x: tapPoint.x - (tapDotSize / 2),
							y: tapPoint.y - (tapDotSize / 2),
							width: tapDotSize,
							height: tapDotSize
						)
						let tapDotPath = Circle().path(in: tapDotRect).cgPath
						
						if tapDotPath.intersects(dotPath) {
							detected = true
							break
						}
					}
					
					// Step 2 of 2:
					// Check to see if any of the lines intersect with the dot.
					if !detected {
						for line in allLines {
					
							var thinLinePath = Path()
							thinLinePath.addLines(line.points)
							let linePath = thinLinePath.strokedPath(StrokeStyle(lineWidth: lineWidth))

							if linePath.cgPath.intersects(dotPath) {
								detected = true
								break
							}
						}
					}

				} else { // iOS 15
					
					// On iOS 15, the easy API isn't available :(
					// But we at least have this method:
					// CGPath.contains(point)
					//
					// In order to use it we'll need to test several points within the "dot detection circle".
					// If you imagine drawing cross-hairs in the middle of the circle,
					// we will test various points along the those lines.
					//
					// It might not be as mathematically accurate as the iOS 16 API,
					// but in practice it seems to work quite well.
				
					let ddr = dotDetectionRect
					
					// Step 1 of 2:
					// Check to see if any of the taps intersect with the dot.
					outerLoop: for tapPoint in taps {
						
						let tapDotRect = CGRect(
							x: tapPoint.x - (tapDotSize / 2),
							y: tapPoint.y - (tapDotSize / 2),
							width: tapDotSize,
							height: tapDotSize
						)
						let tapDotPath = Circle().path(in: tapDotRect)
						
						if tapDotPath.boundingRect.intersects(ddr) {
							let tdp = tapDotPath.cgPath
							
							for x in stride(from: ddr.minX, through: ddr.maxX, by: lineWidth) {
								
								let p = CGPoint(x: x, y: ddr.midY)
								if tdp.contains(p) {
									detected = true
									break outerLoop
								}
							}
							for y in stride(from: ddr.minY, through: ddr.maxY, by: lineWidth) {
								
								let p = CGPoint(x: ddr.midX, y: y)
								if tdp.contains(p) {
									detected = true
									break outerLoop
								}
							}
						}
					}
					
					// Step 2 of 2:
					// Check to see if any of the lines intersect with the dot.
					if !detected {
						outerLoop: for line in allLines {
							
							var thinLinePath = Path()
							thinLinePath.addLines(line.points)
							let linePath = thinLinePath.strokedPath(StrokeStyle(lineWidth: lineWidth))
							
							if linePath.boundingRect.intersects(ddr) {
								let lp = linePath.cgPath
								
								for x in stride(from: ddr.minX, through: ddr.maxX, by: lineWidth) {
									
									let p = CGPoint(x: x, y: ddr.midY)
									if lp.contains(p) {
										detected = true
										break outerLoop
									}
								}
								for y in stride(from: ddr.minY, through: ddr.maxY, by: lineWidth) {
									
									let p = CGPoint(x: ddr.midX, y: y)
									if lp.contains(p) {
										detected = true
										break outerLoop
									}
								}
								
							} // </if linePath.boundingRect.intersects(ddr)>
						} // </outerloop>
					} // </if !detected>
				
				} // </iOS 15>
				
				self.activeDots[DotPoint(x: gridX, y: gridY)] = detected
			}
		}
	}
	
	func toggleLines() {
		log.trace("toggleLines()")
		
		linesHidden.toggle()
	}
	
	func undoLast() {
		log.trace("undoLast()")
		
		if let action = actionOrder.popLast() {
			switch action {
				case .drawLine : lines.removeLast()
				case .tap      : taps.removeLast()
			}
		}
		updateActiveDots()
	}
	
	func clearCanvas() {
		log.trace("clearCanvas()")
		
		currentLine = Line()
		lines.removeAll()
		taps.removeAll()
		activeDots.removeAll()
		actionOrder.removeAll()
	}
}
