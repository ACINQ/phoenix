import SwiftUI

fileprivate let filename = "UsingTorSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct UsingTorSheet: View {
	
	let didCancel: () -> Void
	let didConfirm: () -> Void
	
	@State var iUnderstandState: Bool = false
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Using Tor")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			
			Spacer()
		}
		.padding(.horizontal)
		.padding(.vertical, 8)
		.background(
			Color(UIColor.secondarySystemBackground)
				.cornerRadius(15, corners: [.topLeft, .topRight])
		)
		.padding(.bottom, 4)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			content_message()
			content_checkbox()
			content_buttons()
		}
		.padding(.all)
	}
	
	@ViewBuilder
	func content_message() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
			
			Text(
				"""
				Enabling this option will force Phoenix to only connect \
				to onion services for Lightning and Electrum. Note that a \
				Tor proxy application is required.
				"""
			)
			
			Text("Be advised").font(.headline)
			Text("Tor can improve privacy, but may cause performance issues and missed payments.")
		}
	}
	
	@ViewBuilder
	func content_checkbox() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Toggle(isOn: $iUnderstandState) {
				Text("I understand")
					.foregroundColor(.appAccent)
					.bold()
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			
			Spacer()
		} // </HStack>
		.padding()
		.background(Color(.systemGroupedBackground))
		.cornerRadius(10)
	}
	
	@ViewBuilder
	func content_buttons() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 25) {
			Spacer()
			
			Button {
				cancelButtonTapped()
			} label: {
				Text("Cancel")
			}
			
			Button {
				confirmButtonTapped()
			} label: {
				Text("Confirm")
			}
			.disabled(!iUnderstandState)
		}
		.font(.title3)
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func cancelButtonTapped() {
		log.trace("cancelButtonTapped()")
		
		smartModalState.close(animationCompletion: {
			didCancel()
		})
	}
	
	func confirmButtonTapped() {
		log.trace("confirmButtonTapped()")
		
		smartModalState.close(animationCompletion: {
			didConfirm()
		})
	}
}
