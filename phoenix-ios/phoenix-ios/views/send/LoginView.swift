import SwiftUI
import PhoenixShared

fileprivate let filename = "LoginView"
#if DEBUG && true
fileprivate var log = LoggerFactory.shared.logger(filename, .trace)
#else
fileprivate var log = LoggerFactory.shared.logger(filename, .warning)
#endif

struct LoginView: View {
	
	let flow: SendManager.ParseResult_Lnurl_Auth
	
	let popTo: (PopToDestination) -> Void // For iOS 16
	
	@State var isLoggingIn = false
	@State var didLogIn = false
	@State var loginError: SendManager.LnurlAuth_Error? = nil
	
	enum MaxImageHeight: Preference {}
	let maxImageHeightReader = GeometryPreferenceReader(
		key: AppendValue<MaxImageHeight>.self,
		value: { [$0.size.height] }
	)
	@State var maxImageHeight: CGFloat? = nil
	
	@Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>
	
	@EnvironmentObject var navCoordinator: NavigationCoordinator
	
	let buttonFont: Font = .title3
	let buttonImgScale: Image.Scale = .medium
	
	// --------------------------------------------------
	// MARK: View Builders
	// --------------------------------------------------
	
	@ViewBuilder
	var body: some View {
		
		layers()
			.navigationTitle("lnurl-auth")
			.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	func layers() -> some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
			hiddenContent()
			content()
		}
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
	}
	
	@ViewBuilder
	func hiddenContent() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 0) {
			
			// I want the height of these 2 components to match exactly:
			// Button("<img> Login")
			// HStack("<img> Logged In")
			//
			// To accomplish this, I need the images to be same height.
			// But they're not - unless we measure them, and enforce matching heights.
			
			Image(systemName: "bolt")
				.imageScale(buttonImgScale)
				.font(buttonFont)
				.foregroundColor(.clear)
				.read(maxImageHeightReader)
			
			Image(systemName: "hand.thumbsup.fill")
				.imageScale(buttonImgScale)
				.font(buttonFont)
				.foregroundColor(.clear)
				.read(maxImageHeightReader)
		}
		.assignMaxPreference(for: maxImageHeightReader.key, to: $maxImageHeight)
	}
	
	@ViewBuilder
	func content() -> some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 30) {
			
			Spacer()
			
			Text("You can use your wallet to anonymously sign and authorize an action on:")
				.multilineTextAlignment(.center)
			
			Text(domain())
				.font(.headline)
				.multilineTextAlignment(.center)
			
			if didLogIn, loginError == nil {
				loginSuccessLabel()
			} else {
				loginButton()
			}
			
			ZStack {
				Divider()
				if isLoggingIn {
					HorizontalActivity(color: .appAccent, diameter: 10, speed: 1.6)
				}
			}
			.frame(width: 100, height: 10)
			
			if let errorStr = errorText() {
				
				Text(errorStr)
					.font(.callout)
					.foregroundColor(.appNegative)
					.multilineTextAlignment(.center)
				
			} else {
				
				Text("No personal data will be shared with this service.")
					.font(.callout)
					.foregroundColor(.secondary)
					.multilineTextAlignment(.center)
			}
			
			doneButton()
			
			Spacer()
			Spacer()
			
		} // </VStack>
		.padding(.horizontal, 20)
	}
	
	@ViewBuilder
	func loginSuccessLabel() -> some View {
		
		HStack(alignment: VerticalAlignment.firstTextBaseline) {
			Image(systemName: "hand.thumbsup.fill")
				.renderingMode(.template)
				.imageScale(buttonImgScale)
				.frame(minHeight: maxImageHeight)
			Text(successTitle())
		}
		.font(buttonFont)
		.foregroundColor(Color.appPositive)
		.padding(.top, 4)
		.padding(.bottom, 5)
		.padding([.leading, .trailing], 24)
	}
	
	@ViewBuilder
	func loginButton() -> some View {
		
		Button {
			loginButtonTapped()
		} label: {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "bolt")
					.renderingMode(.template)
					.imageScale(buttonImgScale)
					.frame(minHeight: maxImageHeight)
				Text(buttonTitle())
			}
			.font(buttonFont)
			.foregroundColor(Color.white)
			.padding(.top, 4)
			.padding(.bottom, 5)
			.padding([.leading, .trailing], 24)
		}
		.buttonStyle(ScaleButtonStyle(
			cornerRadius: 100,
			backgroundFill: Color.appAccent,
			disabledBackgroundFill: Color(UIColor.systemGray)
		))
		.disabled(isLoggingIn)
	}
	
	@ViewBuilder
	func doneButton() -> some View {
		
		Button {
			doneButtonTapped()
		} label: {
			HStack(alignment: VerticalAlignment.firstTextBaseline) {
				Image(systemName: "checkmark.circle")
					.renderingMode(.template)
					.imageScale(buttonImgScale)
				Text("Done")
			}
			.font(buttonFont)
			.foregroundColor(Color.white)
			.padding(.top, 4)
			.padding(.bottom, 5)
			.padding([.leading, .trailing], 24)
		}
		.buttonStyle(ScaleButtonStyle(
			cornerRadius: 100,
			backgroundFill: Color.appAccent,
			disabledBackgroundFill: Color(UIColor.systemGray)
		))
		.disabled(!didLogIn)
		.opacity(didLogIn ? 1.0 : 0.0)
	}
	
	// --------------------------------------------------
	// MARK: View Helpers
	// --------------------------------------------------
	
	var auth: LnurlAuth? {
		return flow.auth
	}
	
	func domain() -> String {
		return flow.auth.initialUrl.host
	}
	
	func buttonTitle() -> String {
		return flow.auth.actionPromptTitle
	}
	
	func successTitle() -> String {
		return flow.auth.actionSuccessTitle
	}
	
	func errorText() -> String? {
		
		if let serverError = loginError as? SendManager.LnurlAuth_Error_ServerError {
			if let details = serverError.details as? LnurlError.RemoteFailure_Code {
				let frmt = String(localized: "Server returned HTTP status code %d", comment: "error details")
				return String(format: frmt, details.code.value)
			
			} else if let details = serverError.details as? LnurlError.RemoteFailure_Detailed {
				let frmt = String(localized: "Server returned error: %@", comment: "error details")
				return String(format: frmt, details.reason)
			
			} else {
				return String(localized: "Server returned unreadable response", comment: "error details")
			}
			
		} else if loginError is SendManager.LnurlAuth_Error_NetworkError {
			return String(localized: "Network error. Check your internet connection.", comment: "error details")
			
		} else if loginError != nil {
			return String(localized: "An unknown error occurred.", comment: "error details")
			
		} else {
			return nil
		}
	}
	
	// --------------------------------------------------
	// MARK: Actions
	// --------------------------------------------------
	
	func loginButtonTapped() {
		log.trace("loginButtonTapped()")
		
		guard !isLoggingIn else {
			log.warning("loginButtonTapped: ignoring: isLoggingIn == true")
			return
		}
		
		// There's usually a bit of delay between:
		// - the successful authentication (when Phoenix receives auth success response from server)
		// - the webpage updating to reflect the authentication success
		//
		// This is probably due to several factors:
		// Possibly due to client-side polling (webpage asking server for an auth result).
		// Or perhaps the server pushing the successful auth to the client via websockets.
		//
		// But whatever the case, it leads to a bit of confusion for the user.
		// The wallet says "success", but the website just sits there.
		// Meanwhile the user is left wondering if the wallet is wrong, or something else is broken.
		//
		// For this reason, we're smoothing the user experience with a bit of extra animation in the wallet.
		// Here's how it works:
		// - the user taps the button, and we immediately send the HTTP GET to the server for authentication
		// - the UI starts a pretty animation to show that it's authenticating
		// - if the server responds too quickly (with succcess), we inject a small delay
		// - during this small delay, the wallet UI continues the pretty animation
		// - this gives the website a bit of time to update
		//
		// The end result is that the website is usually updating (or updated) by the time
		// the wallet shows the "authenticated" screen.
		// This leads to less confusion, and a happier user.
		// Which hopefully leads to more lnurl-auth adoption.
		
		isLoggingIn = true
		loginError = nil
		
		Task { @MainActor () -> Void in
			do {
				let err: SendManager.LnurlAuth_Error? =
					try await Biz.business.sendManager.lnurlAuth_signAndSend(
						auth: flow.auth,
						minSuccessDelaySeconds: 1.6,
						scheme: LnurlAuth.Scheme_DEFAULT()
					)
				
				isLoggingIn = false
				if err == nil {
					didLogIn = true
				} else {
					loginError = err
				}
				
			} catch {
				log.error("lnurlAuth_signAndSend: error: \(error)")
				
				isLoggingIn = false
				loginError = SendManager.LnurlAuth_Error_OtherError(
					details: KotlinThrowable(message: "Error thrown")
				)
			}
		} // </Task>
	}
	
	func doneButtonTapped() {
		log.trace("doneButtonTapped()")
		
		popToRootView()
	}
	
	func popToRootView() {
		log.trace("popToRootView()")
		
		if #available(iOS 17, *) {
			navCoordinator.path.removeAll()
		} else { // iOS 16
			popTo(.RootView(followedBy: nil))
			presentationMode.wrappedValue.dismiss()
		}
	}
}

