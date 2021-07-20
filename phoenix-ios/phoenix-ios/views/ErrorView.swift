import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ErrorView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct ErrorView: View {
	
	let danger: LossOfSeedDanger
	
	@Environment(\.popoverState) private var popoverState: PopoverState
	@State private var popoverItem: PopoverItem? = nil
	
	@StateObject var toast = Toast()
	
	var body: some View {
		
		ZStack {
			
			main.zIndex(0) // needed for proper animation

			if let popoverItem = popoverItem {
				PopoverWrapper(dismissable: popoverItem.dismissable) {
					popoverItem.view
				}
				.zIndex(1) // needed for proper animation
			}
			
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onReceive(popoverState.display) { (newPopoverItem: PopoverItem) in
			withAnimation {
				popoverItem = newPopoverItem
			}
		}
		.onReceive(popoverState.close) { _ in
			withAnimation {
				popoverItem = nil
			}
		}
	}
	
	@ViewBuilder
	var main: some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if AppDelegate.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.edgesIgnoringSafeArea([.horizontal, .bottom]) // not underneath status bar
			}

			mainContent
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
	}
	
	@ViewBuilder
	var mainContent: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			Spacer()
			
			Text("A temporary error occurred, and Phoenix is unabled to continue loading.")
				.font(.title2)
				.multilineTextAlignment(.center)
				.padding(.bottom, 60)
			
			Text("Please restart Phoenix")
				.padding(.bottom)
			
			Button {
				terminateApp()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "cross.circle.fill")
						.foregroundColor(Color.appNegative)
					
					Text("Terminate App")
						.fontWeight(.medium)
						.foregroundColor(Color.appNegative)
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appNegative
				)
			)
			.padding(.bottom, 30)
			
			Text(styled: NSLocalizedString(
				"If the problem persists, please contact support at **phoenix@acinq.co**.",
				comment: "ErrorView"
			))
			.multilineTextAlignment(.center)
			.padding(.bottom)
			
			Button {
				showDetails()
			} label: {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Image(systemName: "stethoscope")
					Text("Details")
				}
				.padding([.top, .bottom], 8)
				.padding([.leading, .trailing], 16)
			}
			.buttonStyle(
				ScaleButtonStyle(
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent
				)
			)
			
			Spacer()
			Spacer() // Move center up a little bit
		
		} // </VStack>
		.padding([.top, .bottom])
		.padding([.leading, .trailing], 40)
	}
	
	func terminateApp() {
		log.trace("terminateApp()")
		
		exit(0)
	}
	
	func showDetails() {
		log.trace("showDetails()")
		
		popoverState.display.send(PopoverItem(
		
			ErrorDetailsView(danger: danger, toast: toast).anyView,
			dismissable: false
		))
	}
}

struct ErrorDetailsView: View, ViewName {
	
	let danger: LossOfSeedDanger
	@ObservedObject var toast: Toast
	
	@State var sharing: String? = nil
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.popoverState) var popoverState: PopoverState
	
	enum ButtonHeight: Preference {}
	let buttonHeightReader = GeometryPreferenceReader(
		key: AppendValue<ButtonHeight>.self,
		value: { [$0.size.height] }
	)
	@State var buttonHeight: CGFloat? = nil
	
	var body: some View {
		
		let errTxt = errorText()
		VStack {
			
			ScrollView {
				Text(errTxt)
					.font(.caption)
					.padding()
			}
			.frame(height: 200)
			.frame(maxWidth: .infinity, alignment: .leading)
			
			HStack {
				
				Button {
					UIPasteboard.general.string = errTxt
					toast.pop(
						Text("Copied to pasteboard!").anyView,
						colorScheme: colorScheme.opposite
					)
				} label: {
					Image(systemName: "square.on.square")
						.imageScale(.large)
						.font(.body)
						.read(buttonHeightReader)
				}
				
				Divider()
					.frame(height: buttonHeight)
					.padding([.leading, .trailing], 8)

				Button {
					sharing = errTxt
				} label: {
					Image(systemName: "square.and.arrow.up")
						.imageScale(.large)
						.font(.body)
						.read(buttonHeightReader)
				}
				.sharing($sharing)
				
				Spacer()
				Button("Close") {
					closePopover()
				}
				.font(.title2)
				
			} // </HStack>
			.padding(.top, 10)
			.padding([.leading, .trailing])
			.padding(.bottom, 10)
			.background(
				Color(UIColor.secondarySystemBackground)
			)
			
		} // </VStack>
		.assignMaxPreference(for: buttonHeightReader.key, to: $buttonHeight)
	}
	
	func errorText() -> String {
		
		var txt: String = ""
		
		txt += "readSecurityFileError: "
		if let err = danger.readSecurityFileError {
			switch err {
				case .fileNotFound:
					txt += "fileNotFound"
				case .errorReadingFile(let underlying):
					txt += "readingFile:\n\(String(describing: underlying))"
				case .errorDecodingFile(let underlying):
					txt += "decodingFile:\n\(String(describing: underlying))"
			}
		} else {
			txt += "none"
		}
		
		txt += "\n\n"
		
		txt += "readKeychainError: "
		if let err = danger.readKeychainError {
			switch err {
				case .keychainOptionNotEnabled:
					txt += "keychainOptionNotEnabled"
				case .keychainBoxCorrupted(let underlying):
					txt += "keychainBoxCorrupted:\n\(String(describing: underlying))"
				case .errorReadingKey(let underlying):
					txt += "errorReadingKey:\n\(String(describing: underlying))"
				case .keyNotFound:
					txt += "keyNotFound"
				case .errorOpeningBox(let underlying):
					txt += "errorOpeningBox:\n\(String(describing: underlying))"
				case .invalidMnemonics:
					txt += "invalidMnemonics"
			}
		} else {
			txt += "none"
		}
		
		txt += "\n"
		
		return txt
	}
	
	func closePopover() {
		log.trace("[\(viewName)] closePopover()")
		
		popoverState.close.send()
	}
}

