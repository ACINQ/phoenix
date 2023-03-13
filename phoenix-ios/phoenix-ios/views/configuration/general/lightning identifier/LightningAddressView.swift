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
	
	@State var registration = Prefs.shared.lightningAddress
	
	@State var username: String = ""
	@State var registrationRowTruncated: Bool = false
	
	@State var needsCheckAvailability: Bool = false
	@State var isCheckingAvailability: Bool = false
	@State var checkAvailabilityTimer: Timer? = nil
	
	@State var isRegistering: Bool = false
	
	@State var isValidUsername: Bool = false
	@State var isAvailableUsername: Bool = false
	@State var hasInvalidCharacters: Bool = false
	
	@State var networkError: Bool = false
	
	@State var lnAddrTruncated_title1: Bool = false
	@State var lnAddrTruncated_title2: Bool = false
	@State var lnAddrTruncated_title3: Bool = false
	
	enum MaxTextFieldWidth: Preference {}
	let maxTextFieldWidthReader = GeometryPreferenceReader(
		key: AppendValue<MaxTextFieldWidth>.self,
		value: { [$0.size.width] }
	)
	@State var maxTextFieldWidth: CGFloat? = nil
	
	@StateObject var toast = Toast()
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
			content()
			toast.view()
		}
		.navigationTitle(NSLocalizedString("Lightning Address", comment: "Navigation Bar Title"))
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		List {
			section_explanation()
			if let registration {
				section_registration(registration)
				section_remember(registration)
			} else {
				section_register()
				section_notes()
			}
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
	func section_registration(_ registration: LightningAddress) -> some View {
		
		Section(header: Text("Your address")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				Label {
					section_registration_lnAddr(registration)
				} icon: {
					Button {
						copyLightningAddressToPasteboard()
					} label: {
						Image(systemName: "square.on.square")
					}
				}
				.padding(.bottom, 40)
				
				HStack(alignment: VerticalAlignment.center, spacing: 0) {
					Spacer()
					Button {
						changeButtonTapped()
					} label: {
						Text("Change")
					}
					Spacer()
				}
			}
			
		} // </Section>
	}
	
	@ViewBuilder
	func section_registration_lnAddr(_ registration: LightningAddress) -> some View {
		
		if lnAddrTruncated_title3 {
			
			Text(verbatim: lightningAddress(registration))
				.font(.body.weight(.semibold))
				.multilineTextAlignment(.trailing)
			
		} else if lnAddrTruncated_title2 {
			
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				Text(verbatim: lightningAddress(registration))
					.font(.title3.weight(.semibold))
					.lineLimit(1)
			} wasTruncated: {
				lnAddrTruncated_title3 = true
			}
			
		} else if lnAddrTruncated_title1 {
			
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				Text(verbatim: lightningAddress(registration))
					.font(.title2.weight(.semibold))
					.lineLimit(1)
			} wasTruncated: {
				lnAddrTruncated_title2 = true
			}
			
		} else {
			
			TruncatableView(fixedHorizontal: true, fixedVertical: true) {
				Text(verbatim: lightningAddress(registration))
					.font(.title.weight(.semibold))
					.lineLimit(1)
			} wasTruncated: {
				lnAddrTruncated_title1 = true
			}
		}
	}
	
	@ViewBuilder
	func section_remember(_ registration: LightningAddress) -> some View {
		
		Section(header: Text("Remember")) {
			VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
				
				if let altUsername = altUsername(registration) {
					Label {
						Text(
							"""
							Your username is case-insensitive:
							\(registration.username) = \(altUsername)
							"""
						)
					} icon: {
						Image(systemName: "textformat")
					}
					.font(.callout)
					.foregroundColor(.secondary)
					.padding(.bottom, 15)
				}
				
				Label {
					Text(
					  """
					  Phoenix is a non-custodial wallet - payments are received directly on this device. \
					  So your device must be on and connected to the internet to receive a payment.
					  """
					)
				} icon: {
					Image(systemName: "paperclip")
				}
				.font(.callout)
				.foregroundColor(.secondary)
				
			} // </VStack>
		} // </Section>
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
				Text(verbatim: lightningDomain())
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
				Text(verbatim: lightningDomain())
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
		.disabled(isRegistering)
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
			
		} else if isRegistering {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "hourglass.circle")
					.font(.callout)
					.imageScale(.large)
				Text("Reserving username...")
					.font(.subheadline)
			}
			.foregroundColor(.primary)
			
		} else if networkError {
			
			HStack(alignment: VerticalAlignment.center, spacing: 4) {
				Image(systemName: "exclamationmark.circle")
					.font(.callout)
					.imageScale(.large)
				Text("Error - check network connection")
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
		let lowercaseStr = str.lowercased()
		return lowercaseStr.unicodeScalars.allSatisfy { charset.contains($0) }
	}
	
	func lightningDomain() -> String {
		
		return "@phoenix.deusty.com"
	}
	
	func lightningAddress() -> String? {
		
		if let registration {
			return lightningAddress(registration)
		} else {
			return nil
		}
	}
	
	func lightningAddress(_ registration: LightningAddress) -> String {
		
		return "\(registration.username)\(lightningDomain())"
	}
	
	func altUsername(_ registration: LightningAddress) -> String? {
		
		var idx = 0
		let alt1 = registration.username.map { (c: Character) in
			let altC = (idx % 2 == 0) ? c.lowercased() : c.uppercased()
			idx += 1
			return altC
		}.joined()
		
		if alt1 != registration.username {
			return alt1
		}
		
		let alt2 = registration.username.lowercased()
		if alt2 != registration.username {
			return alt2
		}
		
		let alt3 = registration.username.uppercased()
		if alt3 != registration.username {
			return alt3
		}
		
		return nil
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func usernameChanged() {
		log.trace("usernameChanged()")
		
		isValidUsername = checkIsValidUsername(username)
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
	
	func copyLightningAddressToPasteboard() {
		log.trace("copyLightningAddressToPasteboard()")
		
		guard let registration else {
			return
		}
		
		let lnAddr = "\(registration.username)\(lightningDomain())"
		
		UIPasteboard.general.string = lnAddr
		toast.pop(
			NSLocalizedString("Copied to pasteboard!", comment: "Toast message"),
			colorScheme: colorScheme.opposite,
			alignment: .bottom
		)
	}
	
	func checkAvailability() {
		log.trace("checkAvailability")
		
		let _username = username
		if !checkIsValidUsername(_username) {
			return
		}
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnid/availability")
		guard let requestUrl = url else { return }
		
		let body = [
			"username": _username
		]
		let bodyData = try? JSONSerialization.data(
			 withJSONObject: body,
			 options: []
		)
		
		var request = URLRequest(url: requestUrl)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		let task = URLSession.shared.dataTask(with: request) { (data, response, error) in
			
			guard self.username == _username else {
				// TextField has changed since we sent the availability request.
				// Ignore response.
				return
			}
			
			if let data = data, let response: AvailabilityResponse = data.jsonDecode() {
				
				self.isAvailableUsername = response.available
				self.needsCheckAvailability = false
				self.networkError = false
				
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
				self.networkError = true
			}
			
			self.isCheckingAvailability = false
		}
		
		isCheckingAvailability = true
		log.debug("/lnid/availability ...")
		task.resume()
	}
	
	func reserveButtonTapped() {
		log.trace("reserveButtonTapped()")
		
		let _username = username
		if !checkIsValidUsername(_username) {
			return
		}
		
		guard let nodeId = Biz.nodeId else {
			return
		}
		
		let url = URL(string: "https://phoenix.deusty.com/v1/pub/lnid/register")
		guard let requestUrl = url else { return }
		
		let body = [
			"username" : _username,
			"node_id"  : nodeId
		]
		let bodyData = try? JSONSerialization.data(
			 withJSONObject: body,
			 options: []
		)
		
		var request = URLRequest(url: requestUrl)
		request.httpMethod = "POST"
		request.httpBody = bodyData
		
		let task = URLSession.shared.dataTask(with: request) { (data, response, error) in
			
			if let data = data, let response = RegisterResponse.decode(data) {
				
				switch response.result {
					case .Left(let success):
						let addr = LightningAddress(username: _username, created: success.createdDate)
						Prefs.shared.lightningAddress = addr
						self.registration = addr
					
					case .Right(let failure):
						log.debug("/lnid/register: error_msg: \(failure.error)")
						log.debug("/lnid/register: error_code: \(failure.error_code)")
						self.isAvailableUsername = false
				}
				self.networkError = false
				
			} else {
				
				if let error = error {
					log.debug("/lnid/register: error: \(String(describing: error))")
				}
				if let httpResponse = response as? HTTPURLResponse {
					log.debug("/lnid/register: statusCode: \(httpResponse.statusCode)")
				}
				if let data = data, let dataString = String(data: data, encoding: .utf8) {
					log.debug("/lnid/register: response:\n\(dataString)")
				}
				
				self.networkError = true
			}
			
			self.isRegistering = false
		}
		
		isRegistering = true
		log.debug("/lnid/register ...")
		task.resume()
	}
	
	func changeButtonTapped() {
		log.trace("changeButtonTapped()")
		
		// Todo...
	}
}

// MARK: -

struct AvailabilityResponse: Codable {
	let available: Bool
}

struct RegisterResponse {
	let result: Either<Success, Failure>
	
	struct Success: Codable {
		let message: String
		let username: String
		let created: Int64
		
		var createdDate: Date {
			return created.toDate(from: .milliseconds)
		}
	}
	
	struct Failure: Codable {
		let error: String
		let error_code: Int
	}
	
	static func decode(_ data: Data) -> RegisterResponse? {
		
		if let success: Success = data.jsonDecode() {
			return RegisterResponse(result: Either.Left(success))
		}
		if let failure: Failure = data.jsonDecode() {
			return RegisterResponse(result: Either.Right(failure))
		}
		return nil
	}
}
