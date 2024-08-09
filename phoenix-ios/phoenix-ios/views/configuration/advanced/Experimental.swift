import SwiftUI
import PhoenixShared

fileprivate let filename = "Experimental"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct Experimental: View {
	
	@State var address: String? = AppSecurity.shared.getBip353Address()
	@State var isClaiming: Bool = false
	
	enum ClaimError: Error {
		case noChannels
		case timeout
	}
	@State var claimError: ClaimError? = nil
	
	@State var claimIndex: Int = 0
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			content()
			toast.view()
		}
		.navigationTitle("Experimental")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_bip353()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
	}
	
	@ViewBuilder
	func section_bip353() -> some View {
		
		Section {
			
			VStack(alignment: HorizontalAlignment.leading, spacing: 24) {
				Label {
					section_bip353_info()
				} icon: {
					Image(systemName: "at")
				}
				
				if address == nil {
					section_bip353_claim()
				}
				
				if let err = claimError {
					section_bip353_error(err)
				}
			}
			
		} /* Section.*/ header: {
			
			Text("BIP353 DNS Address")
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_bip353_info() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			
			if let address {
				HStack(alignment: VerticalAlignment.center, spacing: 4) {
					Text(address)
					Spacer(minLength: 0)
					Button {
						copyText(address)
					} label: {
						Image(systemName: "square.on.square")
					}
				}
				.font(.headline)
				
			} else {
				Text("No address yet...")
					.font(.headline)
			}
			
			Text(
				"""
				This is a human-readable address for your Bolt12 payment request.
				"""
			)
			.font(.subheadline)
			.foregroundColor(.secondary)
			
			if address != nil {
				Text(
					"""
					Want a prettier address? Use third-party services, or self-host the address!
					"""
				)
				.font(.subheadline)
				.foregroundColor(.secondary)
				.padding(.top, 8)
			}
			
		} // </VStack>
	}
	
	@ViewBuilder
	func section_bip353_claim() -> some View {
		
		ZStack(alignment: Alignment.leading) {
			
			if isClaiming {
				Label {
					Text("")
				} icon: {
					ProgressView()
						.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
				}
			}
			
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer(minLength: 10)
				
				Button {
					claimButtonTapped()
				} label: {
					Text("Claim my address")
				}
				.buttonStyle(.borderedProminent)
				.buttonBorderShape(.capsule)
				.disabled(isClaiming)
				
				Spacer(minLength: 10)
			} // </HStack>
		}
	}
	
	@ViewBuilder
	func section_bip353_error(_ err: ClaimError) -> some View {
		
		Label {
			switch err {
			case .noChannels:
				Text(
					"""
					You need at least one channel to claim your address. \
					Try adding funds to your wallet and try again.
					"""
				)
				
			case .timeout:
				Text(
					"""
					The request timed out. \
					Please check your internet connection and try again.
					"""
				)
			}
		} icon: {
			Image(systemName: "exclamationmark.triangle")
				.foregroundColor(.appNegative)
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func claimButtonTapped() {
		log.trace("claimButtonTapped")
		
		let channels = Biz.business.peerManager.channelsValue()
		guard !channels.isEmpty else {
			claimError = .noChannels
			return
		}
		
		guard
			let peer = Biz.business.peerManager.peerStateValue(),
			!isClaiming
		else {
			return
		}
		
		isClaiming = true
		claimError = nil
		
		let idx = claimIndex
		let finish = {(result: Result<String, ClaimError>) in
			
			guard self.claimIndex == idx else {
				return
			}
			self.claimIndex += 1
			self.isClaiming = false
			
			switch result {
			case .success(let addr):
				self.address = addr
				self.claimError = nil
				let _ = AppSecurity.shared.setBip353Address(addr)
				
			case .failure(let err):
				self.claimError = err
			}
		}
		
		Task { @MainActor in
			do {
				let addr = try await peer.requestAddress(languageSubtag: "en")
				finish(.success(addr))
				
			} catch {
				finish(.failure(.timeout))
			}
		}
		
		Task { @MainActor in
			try await Task.sleep(seconds: 5)
			finish(.failure(.timeout))
		}
	}
	
	func copyText(_ text: String) -> Void {
		log.trace("copyText()")
		
		UIPasteboard.general.string = text
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			style: .chrome
		)
	}
}
