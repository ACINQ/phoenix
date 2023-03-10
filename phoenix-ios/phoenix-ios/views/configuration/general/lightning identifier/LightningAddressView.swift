import SwiftUI
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LightningAddressView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif


struct LightningAddressView: View {
	
	@State var username: String = ""
	@State var registrationRowTruncated: Bool = false
	
	@State var needsCheckAvailability: Bool = false
	@State var isCheckingAvailability: Bool = false
	@State var checkAvailabilityTimer: Timer? = nil
	
	@State var isValidUsername: Bool = false
	@State var isAvailableUsername: Bool = false
	@State var hasInvalidCharacters: Bool = false
	
	enum MaxTextFieldWidth: Preference {}
	let maxTextFieldWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxTextFieldWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxTextFieldWidth: CGFloat? = nil
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		content()
			.navigationTitle(NSLocalizedString("Lightning Address", comment: "Navigation Bar Title"))
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_explanation()
			section_register()
			section_notes()
		}
		.listStyle(.insetGrouped)
		.listBackgroundColor(.primaryBackground)
		.onChange(of: username) { text in
			usernameChanged()
		}
	}
	
	@ViewBuilder
	func section_explanation() -> some View {
		
		Section {
			VStack(alignment: HorizontalAlignment.leading, spacing: 15) {
				Label {
					Text("Like an email address, but for your Bitcoin!")
						.font(.headline)
				} icon: {
					let fontHeight = UIFont.preferredFont(forTextStyle: .headline).pointSize + 4
					Image("bitcoin")
						.resizable()
						.aspectRatio(contentMode: .fit)
						.frame(width: fontHeight, height: fontHeight, alignment: .center)
						.offset(x: 0, y: 2)
				}
				
				Label {
					Text("An easy way for anyone to send you Bitcoin instantly on the Lightning Network.")
						.foregroundColor(.secondary)
				} icon: {
					Image(systemName: "network")
				}
			}
		}
	}
	
	@ViewBuilder
	func section_register() -> some View {
		
		Section(header: Text("Choose your address")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				register_username()
					.padding(.bottom, 10)
				
				availability()
					.padding(.bottom, 40)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer()
					Button {
						reserveButtonTapped()
					} label: {
						HStack(alignment: VerticalAlignment.center, spacing: 4) {
							Image(systemName: "checkmark.seal")
							Text("Reserve")
						}
						.font(.title3.weight(.medium))
						.foregroundColor(.white)
						.padding(.horizontal, 12)
						.padding(.vertical, 4)
					} // </Button>
					.buttonStyle(ScaleButtonStyle(
						cornerRadius: 100,
						backgroundFill: Color.appAccent,
						disabledBackgroundFill: Color.gray
					))
					.disabled(reserveButtonDisabled())
					Spacer()
				}
			}
		}
	}
	
	@ViewBuilder
	func section_notes() -> some View {
		
		Section(header: Text("Notes")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					Text("Only the following characters are allowed:\n") +
					Text("a-z 0-9 -_.")
						.font(.system(.callout, design: .monospaced))
				} icon: {
					Image(systemName: "highlighter")
				}
				.font(.callout)
				.foregroundColor(hasInvalidCharacters ? Color.appNegative : Color.primary)
				.padding(.bottom, 15)
				
				Label {
					Text("You can change your address after 30 days.")
				} icon: {
					Image(systemName: "calendar")
				}
				.font(.callout)
				.foregroundColor(.secondary)
				
			} // </VStack>
			.padding(.vertical, 5)
		}
	}
	
	@ViewBuilder
	func register_username() -> some View {
		
		if !registrationRowTruncated {
			register_username_horizontal()
		} else {
			register_username_vertical()
		}
	}
	
	@ViewBuilder
	func register_username_horizontal() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 4) {
			register_username_textField()
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				Text("@myphoenix.app")
					.bold()
					.lineLimit(1)
					
			} wasTruncated: {
				registrationRowTruncated = true
				maxTextFieldWidth = nil
			}
		}
		.assignMaxPreference(for: maxTextFieldWidthReader.key, to: $maxTextFieldWidth)
	}
	
	@ViewBuilder
	func register_username_vertical() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 4) {
			register_username_textField()
			HStack(alignment: VerticalAlignment.center, spacing: 0) {
				Spacer()
				Text("@myphoenix.app")
			}
		}
	}
	
	@ViewBuilder
	func register_username_textField() -> some View {
		
		TextField(
			NSLocalizedString("username", comment: "TextField placeholder"),
			text: $username
		)
		.textFieldAutocapitalization(.never)
		.disableAutocorrection(true)
		.padding([.leading, .vertical], 8)
		.padding(.trailing, 4)
		.overlay(
			RoundedRectangle(cornerRadius: 8)
				.stroke(Color.textFieldBorder, lineWidth: 1)
		)
		.frame(maxWidth: maxTextFieldWidth)
		.read(maxTextFieldWidthReader)
	}
	
	@ViewBuilder
	func availability() -> some View {
		
		if username.isEmpty {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "circle.dotted")
					.font(.callout)
					.imageScale(.large)
				Text("Start typing to check availability")
					.font(.subheadline)
			}
			.foregroundColor(.appPositive)
			
		} else if !isValidUsername {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "pencil.circle")
					.font(.callout)
					.imageScale(.large)
				Text("Invalid username")
					.font(.subheadline)
			}
			.foregroundColor(.appNegative)
			
		} else if isCheckingAvailability || needsCheckAvailability {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "hourglass.circle")
					.font(.callout)
					.imageScale(.large)
				Text("Checking availability...")
					.font(.subheadline)
			}
			.foregroundColor(.primary)
			
		} else if !isAvailableUsername {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "x.circle.fill")
					.font(.callout)
					.imageScale(.large)
				Text("Username taken")
					.font(.subheadline)
			}
			.foregroundColor(.appNegative)
			
		} else {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "checkmark.circle.fill")
					.font(.callout)
					.imageScale(.large)
				Text("Username available")
					.font(.subheadline)
			}
			.foregroundColor(.appPositive)
		}
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------

	func reserveButtonDisabled() -> Bool {
		
		return needsCheckAvailability || isCheckingAvailability || !isValidUsername || !isAvailableUsername
	}
	
	// --------------------------------------------------
	// MARK: Utilities
	// --------------------------------------------------
	
	func checkIsValidUsername(_ str: String) -> Bool {
		
		let charset = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789-_.")
		return str.unicodeScalars.allSatisfy { charset.contains($0) }
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func usernameChanged() {
		log.trace("usernameChanged()")
		
		isValidUsername = checkIsValidUsername(username.lowercased())
		if isValidUsername {
			
			needsCheckAvailability = true
			
			if checkAvailabilityTimer != nil {
				checkAvailabilityTimer?.invalidate()
				checkAvailabilityTimer = nil
			}
			checkAvailabilityTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
				checkAvailability()
			}
		}
	}
	
	func checkAvailability() {
		log.trace("checkAvailability")
		
		let usernameSnapshot = username.lowercased()
		if !checkIsValidUsername(usernameSnapshot) {
			return
		}
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnid/availability")
		guard let requestUrl = url else { return }
		
		let body = [
			"username": usernameSnapshot
		]
		let bodyData = try? JSONSerialization.data(
			 withJSONObject: body,
			 options: []
		)
		
		var request = URLRequest(url: requestUrl)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		struct AvailabilityResponse: Codable {
			let available: Bool
		}
		
		let task = URLSession.shared.dataTask(with: request) { (data, response, error) in
			
			guard self.username.lowercased() == usernameSnapshot else {
				// TextField has changed since we sent the availability request.
				// Ignore response.
				return
			}
			
			if let data = data, let response: AvailabilityResponse = data.jsonDecode() {
				
				self.isAvailableUsername = response.available
				self.needsCheckAvailability = false
				
			} else {
				
				if let error = error {
					log.debug("/lnid/availability: error: \(String(describing: error))")
				}
				if let httpResponse = response as? HTTPURLResponse {
					log.debug("/lnid/availability: statusCode: \(httpResponse.statusCode)")
				}
				if let data = data, let dataString = String(data: data, encoding: .utf8) {
					log.debug("/lnid/availability: response:\n\(dataString)")
				}
			}
			
			self.isCheckingAvailability = false
		}
		
		isCheckingAvailability = true
		log.debug("/lnid/availability ...")
		task.resume()
	}
	
	func reserveButtonTapped() {
		log.trace("reserveButtonTapped()")
		
		// Todo...
	}
}
