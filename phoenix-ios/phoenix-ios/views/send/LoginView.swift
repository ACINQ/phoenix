import SwiftUI
import PhoenixShared
import os.log

#if DEBUG && true
fileprivate var log = Logger(
	subsystem: Bundle.main.bundleIdentifier!,
	category: "LoginView"
)
#else
fileprivate var log = Logger(OSLog.disabled)
#endif

struct LoginView: View {
	
	@ObservedObject var mvi: MVIState<Scan.Model, Scan.Intent>
	
	enum MaxImageHeight: Preference {}
	let maxImageHeightReader = GeometryPreferenceReader(
		key: AppendValue<MaxImageHeight>.self,
		value: { [$0.size.height] }
	)
	@State var maxImageHeight: CGFloat? = nil
	
	let buttonFont: Font = .title3
	let buttonImgScale: Image.Scale = .medium
	
	@ViewBuilder
	var body: some View {
		
		ZStack {
		
			Color.primaryBackground
				.edgesIgnoringSafeArea(.all)
			
			if BusinessManager.showTestnetBackground {
				Image("testnet_bg")
					.resizable(resizingMode: .tile)
					.ignoresSafeArea(.all, edges: .all)
			}
			
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
			
			content
		}
		.assignMaxPreference(for: maxImageHeightReader.key, to: $maxImageHeight)
		.frame(maxHeight: .infinity)
		.edgesIgnoringSafeArea([.bottom, .leading, .trailing]) // top is nav bar
		.navigationTitle("lnurl-auth")
		.navigationBarTitleDisplayMode(.inline)
	}
	
	@ViewBuilder
	var content: some View {
		
		VStack(alignment: HorizontalAlignment.center, spacing: 30) {
			
			Spacer()
			
			Text("You can use your wallet to anonymously sign and authorize an action on:")
				.multilineTextAlignment(.center)
			
			Text(domain())
				.font(.headline)
				.multilineTextAlignment(.center)
			
			if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult, model.error == nil {
				
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
				
			} else {
				
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
				.disabled(mvi.model is Scan.Model_LnurlAuthFlow_LoggingIn)
			}
			
			ZStack {
				Divider()
				if mvi.model is Scan.Model_LnurlAuthFlow_LoggingIn {
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
			
			Spacer()
			Spacer()
			
		} // </VStack>
		.padding(.horizontal, 20)
	}
	
	var auth: LnurlAuth? {
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginRequest {
			return model.auth
		} else if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoggingIn {
			return model.auth
		} else if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult {
			return model.auth
		} else {
			return nil
		}
	}
	
	func domain() -> String {
		
		return auth?.initialUrl.host ?? "?"
	}
	
	func buttonTitle() -> String {
		
		return auth?.actionPromptTitle ?? LnurlAuth.defaultActionPromptTitle
	}
	
	func successTitle() -> String {
		
		return auth?.actionSuccessTitle ?? LnurlAuth.defaultActionSuccessTitle
	}
	
	func errorText() -> String? {
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginResult, let error = model.error {
			
			if let error = error as? Scan.LoginErrorServerError {
				if let details = error.details as? LnurlError.RemoteFailure_Code {
					let frmt = NSLocalizedString("Server returned HTTP status code %d", comment: "error details")
					return String(format: frmt, details.code.value)
				
				} else if let details = error.details as? LnurlError.RemoteFailure_Detailed {
					let frmt = NSLocalizedString("Server returned error: %@", comment: "error details")
					return String(format: frmt, details.reason)
				
				} else {
					return NSLocalizedString("Server returned unreadable response", comment: "error details")
				}
				
			} else if error is Scan.LoginErrorNetworkError {
				return NSLocalizedString("Network error. Check your internet connection.", comment: "error details")
				
			} else {
				return NSLocalizedString("An unknown error occurred.", comment: "error details")
			}
		}
		
		return nil
	}
	
	func loginButtonTapped() {
		log.trace("loginButtonTapped()")
		
		if let model = mvi.model as? Scan.Model_LnurlAuthFlow_LoginRequest {
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
			//
			mvi.intent(Scan.Intent_LnurlAuthFlow_Login(
				auth: model.auth,
				minSuccessDelaySeconds: 1.6,
				scheme: LnurlAuth.Scheme_DEFAULT()
			))
		}
	}
}
