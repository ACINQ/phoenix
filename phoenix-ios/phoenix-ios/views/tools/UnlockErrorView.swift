import SwiftUI

fileprivate let filename = "UnlockErrorView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct UnlockErrorView: View {
	
	let danger: UnlockError
	
	@ViewBuilder
	var body: some View {
		
		_UnlockErrorView(danger: danger)
			.modifier(GlobalEnvironment.errorInstance())
	}
}

fileprivate struct _UnlockErrorView: View {
	
	let danger: UnlockError
	
	@EnvironmentObject var popoverState: PopoverState
	@State var popoverItem: PopoverItem? = nil
	
	@StateObject var toast = Toast()
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			
			main().zIndex(0) // needed for proper animation

			if let popoverItem = popoverItem {
				PopoverWrapper(dismissable: popoverState.dismissable) {
					popoverItem.view
				}
				.zIndex(1) // needed for proper animation
			}
			
			toast.view()
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.onReceive(popoverState.itemPublisher) { (item: PopoverItem?) in
			withAnimation {
				popoverItem = item
			}
		}
	}
	
	@ViewBuilder
	func main() -> some View {
		
		ZStack {
			
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)

			if Biz.showTestnetBackground {
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
			
			Text("A temporary error occurred, and Phoenix is unable to continue loading.")
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
					cornerRadius: 16,
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appNegative
				)
			)
			.padding(.bottom, 30)
			
			Text("If the problem persists, please contact support at **phoenix@acinq.co**.")
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
					cornerRadius: 16,
					backgroundFill: Color.primaryBackground,
					borderStroke: Color.appAccent
				)
			)
			
			Spacer()
			Spacer() // Move center up a little bit
			
			Text("Phoenix Version: \(versionString())")
				.foregroundColor(.secondary)
		
		} // </VStack>
		.padding([.top, .bottom])
		.padding([.leading, .trailing], 40)
	}
	
	func versionString() -> String {
		return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
	}
	
	func terminateApp() {
		log.trace("terminateApp()")
		
		exit(0)
	}
	
	func showDetails() {
		log.trace("showDetails()")
		
		popoverState.display(dismissable: false) {
			ErrorDetailsView(danger: danger, toast: toast)
		}
	}
}

fileprivate struct ErrorDetailsView: View, ViewName {
	
	let danger: UnlockError
	@ObservedObject var toast: Toast
	
	@State var sharing: String? = nil
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@EnvironmentObject var popoverState: PopoverState
	
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
						NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
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
		return "\(danger)\n" // UnlockError implements CustomStringConvertible protocol
	}
	
	func closePopover() {
		log.trace("[\(viewName)] closePopover()")
		
		popoverState.close()
	}
}

