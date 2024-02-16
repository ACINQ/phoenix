import SwiftUI

fileprivate let filename = "BtcAddrOptionsSheet"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct BtcAddrOptionsSheet: View {
	
	@Binding var swapInAddressType: SwapInAddressType
	
	@EnvironmentObject var smartModalState: SmartModalState
	
	@ViewBuilder
	var body: some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			header()
			content()
		}
		.onChange(of: swapInAddressType) { _ in
			swapInAddressTypeChanged()
		}
	}
	
	@ViewBuilder
	func header() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			Text("Bitcoin address format")
				.font(.title3)
				.accessibilityAddTraits(.isHeader)
				.accessibilitySortPriority(100)
			Spacer()
			Button {
				closeSheet()
			} label: {
				Image(systemName: "xmark").imageScale(.medium).font(.title2)
			}
			.accessibilityLabel("Close")
			.accessibilityHidden(smartModalState.dismissable)
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
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Toggle(isOn: taprootBinding()) {
				Text("Taproot (recommended)")
					.foregroundColor(.appAccent)
					.bold()
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			.padding(.bottom, 5)
			
			Label {
				Text(
					"""
					Default format, with better privacy, cheaper fees and address rotation. \
					Some older services or wallets may not understand this modern address format.
					"""
				)
				.font(.subheadline)
				.foregroundColor(.secondary)
			} icon: {
				invisibleImage()
			}
			.padding(.bottom, 15)
			
			Toggle(isOn: legacyBinding()) {
				Text("Legacy")
					.foregroundColor(.appAccent)
					.bold()
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: onImage(),
				offImage: offImage()
			))
			
			Label {
				Text(
					"""
					A less efficient and less private format that does not rotate addresses. \
					However, it is compatible with almost every service and wallet.
					"""
				)
				.font(.subheadline)
				.foregroundColor(.secondary)
			} icon: {
				invisibleImage()
			}
		}
		.padding()
	}
	
	@ViewBuilder
	func onImage() -> some View {
		Image(systemName: "smallcircle.filled.circle")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
		
	@ViewBuilder
	func offImage() -> some View {
		Image(systemName: "circle")
			.imageScale(.large)
			.foregroundColor(.appAccent)
	}
	
	@ViewBuilder
	func invisibleImage() -> some View {
		
		Image(systemName: "circle")
			.imageScale(.large)
			.foregroundColor(.clear)
			.accessibilityHidden(true)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	private func taprootBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { swapInAddressType == .taproot },
			set: { if $0 { swapInAddressType = .taproot }}
		)
	}
	
	private func legacyBinding() -> Binding<Bool> {
		
		return Binding<Bool>(
			get: { swapInAddressType == .legacy },
			set: { if $0 { swapInAddressType = .legacy } }
		)
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func swapInAddressTypeChanged() {
		log.trace("swapInAddressTypeChanged()")
		
		// It's nice to add a slight delay before we close the sheet.
		// Because it's assuring to visually see the Toggle update, and then see the sheet close.
		
		DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
			self.closeSheet()
		}
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		
		smartModalState.close()
	}
}
