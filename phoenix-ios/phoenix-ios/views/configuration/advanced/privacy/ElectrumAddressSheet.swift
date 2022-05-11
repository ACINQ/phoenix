import SwiftUI
import Combine
import CryptoKit
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "ElectrumAddressSheet"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

enum CertInfoType: Int {
	case sha256Fingerprint
	case sha1Fingerprint
	case commonName
	case subjectSummary
	case email
	
	static func first() -> CertInfoType {
		return CertInfoType(rawValue: 0)!
	}
	
	func next() -> CertInfoType {
		if let next = CertInfoType(rawValue: self.rawValue + 1) {
			return next
		} else {
			return CertInfoType.first()
		}
	}
}

struct ElectrumAddressSheet: View {
	
	let DEFAULT_PORT: UInt16 = 50002
	
	@ObservedObject var mvi: MVIState<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>
	
	@State var isCustomized: Bool
	@State var host: String
	@State var port: String
	
	@State var invalidHost = false
	@State var invalidPort = false
	
	@State var userHasMadeChanges: Bool = false
	@State var disableTextFields: Bool = false
	
	@State var activeCheck: AnyCancellable? = nil
	@State var checkResult: Result<TLSConnectionStatus, TLSConnectionError>? = nil
	
	@State var certInfoType: CertInfoType = .sha256Fingerprint
	@State var shouldTrustPubKey: Bool = false
	
	@StateObject var toast = Toast()
	
	var untrustedCert: SecCertificate? {
		switch checkResult {
			case .success(let status):
				switch status {
					case .untrusted(let cert): return cert
					default: return nil
				}
			default: return nil
		}
	}
	
	var isUntrustedCert: Bool {
		return (untrustedCert != nil)
	}
	
	enum TitleWidth: Preference {}
	let titleWidthReader = GeometryPreferenceReader(
		key: AppendValue<TitleWidth>.self,
		value: { [$0.size.width] }
	)
	@State var titleWidth: CGFloat? = nil
	
	@Environment(\.colorScheme) var colorScheme: ColorScheme
	@Environment(\.shortSheetState) var shortSheetState: ShortSheetState
	
	init(mvi: MVIState<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>) {
		self.mvi = mvi
		
		_isCustomized = State(initialValue: mvi.model.isCustom())
		let customConfig = mvi.model.configuration as? ElectrumConfig.Custom
		let host = customConfig?.server.host ?? ""
		let port = customConfig?.server.port ?? 0
		
		if mvi.model.isCustom() && host.count > 0 {
			_host = State(initialValue: host)
			_port = State(initialValue: String(port))
		} else {
			_host = State(initialValue: "")
			_port = State(initialValue: "")
		}
	}

	// --------------------------------------------------
	// MARK: ViewBuilders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		ZStack(alignment: Alignment.bottom) {
			content()
			toast.view().padding(.bottom, 75)
		}
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			VStack(alignment: HorizontalAlignment.leading) {
				
				Toggle(isOn: $isCustomized.animation()) {
					Text("Use a custom server")
				}
				.onChange(of: isCustomized) { _ in
					onToggleDidChange()
				}
				.padding(.trailing, 2) // Toggle draws outside its bounds
			}
			.padding(.bottom, 15)
			.background(Color(UIColor.systemBackground))
			.zIndex(2)
			
			if isCustomized { // animation control
			
				customizeServerView()
					.transition(.move(edge: .top).animation(.easeInOut(duration: 0.3)))
					.zIndex(1)
				
			} else {
				
				randomServerView()
					.transition(.asymmetric(
						insertion : .opacity.animation(.easeInOut(duration: 0.1).delay(0.3)),
						removal   : .identity // .opacity.animation(.linear(duration: 0.05))
					))
					.zIndex(0)
			}
			
			footer()
				.padding(.top)
			
		} // </VStack>
		.clipped()
		.padding()
		
	} // </var body: some View>
	
	@ViewBuilder
	func randomServerView() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Text("A random Electrum server will be selected on each connection attempt.")
				.font(.footnote)
			
			Spacer()
		}
		.padding(.top, 10)
	}
	
	@ViewBuilder
	func customizeServerView() -> some View {
		
		VStack(alignment: HorizontalAlignment.leading) {

			// == Server: ([TextField][X]) ===
			HStack(alignment: .firstTextBaseline) {
				Text("Server")
					.font(.subheadline)
					.fontWeight(.thin)
					.foregroundColor(invalidHost ? Color.appNegative : Color.primary)
					.read(titleWidthReader)
					.frame(width: titleWidth, alignment: .leading)

				HStack {
					TextField("example.com", text: $host,
						onCommit: {
							onHostDidCommit()
						}
					)
					.keyboardType(.URL)
					.disableAutocorrection(true)
					.autocapitalization(.none)
					.foregroundColor(disableTextFields ? .secondary : .primary)
					.onChange(of: host) { _ in
						onHostDidChange()
					}

					Button {
						host = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(Color(UIColor.tertiaryLabel))
					}
					.isHidden(!isCustomized || host == "")

				} // </HStack> (textfield with clear button)
				.disabled(disableTextFields)
				.padding([.top, .bottom], 8)
				.padding(.leading, 16)
				.padding(.trailing, 8)
				.background(
					ZStack {
						Capsule()
							.fill(disableTextFields
								? Color(UIColor.systemFill)
								: Color(UIColor.systemBackground)
							)
						Capsule()
							.strokeBorder(Color(UIColor.separator))
					}
				)
			} // </HStack> (row)
			.frame(maxWidth: .infinity)
			.padding(.bottom)

			// == Port: ([TextField][X]) ===
			HStack(alignment: .firstTextBaseline) {
				Text("Port")
					.font(.subheadline)
					.fontWeight(.thin)
					.foregroundColor(invalidPort ? Color.appNegative : Color.primary)
					.read(titleWidthReader)
					.frame(width: titleWidth, alignment: .leading)

				HStack {
					TextField(verbatim: "\(DEFAULT_PORT)", text: $port,
						onCommit: {
							onPortDidCommit()
						}
					)
					.keyboardType(.numberPad)
					.disableAutocorrection(true)
					.foregroundColor(disableTextFields ? .secondary : .primary)
					.onChange(of: port) { _ in
						onPortDidChange()
					}

					Button {
						port = ""
					} label: {
						Image(systemName: "multiply.circle.fill")
							.foregroundColor(Color(UIColor.tertiaryLabel))
					}
					.isHidden(!isCustomized || port == "")
				} // </HStack> (textfield with clear button)
				.disabled(disableTextFields)
				.padding([.top, .bottom], 8)
				.padding(.leading, 16)
				.padding(.trailing, 8)
				.background(
					ZStack {
						Capsule()
							.fill(disableTextFields
								? Color(UIColor.systemFill)
								: Color(UIColor.systemBackground)
							)
						Capsule()
							.strokeBorder(Color(UIColor.separator))
					}
				)
			} // </HStack> (row)
			.frame(maxWidth: .infinity)
			.padding(.bottom, 30)

			customizeServerView_status()

		} // </VStack>
		.assignMaxPreference(for: titleWidthReader.key, to: $titleWidth)
		
	}
	
	@ViewBuilder
	func customizeServerView_status() -> some View {
		
		if invalidHost {
			Text("Invalid server. Use format: domain.tld")
				.bold()
				.foregroundColor(Color.appNegative)
				.font(.footnote)
		} else if invalidPort {
			Text("Invalid port. Valid range: [1 - 65535]")
				.bold()
				.foregroundColor(Color.appNegative)
				.font(.footnote)
		} else if activeCheck != nil {
			Text("Checking server certificate…")
				.italic()
				.font(.footnote)
		} else if let checkResult = checkResult {
			customizeServerView_status_checkResult(checkResult)
		} else {
			Text("Note: Server must have a certificate")
				.italic()
				.font(.footnote)
		}
	}
	
	@ViewBuilder
	func customizeServerView_status_checkResult(
		_ result: Result<TLSConnectionStatus, TLSConnectionError>
	) -> some View {
		
		switch result {
		case .success(let status):
			switch status {
			case .trusted:
				Text("Server certificate is trusted.")
					.italic()
					.font(.footnote)
			case .untrusted(let cert):
				customizeServerView_status_untrusted(cert)
			}
		case .failure(_):
			Text("Check spelling & internet connection")
				.bold()
				.foregroundColor(Color.appNegative)
				.font(.footnote)
		}
	}
	
	@ViewBuilder
	func customizeServerView_status_untrusted(_ cert: SecCertificate) -> some View {
		
		VStack(alignment: HorizontalAlignment.leading, spacing: 0) {
			
			Text("Server has untrusted certificate.")
				.bold()
				.foregroundColor(Color.appNegative)
				.font(.footnote)
				.padding(.bottom, 8)
			
			HStack(alignment: VerticalAlignment.bottom, spacing: 0) {
				Group {
					switch certInfoType {
					case .sha256Fingerprint:
						Text("SHA256 Fingerprint:")
					case .sha1Fingerprint:
						Text("SHA1 Fingerprint:")
					case .commonName:
						Text("Common Name:")
					case .subjectSummary:
						Text("Subject:")
					case .email:
						Text("Email:")
					}
				}
				.font(.footnote)
				
				Spacer()
				Button {
					certInfoType = certInfoType.next()
				} label: {
					Image(systemName: "arrowshape.turn.up.forward.fill")
				}
				.font(.body)
			}
			.padding(.bottom, 4)
			
			Group {
				switch certInfoType {
				case .sha256Fingerprint:
					Text(verbatim: "\(sha256Fingerprint(cert))")
				case .sha1Fingerprint:
					Text(verbatim: "\(sha1Fingerprint(cert))")
				case .commonName:
					Text(verbatim: "\(commonName(cert))")
				case .subjectSummary:
					Text(verbatim: "\(subjectSummary(cert))")
				case .email:
					Text(verbatim: "\(email(cert))")
				}
			}
			.font(.footnote)
			
			Spacer().frame(height: 16)
			
			Toggle(isOn: $shouldTrustPubKey) {
				Text("Trust certificate & pin public key")
					.lineLimit(nil)
					.alignmentGuide(VerticalAlignment.center) { d in
						d[VerticalAlignment.firstTextBaseline]
					}
			}
			.toggleStyle(CheckboxToggleStyle(
				onImage: checkmarkImage_on(),
				offImage: checkmarkImage_off()
			))
		}
	}
	
	@ViewBuilder
	func footer() -> some View {
		
		if isCustomized {
			footer_customServer()
		} else {
			footer_randomServer()
		}
	}
	
	@ViewBuilder
	func footer_randomServer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			Spacer()
			
			Button("Cancel") {
				closeSheet()
			}
			.padding(.trailing)
				
			Button("Save") {
				saveConfig()
			}
			.disabled(!userHasMadeChanges)
		}
		.font(.title2)
	}
	
	@ViewBuilder
	func footer_customServer() -> some View {
		
		HStack(alignment: VerticalAlignment.center, spacing: 0) {
			
			if activeCheck != nil {
			
				ProgressView()
					.progressViewStyle(CircularProgressViewStyle(tint: Color.appAccent))
					.padding(.trailing, 6)
				
				Button("cancel") {
					cancelServerConnectionCheck()
				}
				.font(.callout)
				
			} else if isUntrustedCert {
				
				Button {
					copyCert()
				} label: {
					HStack(alignment: VerticalAlignment.firstTextBaseline, spacing: 4) {
						Image(systemName: "square.on.square")
						Text("Copy certificate")
					}
					.font(.callout)
				}
			}
			
			Spacer()
			
			if activeCheck == nil {
				Button("Cancel") {
					closeSheet()
				}
				.padding(.trailing)
				.font(.title2)
			}
			
			Button("Save") {
				if isUntrustedCert {
					saveConfig()
				} else {
					checkServerConnection()
				}
			}
			.disabled(!userHasMadeChanges || activeCheck != nil || (isUntrustedCert && !shouldTrustPubKey))
			.font(.title2)
		}
	}
	
	@ViewBuilder
	func checkmarkImage_on() -> some View {
		Image(systemName: "checkmark.square.fill")
			.imageScale(.large)
	}
	
	@ViewBuilder
	func checkmarkImage_off() -> some View {
		Image(systemName: "square")
			.imageScale(.large)
	}
	
	// --------------------------------------------------
	// MARK: UI Content Helpers
	// --------------------------------------------------
	
	/**
	 * The fingerprint of an X509 cert is simply the hash of the raw DER-encoded data.
	 * The hash used is typically either SHA1 or SHA256.
	 * And the display format is typically upper-case hexadecimal.
	 * Example for SHA1 Fingerprint:
	 *
	 * C1:62:FA:5C:48:73:EC:06:0C:FC:AE:65:93:73:96:E0:66:03:A7:E6
	 */
	func sha1Fingerprint(_ cert: SecCertificate) -> String {
		
		let data = SecCertificateCopyData(cert) as Data
		let fingerprint = Insecure.SHA1.hash(data: data).map { String(format: "%02X:", $0) }.joined()
		return String(fingerprint.dropLast(1))
	}
	
	func sha256Fingerprint(_ cert: SecCertificate) -> String {
		
		let data = SecCertificateCopyData(cert) as Data
		let fingerprint = SHA256.hash(data: data).map { String(format: "%02X:", $0) }.joined()
		return String(fingerprint.dropLast(1))
	}
	
	func commonName(_ cert: SecCertificate) -> String {
		
		var cfstr: CFString? = nil
		SecCertificateCopyCommonName(cert, &cfstr)
		
		return cfstr as String? ?? ""
	}
	
	func subjectSummary(_ cert: SecCertificate) -> String {
		
		let cfstr = SecCertificateCopySubjectSummary(cert)
		return cfstr as String? ?? ""
	}
	
	func email(_ cert: SecCertificate) -> String {
		
		var cfarray: CFArray? = nil
		SecCertificateCopyEmailAddresses(cert, &cfarray)
		
		let array = cfarray as? [String] ?? []
		return array.first ?? ""
	}
	
	func pubKey(_ cert: SecCertificate) -> String? {
		
		guard let key = SecCertificateCopyKey(cert) else {
			return nil
		}
		
		var cferr: Unmanaged<CFError>? = nil
		let cfdata = SecKeyCopyExternalRepresentation(key, &cferr)
		
		if let cfdata = cfdata {
			let data = cfdata as Data
			return data.base64EncodedString()
		} else {
			return nil
		}
	}
	
	func encodePEM(_ cert: SecCertificate) -> String {
		
		let derData = SecCertificateCopyData(cert) as Data
		var pemString = derData.base64EncodedString(options: [.lineLength64Characters, .endLineWithLineFeed])
		
		pemString = "-----BEGIN CERTIFICATE-----\n" + pemString
		if !pemString.hasSuffix("\n") {
			pemString += "\n"
		}
		pemString += "-----END CERTIFICATE-----"
		
		return pemString
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func onToggleDidChange() -> Void {
		log.trace("onToggleDidChange()")
		
		userHasMadeChanges = true
		if !isCustomized {
			disableTextFields = false
			invalidHost = false
			invalidPort = false
			activeCheck?.cancel()
			activeCheck = nil
			checkResult = nil
			certInfoType = CertInfoType.first()
			shouldTrustPubKey = false
		}
	}
	
	func onHostDidChange() {
		log.trace("onHostDidChange()")
		userHasMadeChanges = true
	}
	
	func onHostDidCommit() {
		log.trace("onHostDidCommit()")
		checkHost()
	}
	
	func onPortDidChange() {
		log.trace("onPortDidChange()")
		userHasMadeChanges = true
	}
	
	func onPortDidCommit() {
		log.trace("onPortDidCommit()")
		checkPort()
	}
	
	func closeSheet() {
		log.trace("closeSheet()")
		shortSheetState.close()
	}
	
	func copyCert() {
		log.trace("copyCert()")
		
		guard let cert = untrustedCert else {
			return
		}
		let pem = encodePEM(cert)
		
		UIPasteboard.general.string = pem
		toast.pop(
			Text("Copied to pasteboard!").anyView,
			colorScheme: colorScheme.opposite,
			alignment: .none
		)
	}
	
	func checkServerConnection() {
		log.trace("checkServerConnection()")
		
		guard
			let checkedHost: String = checkHost(),
			let checkedPort: UInt16 = checkPort()
		else {
			// Either invalidHost or invalidPort set to true
			return
		}
		
		var thisCheck: AnyCancellable? = nil
		let completion = {(result: Result<TLSConnectionStatus, TLSConnectionError>) in
			if thisCheck == self.activeCheck {
				self.tlsConnectionCheckDidFinish(result)
			}
		}
		
//	#if DEBUG
//		let cert = TLSConnectionCheck.debugCert()
//		thisCheck = TLSConnectionCheck.debug(
//			result: Result.success(TLSConnectionStatus.untrusted(cert: cert)),
//			delay: 1.0,
//			completion: completion
//		)
//	#endif
		
		thisCheck = TLSConnectionCheck.check(
			host: checkedHost,
			port: checkedPort,
			completion: completion
		)
		
		disableTextFields = true
		checkResult = nil
		activeCheck = thisCheck
	}
	
	func cancelServerConnectionCheck() {
		log.trace("cancelServerConnectionCheck()")
		
		disableTextFields = false
		checkResult = nil
		activeCheck?.cancel()
		activeCheck = nil
	}
	
	func saveConfig() -> Void {
		log.trace("saveConfig()")
		
		if isCustomized,
		   let checkedHost: String = checkHost(),
		   let checkedPort: UInt16 = checkPort()
		{
			let pinnedPubKey: String?
			let tls: Lightning_kmpTcpSocketTLS
			if let cert = untrustedCert {
				guard let pubKey = pubKey(cert) else {
					return toast.pop(
						Text("Unable to extract public key!").anyView,
						colorScheme: colorScheme.opposite,
						alignment: .none
					)
				}
				pinnedPubKey = pubKey
				tls = Lightning_kmpTcpSocketTLS.PINNED_PUBLIC_KEY(pubKey: pubKey)
			} else {
				pinnedPubKey = nil
				tls = Lightning_kmpTcpSocketTLS.TRUSTED_CERTIFICATES()
			}
			
			Prefs.shared.electrumConfig = ElectrumConfigPrefs(
				host: checkedHost,
				port: checkedPort,
				pinnedPubKey: pinnedPubKey
			)
			mvi.intent(ElectrumConfiguration.IntentUpdateElectrumServer(server: Lightning_kmpServerAddress(
				host: checkedHost,
				port: Int32(checkedPort),
				tls: tls
			)))
			
		} else {
			
			Prefs.shared.electrumConfig = nil
			mvi.intent(ElectrumConfiguration.IntentUpdateElectrumServer(server: nil))
		}
		
		shortSheetState.close()
	}
	
	@discardableResult
	func checkHost() -> String? {
		log.trace("checkHost()")
		
		if isCustomized {
			
			let rawHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
			let urlStr = "http://\(rawHost)"
			
			// careful: URL(string: "http://") return non-nil
			
			if (rawHost.count > 0) && (URL(string: urlStr) != nil) {
				invalidHost = false
				return rawHost
			} else {
				invalidHost = true
				return nil
			}
			
		} else {
			
			invalidHost = false
			return ""
		}
	}
	
	@discardableResult
	func checkPort() -> UInt16? {
		log.trace("checkPort()")
		
		if isCustomized {
			
			if port.isEmpty {
				invalidPort = false
				return DEFAULT_PORT
				
			} else {
				let rawPort = port.trimmingCharacters(in: .whitespacesAndNewlines)
				
				if let parsedPort = UInt16(rawPort), parsedPort != 0 {
					invalidPort = false
					return parsedPort
				} else {
					invalidPort = true
					return nil
				}
			}
			
		} else {
			
			invalidPort = false
			return 0
		}
	}
	
	func tlsConnectionCheckDidFinish(_ result: Result<TLSConnectionStatus, TLSConnectionError>) {
		log.trace("tlsConnectionCheckDidFinish()")
		
		switch result {
		case .failure(let error):
			switch error {
			case .invalidPort:
				log.debug("FAILURE: invalidPort")
			case .cancelled:
				log.debug("FAILURE: cancelled")
			case .network(_):
				log.debug("FAILURE: network")
			}
			
		case .success(let status):
			switch status {
			case .trusted:
				log.debug("SUCCESS: trusted")
			case .untrusted(_):
				log.debug("SUCCESS: untrusted")
			}
		}
		
		activeCheck = nil
		checkResult = result
	}
}

// MARK:-
/*
class ElectrumConfigurationView_Previews: PreviewProvider {
	
	static let electrumServer1 = Lightning_kmpServerAddress(
		host: "tn.not.fyi",
		port: 55002,
		tls: nil
	)
	
	static let electrumServer2 = Lightning_kmpServerAddress(
		host: "",
		port: 0,
		tls: nil
	)
	
	static let mockModel = ElectrumConfiguration.Model(
		configuration: ElectrumConfig.Custom(server: electrumServer2),
		currentServer: nil,
		connection: Lightning_kmpConnection.closed,
		feeRate: 9999,
		blockHeight: 1234,
		tipTimestamp: 1234567890,
		walletIsInitialized: true,
		error: nil
	)

	static var previews: some View {

		ElectrumAddressSheet(
			model: mockModel,
			postIntent: { _ in },
			showing: $isShowing
		)
		.padding()
		.preferredColorScheme(.light)
		.previewDevice("iPhone 8")
	}
}
*/
