import SwiftUI
import os.log

fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "FinishBackupView"
)

struct FinishBackupView: View {
	
	let userPattern: Set<DotPoint>
	let backup: EntropyGridBackup
	
	let gridSize: UInt16 = 8
	let dotSize: CGFloat = 4
	let dotDetectionSize: CGFloat = 15
	let canvasFrameSize: CGFloat = 300
	let canvasBorderSize: CGFloat = 4
	
	@State var name: String = ""
	
	@EnvironmentObject var router: Router
	
	@ViewBuilder
	var body: some View {
		
		GeometryReader { geometry in
			ScrollView(.vertical) {
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer(minLength: 0)
					content()
					Spacer(minLength: 0)
				}
			}
		}
	}
		
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer()
			
			instructions()
				.padding(.bottom, 40)
			canvas()
				.padding(.bottom, 20)
			saveOptions()
			
			Spacer()
			
			Button {
				finishBackup()
			} label: {
				Text("Finish").font(.title2)
			}
			.padding(.bottom, 20)
		}
	}
	
	@ViewBuilder
	func instructions() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Spacer(minLength: 0)
			Text("To restore your wallet you will need to reproduce this pattern.")
				.multilineTextAlignment(.center)
			Spacer(minLength: 0)
		}
		.frame(width: canvasFrameSize)
	}
	
	@ViewBuilder
	func canvas() -> some View {
		
		Canvas { context, size in
			
			let hSpace = size.width / CGFloat(gridSize + 1)
			let vSpace = size.height / CGFloat(gridSize + 1)

			for gridY in 0..<gridSize {
				for gridX in 0..<gridSize {
					
					let detected = userPattern.contains(DotPoint(x: gridX, y: gridY))
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
			
		} // </Canvas>
		.frame(width: canvasFrameSize, height: canvasFrameSize)
		.padding(.all, canvasBorderSize)
		.border(Color(uiColor: .darkGray), width: canvasBorderSize)
	}
	
	@ViewBuilder
	func saveOptions() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 4) {
			
			Text("Name your backup:")
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				TextField(defaultName(), text: $name)
				
				// Clear button (appears when TextField's text is non-empty)
				if name != "" {
					Button {
						name = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(.secondary)
					}
				}
			} // </HStack>
			.padding(.all, 8)
			.overlay(
				RoundedRectangle(cornerRadius: 8)
					.stroke(.secondary, lineWidth: 1)
			)
			
		} // </VStack>
		.frame(width: canvasFrameSize)
	}
	
	func defaultName() -> String {
		return "Wallet 1"
	}
	
	func finishBackup() {
		log.trace("finishBackup()")
		
		let cloudBackup = EntropyGridCloudBackup(
			name: name,
			timestamp: Date.now,
			backup: backup
		)
		
		do {
			let jsonData = try JSONEncoder().encode(cloudBackup)
			
			let fileURL = randomFileURL()
			try jsonData.write(to: fileURL, options: .atomic)
			
		} catch {
			log.error("Error: \(error)")
			return
		}
		
		router.popToRoot()
	}
	
	func randomFileURL() -> URL {
		
		let alphabet = "abcdefghijklmnopqrstuvwxyx".map { String($0) }
		var random = ""
		for _ in 0..<16 {
			let i = Int.random(in: 0..<alphabet.count)
			random.append(alphabet[i])
		}
		
		let filename = "\(random).json"
		return getDocumentsDirectory().appending(path: filename, directoryHint: .notDirectory)
	}
	
	func getDocumentsDirectory() -> URL {
		
		let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
		return paths[0]
	}
}
